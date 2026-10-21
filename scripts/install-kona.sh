#!/usr/bin/env bash
# Compat wrapper: build `kona` from this checkout.
# Prefer: curl -fsSL https://raw.githubusercontent.com/konaframework/kona/main/scripts/install.sh | bash
#         (installer from GitHub; product from latest Maven Central kona-cli)
# Or:     ./scripts/install.sh --local
set -euo pipefail
DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec "$DIR/install.sh" --local "$@"
