#!/usr/bin/env bash
# Install the global `kona` CLI onto your PATH (creates applications).
# App-local work uses ./grind inside each project — this script does not install grind globally.
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
PREFIX="${KONA_PREFIX:-$HOME/.local}"
BIN_DIR="$PREFIX/bin"

echo "Building kona CLI…"
(cd "$ROOT" && ./gradlew -q :kona-cli:installDist)

SRC="$ROOT/modules/cli/build/install/kona-cli"
mkdir -p "$BIN_DIR"
ln -sfn "$SRC/bin/kona" "$BIN_DIR/kona"

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
