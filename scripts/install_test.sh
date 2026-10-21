#!/usr/bin/env bash
# Unit tests for install.sh release resolution (no network, no Gradle).
set -euo pipefail
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
KONA_INSTALL_SOURCED=1
# shellcheck source=install.sh
source "$ROOT/scripts/install.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_eq() {
  local got="$1" want="$2" label="$3"
  if [[ "$got" != "$want" ]]; then
    fail "$label: got '$got' want '$want'"
  fi
}

xml_ok='<?xml version="1.0"?>
<metadata>
  <versioning>
    <latest>0.9.0-SNAPSHOT</latest>
    <release>0.2.0</release>
  </versioning>
</metadata>'

assert_eq "$(parse_maven_release "$xml_ok")" "0.2.0" "parse_maven_release"
assert_eq "$(parse_maven_release '<release>1.10.0</release>')" "1.10.0" "parse_maven_release compact"
assert_eq "$(parse_maven_release '<latest>1.0.0</latest>')" "" "parse_maven_release ignores latest-only"

assert_eq "$(parse_github_tag '{"tag_name": "v0.2.0", "draft": false}')" "v0.2.0" "parse_github_tag"
assert_eq "$(parse_github_tag '{}')" "" "parse_github_tag empty"

is_release_version "0.2.0" || fail "0.2.0 should be a release"
is_release_version "0.2.0-SNAPSHOT" && fail "SNAPSHOT must not count as a release"
is_release_version "" && fail "empty must not count as a release"

assert_eq "$(normalize_git_ref "0.2.0")" "v0.2.0" "normalize version"
assert_eq "$(normalize_git_ref "v0.2.0")" "v0.2.0" "normalize already tagged"
assert_eq "$(normalize_git_ref "main")" "main" "normalize branch"

http_get() {
  case "$1" in
    *maven-metadata*)
      printf '%s\n' "$xml_ok"
      ;;
    *)
      return 1
      ;;
  esac
}

assert_eq "$(resolve_install_ref)" "v0.2.0" "resolve from Maven Central"

http_get() {
  case "$1" in
    *maven-metadata*)
      printf '%s\n' '<metadata><versioning><release>0.3.0-SNAPSHOT</release></versioning></metadata>'
      ;;
    *releases/latest*)
      printf '%s\n' '{"tag_name":"v0.1.1"}'
      ;;
    *)
      return 1
      ;;
  esac
}

assert_eq "$(resolve_install_ref)" "v0.1.1" "resolve falls back to GitHub Releases"

http_get() { return 1; }
if resolve_install_ref >/dev/null 2>&1; then
  fail "resolve_install_ref should fail when Central and GitHub are unreachable"
fi

KONA_REF=""
parse_args --dev
assert_eq "$KONA_REF" "main" "--dev sets KONA_REF=main"

KONA_REF="0.1.1"
ensure_install_ref
assert_eq "$KONA_REF" "v0.1.1" "ensure_install_ref normalizes env version"

if ( parse_args --nope ) >/dev/null 2>&1; then
  fail "unknown option should fail"
fi

printf 'OK: scripts/install.sh release resolution\n'
