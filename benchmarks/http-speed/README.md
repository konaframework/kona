# HTTP speed bench: Kona vs Spring Boot vs FastAPI vs Laravel

Disposable, reproducible localhost comparison of pure HTTP hot paths.

## Apps

| Framework | Port | Stack |
|---|---|---|
| Kona | 4000 | `benchmarks/http-speed/kona` — `kona-http` + Netty + virtual threads |
| Spring Boot 3.5.6 | 4001 | `spring-boot-starter-web` (Tomcat) + virtual threads |
| FastAPI | 4002 | uvicorn `[standard]` / uvloop, `--workers 1` |
| Laravel (latest) | 4003 | `php artisan serve` (PHP built-in server, single-threaded) |

Endpoints (identical payload shape):

- `GET /ping` → plain text `pong`
- `GET /json` → `{"message":"hello","ok":true,"n":42}`

No auth, sessions, CSRF, ORM, or docs UI. Laravel routes are registered
outside the web/api middleware stacks; `APP_ENV=production`, config + route cache
on start.

## Run

```bash
# one-time setup
# python3 -m venv fastapi/.venv && fastapi/.venv/bin/pip install -r fastapi/requirements.txt
# curl -fsSL -o bin/bombardier https://github.com/codesenberg/bombardier/releases/download/v2.0.2/bombardier-linux-amd64
# chmod +x bin/bombardier
# (cd laravel && composer install)

./run_bench.sh
# optional: DURATION=15s WARMUP_DURATION=10s ./run_bench.sh
```

Results land in `results/` (`*.json`, `summary.md`, `summary.json`, `versions.txt`).

## Notes

- JVM apps get a 15s warm-up per endpoint before measured 30s runs.
- Kona’s default server adapter is Netty (epoll on Linux when available) with virtual-thread request dispatch.
- FastAPI and Laravel are intentionally single-worker / single-threaded in this harness
  (uvicorn `--workers 1`, `php artisan serve`). Laravel Octane (Swoole/RoadRunner) is
  **not** used — that would be a different class of server and is noted as a caveat.
- Single-machine laptop/WSL noise applies — not a production ranking.
