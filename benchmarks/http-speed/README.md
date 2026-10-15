# HTTP speed bench: Kona vs Spring Boot vs FastAPI

Disposable, reproducible localhost comparison of pure HTTP hot paths.

## Apps

| Framework | Port | Stack |
|---|---|---|
| Kona | 4000 | `benchmarks/http-speed/kona` — `kona-http` + Netty + virtual threads |
| Spring Boot 3.5.6 | 4001 | `spring-boot-starter-web` (Tomcat) + virtual threads |
| FastAPI | 4002 | uvicorn `[standard]` / uvloop, `--workers 1` |

Endpoints (identical payload shape):

- `GET /ping` → plain text `pong`
- `GET /json` → `{"message":"hello","ok":true,"n":42}`

No auth, sessions, CSRF, ORM, or docs UI.

## Run

```bash
# one-time: bombardier binary + FastAPI venv (script assumes these exist)
# python3 -m venv fastapi/.venv && fastapi/.venv/bin/pip install -r fastapi/requirements.txt
# curl -fsSL -o bin/bombardier https://github.com/codesenberg/bombardier/releases/download/v2.0.2/bombardier-linux-amd64
# chmod +x bin/bombardier

./run_bench.sh
# optional: DURATION=15s WARMUP_DURATION=10s ./run_bench.sh
```

Results land in `results/` (`*.json`, `summary.md`, `summary.json`, `versions.txt`).

## Notes

- JVM apps get a 15s warm-up per endpoint before measured 30s runs.
- Kona’s default server adapter is Netty (epoll on Linux when available) with virtual-thread request dispatch.
- Single-machine laptop/WSL noise applies — not a production ranking.
