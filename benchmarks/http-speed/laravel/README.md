# Laravel HTTP speed bench app

Minimal Laravel (latest stable) app for the four-way HTTP hot-path comparison.

## Endpoints

- `GET /ping` → plain text `pong`
- `GET /json` → `{"message":"hello","ok":true,"n":42}`

Routes live in `routes/bench.php` and are registered **outside** the web/api
middleware stacks (no session, CSRF, cookies).

## Serve mode

The harness uses:

```bash
php artisan config:cache
php artisan route:cache
php artisan serve --host=127.0.0.1 --port=4003 --no-reload
```

That is PHP’s built-in single-threaded server — intentionally comparable to
FastAPI’s `--workers 1`. Laravel Octane is **not** used here.

## Setup

```bash
cp .env.example .env
php artisan key:generate
composer install
```
