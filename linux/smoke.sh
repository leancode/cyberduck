#!/bin/bash
# Scripted end-to-end scenarios for the Linux GUI against the local filesystem. No server needed.
# Runs from the repository root. Set CYBERDUCK_BIN to test an installed package, for example `cyberduck`.
# Scenarios that open windows need a display: xvfb-run -a linux/smoke.sh
set -u
cd "$(cd "$(dirname "$0")/.." && pwd)"
bin="${CYBERDUCK_BIN:-linux/run.sh}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# Preferences, bookmarks and logs are written to the home folder. Never touch the real one.
export HOME="$work/home"
mkdir -p "$HOME"

fail() {
    echo "SMOKE SCRIPT FAIL: $*" >&2
    exit 1
}

# Run one smoke mode and require a line matching the pattern. Output is shown on failure.
smoke() {
    local pattern="$1"
    shift
    local out
    out="$(timeout 130 "$bin" --smoke "$@" 2>&1)" || { echo "$out"; fail "$1 exited with failure"; }
    echo "$out" | grep -Eq "$pattern" || { echo "$out"; fail "$1: expected output matching '$pattern'"; }
    echo "ok $1"
}

mkdir "$work/list" && touch "$work/list/a" "$work/list/b" "$work/list/c"
smoke '^SMOKE OK core-list 3$' core-list "$work/list"
smoke '^SMOKE OK list 3$' list "$work/list"

mkdir -p "$work/nav/a/b" && echo hello > "$work/nav/a/b/file.txt"
smoke '^SMOKE OK navigate$' navigate "$work/nav"

smoke '^SMOKE OK connect 3$' connect "$work/list"

smoke '^SMOKE OK connect-fail ' connect-fail

smoke '^SMOKE OK bookmarks$' bookmarks "$work/list"

mkdir -p "$work/down-src" "$work/down-dst" && head -c 1048576 /dev/urandom > "$work/down-src/f.bin"
smoke '^SMOKE OK download progress=[0-9]+$' download "$work/down-src" "$work/down-dst"
[ "$(sha256sum < "$work/down-src/f.bin")" = "$(sha256sum < "$work/down-dst/f.bin")" ] || fail "download: checksum of the downloaded file differs"

echo "SMOKE SCRIPT OK"
