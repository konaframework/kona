<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        commands: __DIR__.'/../routes/console.php',
        then: function (): void {
            // Bare routes — intentionally outside the web/api middleware stacks.
            require __DIR__.'/../routes/bench.php';
        },
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Hot-path bench: no global middleware weight beyond framework defaults.
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->expectsJson(),
        );
    })->create();
