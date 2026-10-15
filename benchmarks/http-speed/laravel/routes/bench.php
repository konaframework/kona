<?php

/**
 * Fair hot-path routes — no web/api middleware (no session, CSRF, cookies).
 * Payloads match Kona / Spring / FastAPI bench apps.
 */

use Illuminate\Support\Facades\Route;

Route::get('/ping', static function () {
    return response('pong', 200, ['Content-Type' => 'text/plain; charset=UTF-8']);
});

Route::get('/json', static function () {
    return response()->json([
        'message' => 'hello',
        'ok' => true,
        'n' => 42,
    ]);
});
