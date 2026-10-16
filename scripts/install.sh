#!/usr/bin/env bash
# Install the global `kona` CLI onto your PATH (creates applications).
# App-local work uses ./grind inside each project — this script does not install grind.
#
# Happy path (no prior clone):
#   curl -fsSL https://raw.githubusercontent.com/konaframework/kona/main/scripts/install.sh | bash
#
# From a Kona checkout:
#   ./scripts/install.sh --local
#
# Env:
#   KONA_SRC     Source tree (default: ~/.kona) when not using --local
#   KONA_PREFIX  Install prefix for bin/kona (default: ~/.local)
#   KONA_REF     Git ref / branch / tag to sync (default: main)
#   KONA_REPO    Git remote URL (default: https://github.com/konaframework/kona.git)
#   NO_COLOR     Set to disable ANSI colors
#   FORCE_COLOR  Set to force ANSI colors
set -euo pipefail

KONA_REPO="${KONA_REPO:-https://github.com/konaframework/kona.git}"
KONA_REF="${KONA_REF:-main}"
KONA_SRC="${KONA_SRC:-$HOME/.kona}"
KONA_PREFIX="${KONA_PREFIX:-$HOME/.local}"
BIN_DIR="$KONA_PREFIX/bin"
USE_LOCAL=0

# ——— UI (all human status → stderr so $(…) never captures it) ———

ui_color=0
if [[ -n "${NO_COLOR:-}" ]]; then
  ui_color=0
elif [[ -n "${FORCE_COLOR:-}" && "${FORCE_COLOR}" != "0" ]]; then
  ui_color=1
elif [[ -t 2 && "${TERM:-dumb}" != "dumb" ]]; then
  ui_color=1
fi

if [[ "$ui_color" -eq 1 ]]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_DIM=$'\033[2m'
  C_RED=$'\033[31m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'
  C_CYAN=$'\033[36m'
  C_PRIMARY=$'\033[38;2;255;154;0m'   # #FF9A00
  C_ACCENT=$'\033[38;2;40;98;145m'    # #286291
else
  C_RESET=; C_BOLD=; C_DIM=; C_RED=; C_GREEN=; C_YELLOW=; C_BLUE=; C_CYAN=; C_PRIMARY=; C_ACCENT=
fi

ui() { printf '%b' "$*" >&2; }
uiln() { printf '%b\n' "$*" >&2; }

ui_banner() {
  uiln ""
  uiln "  ${C_PRIMARY}${C_BOLD}╭──────────────────────────────────────────╮${C_RESET}"
  uiln "  ${C_PRIMARY}${C_BOLD}│${C_RESET}  ${C_PRIMARY}${C_BOLD}Kona${C_RESET}${C_DIM} installer${C_RESET}                          ${C_PRIMARY}${C_BOLD}│${C_RESET}"
  uiln "  ${C_PRIMARY}${C_BOLD}│${C_RESET}  ${C_DIM}Batteries-included JVM web framework${C_RESET}   ${C_PRIMARY}${C_BOLD}│${C_RESET}"
  uiln "  ${C_PRIMARY}${C_BOLD}╰──────────────────────────────────────────╯${C_RESET}"
  uiln ""
}

STEP_TOTAL=5
STEP_NUM=0

# Modules apps resolve from mavenLocal() (transitive deps included).
PUBLISH_TASKS=(
  :kona-core:publishToMavenLocal
  :kona-filesystem:publishToMavenLocal
  :kona-support:publishToMavenLocal
  :kona-http:publishToMavenLocal
  :kona-cadence:publishToMavenLocal
  :kona-auth:publishToMavenLocal
  :kona-session:publishToMavenLocal
  :kona-redis:publishToMavenLocal
  :kona-cache:publishToMavenLocal
  :kona-crema:publishToMavenLocal
  :kona-events:publishToMavenLocal
  :kona-queue:publishToMavenLocal
  :kona-mail:publishToMavenLocal
  :kona-notifications:publishToMavenLocal
  :kona-schedule:publishToMavenLocal
  :kona-grind:publishToMavenLocal
  :kona-drip:publishToMavenLocal
  :kona-process:publishToMavenLocal
  :kona-concurrency:publishToMavenLocal
  :kona-testing:publishToMavenLocal
  :kona-broadcasting:publishToMavenLocal
  :kona-search:publishToMavenLocal
  :kona-lsp:publishToMavenLocal
  :kona-cli:publishToMavenLocal
)

ui_bar() {
  local filled="$1" width="$2" i bar=""
  for ((i = 0; i < filled; i++)); do bar+="#"; done
  for ((i = filled; i < width; i++)); do bar+="-"; done
  printf '%s' "$bar"
}

ui_step() {
  STEP_NUM=$((STEP_NUM + 1))
  local label="$1"
  local width=28
  local filled=$((STEP_NUM * width / STEP_TOTAL))
  local bar
  bar=$(ui_bar "$filled" "$width")
  uiln "  ${C_ACCENT}${C_BOLD}[$STEP_NUM/$STEP_TOTAL]${C_RESET} ${C_BOLD}${label}${C_RESET}"
  uiln "  ${C_PRIMARY}${bar}${C_RESET} ${C_DIM}$((STEP_NUM * 100 / STEP_TOTAL))%${C_RESET}"
}

ui_ok() { uiln "  ${C_GREEN}✓${C_RESET} ${C_DIM}$*${C_RESET}"; }
ui_info() { uiln "  ${C_CYAN}→${C_RESET} $*"; }
ui_warn() { uiln "  ${C_YELLOW}!${C_RESET} $*"; }
ui_err() { uiln "  ${C_RED}${C_BOLD}✗${C_RESET} ${C_RED}$*${C_RESET}"; }

ui_spinner_wait() {
  # $1 = pid, $2 = message
  local pid="$1" msg="$2"
  local frames=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
  local i=0
  if [[ "$ui_color" -eq 0 || ! -t 2 ]]; then
    ui_info "$msg"
    wait "$pid"
    return $?
  fi
  while kill -0 "$pid" 2>/dev/null; do
    printf '\r  %b%s%b %s' "$C_PRIMARY" "${frames[i]}" "$C_RESET" "$msg" >&2
    i=$(((i + 1) % ${#frames[@]}))
    sleep 0.08
  done
  printf '\r\033[K' >&2
  wait "$pid"
}

usage() {
  cat <<'EOF'
Usage: install.sh [--local] [--help]

  --local   Build from the repository that contains this script (no fetch).
  --help    Show this help.

Environment: KONA_SRC, KONA_PREFIX, KONA_REF, KONA_REPO, NO_COLOR, FORCE_COLOR
EOF
}

for arg in "$@"; do
  case "$arg" in
    --local) USE_LOCAL=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      ui_err "Unknown option: $arg"
      usage >&2
      exit 1
      ;;
  esac
done

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    ui_err "Required command not found: $1"
    exit 1
  fi
}

java_major() {
  local line ver major
  line=$(java -version 2>&1 | head -n1 || true)
  ver=$(printf '%s\n' "$line" | sed -n 's/.*version "\([^"]*\)".*/\1/p')
  if [[ -z "$ver" ]]; then
    echo 0
    return
  fi
  if [[ "$ver" == 1.* ]]; then
    major=$(printf '%s\n' "$ver" | cut -d. -f2)
  else
    major=$(printf '%s\n' "$ver" | cut -d. -f1)
  fi
  printf '%s\n' "$major"
}

require_jdk() {
  need_cmd java
  local major
  major=$(java_major)
  if [[ "$major" -lt 21 ]]; then
    ui_err "JDK 21+ is required (found major version: ${major:-unknown})"
    java -version >&2 || true
    exit 1
  fi
  ui_ok "JDK $major"
}

script_dir() {
  local src="${BASH_SOURCE[0]:-}"
  if [[ -z "$src" || "$src" == bash || "$src" == - || "$src" == /dev/stdin ]]; then
    printf ''
    return
  fi
  CDPATH= cd -- "$(dirname -- "$src")" && pwd
}

is_kona_root() {
  local root="$1"
  [[ -n "$root" && -f "$root/settings.gradle.kts" && -d "$root/modules/cli" && -x "$root/gradlew" ]]
}

# Prints ONLY the absolute root path on stdout (no status text).
resolve_root() {
  local dir parent
  dir=$(script_dir)
  if [[ "$USE_LOCAL" -eq 1 ]]; then
    if [[ -z "$dir" ]]; then
      ui_err "--local requires running scripts/install.sh from a Kona checkout (not via curl | bash)"
      exit 1
    fi
    parent="$(CDPATH= cd -- "$dir/.." && pwd)"
    if ! is_kona_root "$parent"; then
      ui_err "--local: not a Kona repository root: $parent"
      exit 1
    fi
    printf '%s\n' "$parent"
    return
  fi

  if [[ -n "$dir" ]]; then
    parent="$(CDPATH= cd -- "$dir/.." && pwd)"
    if is_kona_root "$parent"; then
      printf '%s\n' "$parent"
      return
    fi
  fi

  sync_remote_source
  printf '%s\n' "$KONA_SRC"
}

sync_remote_source() {
  mkdir -p "$(dirname -- "$KONA_SRC")"

  if [[ -d "$KONA_SRC/.git" ]]; then
    ui_info "Updating ${C_BOLD}$KONA_SRC${C_RESET} ${C_DIM}(ref: $KONA_REF)${C_RESET}"
    need_cmd git
    git -C "$KONA_SRC" fetch --depth 1 origin "$KONA_REF" >&2
    git -C "$KONA_SRC" checkout -q FETCH_HEAD
    return
  fi

  if [[ -e "$KONA_SRC" && ! -d "$KONA_SRC/.git" ]]; then
    ui_warn "Replacing non-git tree at $KONA_SRC"
    rm -rf "$KONA_SRC"
  fi

  if command -v git >/dev/null 2>&1; then
    ui_info "Cloning into ${C_BOLD}$KONA_SRC${C_RESET} ${C_DIM}(ref: $KONA_REF)${C_RESET}"
    git clone --depth 1 --branch "$KONA_REF" "$KONA_REPO" "$KONA_SRC" >&2
    return
  fi

  need_cmd curl
  need_cmd tar
  ui_info "Downloading archive ${C_DIM}(ref: $KONA_REF)${C_RESET}"
  local tmp archive url
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' RETURN
  archive="$tmp/kona.tgz"
  url="https://github.com/konaframework/kona/archive/refs/heads/${KONA_REF}.tar.gz"
  if ! curl -fsSL "$url" -o "$archive"; then
    url="https://github.com/konaframework/kona/archive/refs/tags/${KONA_REF}.tar.gz"
    curl -fsSL "$url" -o "$archive"
  fi
  mkdir -p "$tmp/extract"
  tar -xzf "$archive" -C "$tmp/extract"
  local extracted
  extracted=$(find "$tmp/extract" -mindepth 1 -maxdepth 1 -type d | head -n1)
  if [[ -z "$extracted" ]]; then
    ui_err "Archive did not contain a directory"
    exit 1
  fi
  mv "$extracted" "$KONA_SRC"
}

publish_framework() {
  local root="$1"
  local log
  log=$(mktemp)
  (
    cd "$root"
    ./gradlew -q "${PUBLISH_TASKS[@]}"
  ) >"$log" 2>&1 &
  if ! ui_spinner_wait $! "Publishing framework jars to ~/.m2 (mavenLocal)…"; then
    ui_err "publishToMavenLocal failed"
    uiln "${C_DIM}$(tail -n 60 "$log")${C_RESET}"
    rm -f "$log"
    exit 1
  fi
  rm -f "$log"
  ui_ok "Published to mavenLocal()"

  # So `kona new` can wire includeBuild via gradle.properties.
  mkdir -p "${XDG_CONFIG_HOME:-$HOME/.config}/kona"
  printf '%s\n' "$root" >"${XDG_CONFIG_HOME:-$HOME/.config}/kona/home"
  ui_ok "Recorded Kona home → ${XDG_CONFIG_HOME:-$HOME/.config}/kona/home"
}

link_cli() {
  local root="$1"
  local src="$root/modules/cli/build/install/kona"
  local dest

  if [[ ! -x "$src/bin/kona" ]]; then
    ui_err "Expected CLI at $src/bin/kona after installDist"
    exit 1
  fi

  mkdir -p "$BIN_DIR"
  dest="$KONA_PREFIX/lib/kona"
  rm -rf "$dest"
  mkdir -p "$(dirname -- "$dest")"
  cp -a "$src" "$dest"
  ln -sfn "$dest/bin/kona" "$BIN_DIR/kona"
  ui_ok "$BIN_DIR/kona"

  if ! command -v kona >/dev/null 2>&1; then
    uiln ""
    ui_warn "Add this to your shell profile:"
    uiln "    ${C_BOLD}export PATH=\"$BIN_DIR:\$PATH\"${C_RESET}"
  fi

  uiln ""
  uiln "  ${C_GREEN}${C_BOLD}Installed${C_RESET}  ${C_PRIMARY}$BIN_DIR/kona${C_RESET}"
  ui "  ${C_DIM}"
  "$BIN_DIR/kona" version >&2 || true
  ui "${C_RESET}"
  uiln ""
  uiln "  ${C_BOLD}Next${C_RESET}"
  uiln "    ${C_PRIMARY}kona new my-app${C_RESET}"
  uiln "    ${C_DIM}cd my-app && ./grind serve${C_RESET}"
  uiln ""
}

main() {
  ui_banner

  ui_step "Check JDK"
  require_jdk

  ui_step "Fetch source"
  local root
  root=$(resolve_root)
  root=$(printf '%s' "$root" | tr -d '\r' | tail -n1)
  if ! is_kona_root "$root"; then
    ui_err "Invalid Kona tree at: $root"
    exit 1
  fi
  ui_ok "$root"

  ui_step "Build CLI"
  local log
  log=$(mktemp)
  (
    cd "$root"
    ./gradlew -q :kona-cli:installDist
  ) >"$log" 2>&1 &
  if ! ui_spinner_wait $! "Building kona CLI with Gradle…"; then
    ui_err "Gradle build failed"
    uiln "${C_DIM}$(tail -n 40 "$log")${C_RESET}"
    rm -f "$log"
    exit 1
  fi
  rm -f "$log"
  ui_ok "CLI built"

  ui_step "Publish libraries"
  publish_framework "$root"

  ui_step "Link binary"
  link_cli "$root"
}

main
