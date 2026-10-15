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
set -euo pipefail

KONA_REPO="${KONA_REPO:-https://github.com/konaframework/kona.git}"
KONA_REF="${KONA_REF:-main}"
KONA_SRC="${KONA_SRC:-$HOME/.kona}"
KONA_PREFIX="${KONA_PREFIX:-$HOME/.local}"
BIN_DIR="$KONA_PREFIX/bin"
USE_LOCAL=0

usage() {
  cat <<'EOF'
Usage: install.sh [--local] [--help]

  --local   Build from the repository that contains this script (no fetch).
  --help    Show this help.

Environment: KONA_SRC, KONA_PREFIX, KONA_REF, KONA_REPO
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
      echo "Unknown option: $arg" >&2
      usage >&2
      exit 1
      ;;
  esac
done

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "error: required command not found: $1" >&2
    exit 1
  fi
}

java_major() {
  # Parses "21", "21.0.1", "1.8.0_392" → major for JDK 9+ is the first field.
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
    echo "error: JDK 21+ is required (found major version: ${major:-unknown})" >&2
    echo "  java -version" >&2
    java -version >&2 || true
    exit 1
  fi
}

script_dir() {
  # When piped to bash, BASH_SOURCE may be unset or "bash" — caller must not rely on local mode.
  local src="${BASH_SOURCE[0]:-}"
  if [[ -z "$src" || "$src" == bash || "$src" == - || "$src" == /dev/stdin ]]; then
    echo ""
    return
  fi
  CDPATH= cd -- "$(dirname -- "$src")" && pwd
}

is_kona_root() {
  local root="$1"
  [[ -f "$root/settings.gradle.kts" && -d "$root/modules/cli" && -x "$root/gradlew" ]]
}

resolve_root() {
  local dir parent
  dir=$(script_dir)
  if [[ "$USE_LOCAL" -eq 1 ]]; then
    if [[ -z "$dir" ]]; then
      echo "error: --local requires running scripts/install.sh from a Kona checkout (not via curl | bash)" >&2
      exit 1
    fi
    parent="$(CDPATH= cd -- "$dir/.." && pwd)"
    if ! is_kona_root "$parent"; then
      echo "error: --local: not a Kona repository root: $parent" >&2
      exit 1
    fi
    printf '%s\n' "$parent"
    return
  fi

  # Auto-local when invoked as ./scripts/install.sh from a real checkout.
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
    echo "Updating Kona in $KONA_SRC (ref: $KONA_REF)…"
    need_cmd git
    git -C "$KONA_SRC" fetch --depth 1 origin "$KONA_REF"
    git -C "$KONA_SRC" checkout -q FETCH_HEAD
    return
  fi

  if [[ -e "$KONA_SRC" && ! -d "$KONA_SRC/.git" ]]; then
    # Replace non-git tree (e.g. previous tarball unpack) cleanly.
    rm -rf "$KONA_SRC"
  fi

  if command -v git >/dev/null 2>&1; then
    echo "Cloning Kona into $KONA_SRC (ref: $KONA_REF)…"
    git clone --depth 1 --branch "$KONA_REF" "$KONA_REPO" "$KONA_SRC"
    return
  fi

  need_cmd curl
  need_cmd tar
  echo "Downloading Kona archive (ref: $KONA_REF) into $KONA_SRC…"
  local tmp archive url
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' RETURN
  archive="$tmp/kona.tgz"
  # Branch/tag archive URLs (tags that are not branches still work via /tags/).
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
    echo "error: archive did not contain a directory" >&2
    exit 1
  fi
  mv "$extracted" "$KONA_SRC"
}

install_cli() {
  local root="$1"
  local src="$root/modules/cli/build/install/kona"

  echo "Building kona CLI…"
  (cd "$root" && ./gradlew -q :kona-cli:installDist)

  if [[ ! -x "$src/bin/kona" ]]; then
    echo "error: expected CLI at $src/bin/kona after installDist" >&2
    exit 1
  fi

  mkdir -p "$BIN_DIR"
  # Copy the distribution so ~/.local/bin/kona does not break if KONA_SRC is replaced.
  local dest="$KONA_PREFIX/lib/kona"
  rm -rf "$dest"
  mkdir -p "$(dirname -- "$dest")"
  cp -a "$src" "$dest"
  ln -sfn "$dest/bin/kona" "$BIN_DIR/kona"

  if ! command -v kona >/dev/null 2>&1; then
    echo
    echo "Add this to your shell profile if needed:"
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
  fi

  echo
  echo "Installed: $BIN_DIR/kona"
  "$BIN_DIR/kona" version
  echo
  echo "Next:"
  echo "  kona new my-app"
  echo "  cd my-app"
  echo "  ./grind serve"
}

main() {
  require_jdk
  local root
  root=$(resolve_root)
  if ! is_kona_root "$root"; then
    echo "error: invalid Kona tree at $root" >&2
    exit 1
  fi
  install_cli "$root"
}

main
