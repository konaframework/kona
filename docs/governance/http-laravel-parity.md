# HTTP Laravel / Almasix parity ledger

Durable deepen tracker for `kona-http` (and middleware-linked session CSRF only when already in HTTP scope).  
**Do not edit** `modules/cadence` or `modules/grind` from this workstream.

**Branch:** `feat/http-niche-validation`  
**Status:** B2 shipping  
**Updated:** 2026-10-16

## Laws

- 100% line + branch coverage on touched production code.
- Java-callable public API for new surfaces.
- Docs under `apps/docs/.../http/` enriched concurrently (progressive detailed voice; no comparison essays).
- Never mutate GitHub rulesets.

---

## Request

| API | Status | Notes |
|-----|--------|-------|
| Core helpers (input/all/validate/…/safe) | **done** | B1 |
| `only` / `except` / `boolean` / `date` / `bearerToken` / `ip` / `userAgent` / `merge` / `replace` / `has` / `filled` / `missing` / `when*` | **done** | B1 |

## Response

| API | Status | Notes |
|-----|--------|-------|
| Core factories + `withHeaders` | **done** | B1 |
| `stream` | **done** | B2 — buffered `OutputStream` callback |

## Middleware

| API | Status | Notes |
|-----|--------|-------|
| Pipeline / aliases / terminable / priority | **done** | B1 |
| Controller middleware (`middleware` / `only` / `except` / `action`) | **done** | B2; resource routes auto-apply |

## Controllers

| API | Status | Notes |
|-----|--------|-------|
| Base / FormRequest / invokable / resource / typed registration | **done** | |
| Middleware on controller | **done** | B2 |

## Validation rules

### Implemented

Baseline + B1 rules, plus B2 niche:

`prohibited`, `prohibited_if`, `prohibited_unless`, `prohibits`, `exclude`, `exclude_if`, `exclude_unless`, `exclude_with`, `exclude_without`, `accepted_if`, `declined_if`, `ulid`, `mac_address`, `current_password` (+ `CurrentPasswordChecker`)

### DB-aware

| Rule | Status | Notes |
|------|--------|-------|
| `unique` / `exists` | **done (API)** | `ExistenceChecker` pluggable |

## Exception rendering

| Behavior | Status |
|----------|--------|
| ValidationException / HttpException JSON vs HTML / report-render | **done** |

## Docs (`apps/docs`)

| Page | Status |
|------|--------|
| `http/validation.mdx` | **updated** B2 |
| `http/request-response.mdx` | **updated** B2 stream |
| `http/controllers.mdx` | **updated** B2 middleware |

## PR batches

| Batch | Scope | PR |
|-------|-------|-----|
| B1 | Request helpers + validation rules + Response.withHeaders + terminable/priority + docs | #68 |
| B2 | Niche validation + stream + controller middleware + docs | shipping |

## Close criteria

B2 rows **done**; CI green; automerge to `main`.
