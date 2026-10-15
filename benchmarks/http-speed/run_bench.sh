#!/usr/bin/env bash
# Fair HTTP speed comparison: Kona vs Spring Boot vs FastAPI vs Laravel
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
BIN="$ROOT/bin/bombardier"
RESULTS="$ROOT/results"
CONCURRENCIES=(50 100 200)
DURATION="${DURATION:-30s}"
WARMUP_DURATION="${WARMUP_DURATION:-15s}"
ENDPOINTS=(/ping /json)

KONA_PORT=4000
SPRING_PORT=4001
FASTAPI_PORT=4002
LARAVEL_PORT=4003

mkdir -p "$RESULTS"

log() { printf '[bench] %s\n' "$*"; }

wait_http() {
  local url="$1" name="$2" tries="${3:-90}"
  for ((i = 1; i <= tries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$name ready ($url)"
      return 0
    fi
    sleep 1
  done
  log "ERROR: $name did not become ready at $url"
  return 1
}

capture_versions() {
  {
    echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "host=$(uname -a)"
    echo "cpus=$(nproc 2>/dev/null || echo unknown)"
    echo "kona_commit=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)"
    echo "jdk=$(java -version 2>&1 | tr '\n' ' ')"
    echo "python=$("$ROOT/fastapi/.venv/bin/python" --version 2>&1)"
    "$ROOT/fastapi/.venv/bin/python" - <<'PY'
import fastapi, uvicorn
print(f"fastapi={fastapi.__version__}")
print(f"uvicorn={uvicorn.__version__}")
PY
    echo "php=$(php -v | head -1)"
    echo "laravel=$(cd "$ROOT/laravel" && php artisan --version 2>/dev/null | tr -s ' ')"
    echo "laravel_serve=php artisan serve (PHP built-in server, single-threaded)"
    echo "bombardier=$("$BIN" --version 2>&1 | head -1)"
    echo "spring_boot=3.5.6"
    echo "duration=$DURATION"
    echo "warmup=$WARMUP_DURATION"
    echo "concurrencies=${CONCURRENCIES[*]}"
  } | tee "$RESULTS/versions.txt"
}

start_kona() {
  log "Starting Kona on :$KONA_PORT"
  (
    cd "$ROOT"
    HOST=127.0.0.1 PORT="$KONA_PORT" \
      "$ROOT/kona/build/install/http-speed-kona/bin/http-speed-kona"
  ) >"$RESULTS/kona-server.log" 2>&1 &
  echo $! >"$RESULTS/kona.pid"
  wait_http "http://127.0.0.1:$KONA_PORT/ping" "Kona" 60
}

start_spring() {
  log "Starting Spring Boot on :$SPRING_PORT"
  (
    cd "$ROOT/spring"
    java -jar build/libs/http-speed-spring.jar
  ) >"$RESULTS/spring-server.log" 2>&1 &
  echo $! >"$RESULTS/spring.pid"
  wait_http "http://127.0.0.1:$SPRING_PORT/ping" "Spring" 90
}

start_fastapi() {
  log "Starting FastAPI/uvicorn on :$FASTAPI_PORT"
  (
    cd "$ROOT/fastapi"
    .venv/bin/uvicorn main:app --host 127.0.0.1 --port "$FASTAPI_PORT" --log-level warning --workers 1
  ) >"$RESULTS/fastapi-server.log" 2>&1 &
  echo $! >"$RESULTS/fastapi.pid"
  wait_http "http://127.0.0.1:$FASTAPI_PORT/ping" "FastAPI" 60
}

start_laravel() {
  log "Starting Laravel (artisan serve) on :$LARAVEL_PORT"
  (
    cd "$ROOT/laravel"
    # Production-ish: cached config/routes; no debug. Single-threaded PHP built-in server
    # (same fairness note as FastAPI --workers 1). Octane not used.
    php artisan config:cache >/dev/null
    php artisan route:cache >/dev/null
    php artisan serve --host=127.0.0.1 --port="$LARAVEL_PORT" --no-reload
  ) >"$RESULTS/laravel-server.log" 2>&1 &
  echo $! >"$RESULTS/laravel.pid"
  wait_http "http://127.0.0.1:$LARAVEL_PORT/ping" "Laravel" 60
}

stop_pidfile() {
  local pf="$1"
  if [[ -f "$pf" ]]; then
    local pid
    pid="$(cat "$pf")"
    kill "$pid" 2>/dev/null || true
    pkill -P "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    rm -f "$pf"
  fi
}

cleanup() {
  log "Stopping servers"
  stop_pidfile "$RESULTS/kona.pid"
  stop_pidfile "$RESULTS/spring.pid"
  stop_pidfile "$RESULTS/fastapi.pid"
  stop_pidfile "$RESULTS/laravel.pid"
  for port in "$KONA_PORT" "$SPRING_PORT" "$FASTAPI_PORT" "$LARAVEL_PORT"; do
    fuser -k "${port}/tcp" 2>/dev/null || true
  done
}
trap cleanup EXIT

warmup() {
  local framework="$1" port="$2"
  log "Warm-up $framework (${WARMUP_DURATION}, c=100) on /ping and /json"
  "$BIN" -c 100 -d "$WARMUP_DURATION" -q "http://127.0.0.1:${port}/ping" >/dev/null
  "$BIN" -c 100 -d "$WARMUP_DURATION" -q "http://127.0.0.1:${port}/json" >/dev/null
}

bench_framework() {
  local framework="$1" port="$2"
  warmup "$framework" "$port"
  for endpoint in "${ENDPOINTS[@]}"; do
    for c in "${CONCURRENCIES[@]}"; do
      local base="$RESULTS/${framework}_${endpoint#/}_c${c}_meas"
      log "bombardier $framework $endpoint c=$c duration=$DURATION"
      "$BIN" -c "$c" -d "$DURATION" -l -o json --print=r \
        "http://127.0.0.1:${port}${endpoint}" | tee "${base}.json"
      "$ROOT/fastapi/.venv/bin/python" - "${base}.json" <<'PY' | tee "${base}.summary.txt"
import json, sys
r = json.load(open(sys.argv[1])).get("result") or json.load(open(sys.argv[1]))
# reload once
import json as J
raw = J.load(open(sys.argv[1]))
r = raw.get("result", raw)
rps = r.get("rps", {})
lat = r.get("latency", {})
pct = lat.get('percentiles') or {}
def ms(v): return float(v or 0) / 1e3  # bombardier JSON uses microseconds
print(f"Reqs/sec\t{float(rps.get('mean', 0)):.2f}")
print(f"Latency\t{ms(lat.get('mean')):.3f}ms")
print(f"50%\t{ms(pct.get('50')):.3f}ms")
print(f"99%\t{ms(pct.get('99')):.3f}ms")
print(f"2xx\t{r.get('req2xx', 0)}")
PY
    done
  done
}

main() {
  [[ -x "$BIN" ]] || { echo "missing bombardier at $BIN"; exit 1; }
  [[ -d "$ROOT/laravel/vendor" ]] || {
    echo "missing Laravel vendor — run: (cd laravel && composer install)"
    exit 1
  }
  # Clear stale listeners
  for port in "$KONA_PORT" "$SPRING_PORT" "$FASTAPI_PORT" "$LARAVEL_PORT"; do
    fuser -k "${port}/tcp" 2>/dev/null || true
  done
  sleep 1

  capture_versions

  KONA_BIN="$ROOT/kona/build/install/http-speed-kona/bin/http-speed-kona"
  log "Building Kona bench module"
  (cd "$REPO" && ./gradlew :http-speed-kona:installDist -q)
  [[ -x "$KONA_BIN" ]] || { log "ERROR: missing $KONA_BIN"; exit 1; }

  log "Building Spring Boot"
  (cd "$ROOT/spring" && ./gradlew bootJar -q)

  start_kona
  bench_framework kona "$KONA_PORT"
  stop_pidfile "$RESULTS/kona.pid"
  fuser -k "${KONA_PORT}/tcp" 2>/dev/null || true
  sleep 2

  start_spring
  bench_framework spring "$SPRING_PORT"
  stop_pidfile "$RESULTS/spring.pid"
  fuser -k "${SPRING_PORT}/tcp" 2>/dev/null || true
  sleep 2

  start_fastapi
  bench_framework fastapi "$FASTAPI_PORT"
  stop_pidfile "$RESULTS/fastapi.pid"
  fuser -k "${FASTAPI_PORT}/tcp" 2>/dev/null || true
  sleep 2

  start_laravel
  bench_framework laravel "$LARAVEL_PORT"
  stop_pidfile "$RESULTS/laravel.pid"
  fuser -k "${LARAVEL_PORT}/tcp" 2>/dev/null || true

  log "Parsing results"
  "$ROOT/fastapi/.venv/bin/python" "$ROOT/parse_results.py"
  log "Done. See $RESULTS/"
}

main "$@"
