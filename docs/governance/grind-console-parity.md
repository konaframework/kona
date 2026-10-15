# Grind console parity ledger

Durable audit of **grind** / `ConsoleIO` against Laravel Artisan Console and Almasix Smith Console behavioral surface. Public docs must not name Laravel/Almasix; this ledger is agent/governance only.

**Branch / work:** `feat/grind-console-parity`  
**Worktree:** `/home/smaosa/kona-grind-console`  
**Stay out of:** `modules/extract`  
**Modules in scope:** `kona-grind`, `kona-cli` (proxy), `kona-support` (`ConsoleIO`), `kona-testing` (console tests), `apps/docs/`

## Surface matrix

| # | Surface | Laravel / Smith | Kona (before) | Target | Status |
|---|---------|-----------------|---------------|--------|--------|
| C1 | Custom command classes | `Command` + `handle()` | Built-ins only | `ConsoleCommand` + `handle(): Int` | **done** |
| C2 | Signature + description | `$signature` / attributes | N/A | `signature` / `description` | **done** |
| C3 | Args & options parsing | `{arg}` `{--opt=}` | Ad-hoc `parseOptions` | Signature-driven bind | **done** |
| C4 | Registration | Console kernel / providers | N/A | `CommandRegistry` | **done** |
| C5 | Auto-discovery | Commands dir scan | N/A | `META-INF/kona/commands` | **done** |
| C6 | Output IO | info/error/table/progress | No table | `table` on ConsoleIO/GrindIO | **done** |
| C7 | Prompts | ask/confirm/choice | `Prompts` | Keep + document | **done** |
| C8 | Call from code | `Artisan::call` / `callSilently` | N/A | `Grind.call` / `callSilently` | **done** |
| C9 | Scheduling hooks | `schedule->command()` | `schedule:run` | Cross-link docs | **done** |
| C10 | `make:command` | `artisan make:command` | Missing | `grind make:command` | **done** |
| C11 | `list` / `help` | list + per-command help | Partial | Customs + `--help` | **done** |
| C12 | Verbosity | `-q` / `-v` | GrindIO | Keep | **done** |
| C13 | Isolation | `Isolatable` + `--isolated` | Missing | File-lock + `--isolated` | **done** |
| C14 | Exit codes | SUCCESS/FAILURE | 0/1 | Constants + docs | **done** |
| C15 | Java-callable commands | N/A | Partial | Java subclass + tests | **done** |
| C16 | `kona grind` proxy | N/A | Implemented | Documented | **done** |
| C17 | Console testing | `$this->artisan()` | `ConsoleTester` | `assertExit` / `assertDontSee` | **done** |
| C18 | Rich public docs | Digging Deeper Artisan | Thin pages | Progressive Console guides | **done** |

## Exit criteria

- [x] All rows above **done**
- [x] 100% Kover on touched production modules (`kona-support`, `kona-grind`, `kona-testing`)
- [x] Java regression for custom commands (`GrindJavaTest.consoleCommandFromJava`)
- [x] Docs under Digging Deeper / grind Console cluster
- [ ] PR merged with automerge

## Public doc paths

| Path | Topic |
|------|-------|
| `apps/docs/src/content/docs/docs/grind/index.mdx` | Overview |
| `apps/docs/src/content/docs/docs/grind/creating-commands.mdx` | Creating commands |
| `apps/docs/src/content/docs/docs/grind/running-commands.mdx` | Running / `kona grind` / `Grind.call` |
| `apps/docs/src/content/docs/docs/grind/console.mdx` | IO & prompts |
| `apps/docs/src/content/docs/docs/grind/commands.mdx` | Built-in reference |
| `apps/docs/src/content/docs/docs/testing/http-console.mdx` | ConsoleTester |

## Notes

- Product names: **grind**, **steam**, **ConsoleIO**, **GrindIO**.
- Isolation: file lock under `storage/framework/grind/` (`OverlappingFileLockException` → busy).
- Discovery: `META-INF/kona/commands` from classloader + app `build/{classes,resources}/main`.
