#!/bin/bash
# Scripted end-to-end scenarios for the Linux GUI against the local filesystem. No server needed.
# Runs from the repository root. Set CYBERDUCK_BIN to test an installed package, for example `cyberduck`.
# Scenarios that open windows need a display: xvfb-run -a linux/smoke.sh
set -u
cd "$(cd "$(dirname "$0")/.." && pwd)"
bin="${CYBERDUCK_BIN:-linux/run.sh}"
work="$(mktemp -d)"
pids=""
container=""
cleanup() {
    # shellcheck disable=SC2086
    [ -z "$pids" ] || kill $pids 2>/dev/null
    [ -z "$container" ] || docker rm -f "$container" >/dev/null 2>&1
    rm -rf "$work"
}
trap cleanup EXIT
# Preferences, bookmarks and logs are written to the home folder. Never touch the real one.
export HOME="$work/home"
mkdir -p "$HOME/.duck"
# Keep the passwords in the credentials file of the isolated home. The keyring of the machine may be locked and wait for
# a prompt that no one answers.
echo 'factory.passwordstore.class=ch.cyberduck.core.UnsecureHostPasswordStore' > "$HOME/.duck/cyberduck.properties"

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
# A stand-in for notify-send proves that the finished transfer reaches the desktop notification
mkdir -p "$work/fakebin"
printf '#!/bin/sh\nfor a in "$@"; do echo "$a" >> "%s"; done\n' "$work/notify.log" > "$work/fakebin/notify-send"
chmod +x "$work/fakebin/notify-send"
PATH="$work/fakebin:$PATH" smoke '^SMOKE OK download progress=[0-9]+$' download "$work/down-src" "$work/down-dst"
grep -qx 'Download complete' "$work/notify.log" || fail "download: no notification was sent"
echo "ok notified"
[ "$(sha256sum < "$work/down-src/f.bin")" = "$(sha256sum < "$work/down-dst/f.bin")" ] || fail "download: checksum of the downloaded file differs"

mkdir -p "$work/up-src" "$work/up-dst" && head -c 1048576 /dev/urandom > "$work/up-src/g.bin"
smoke '^SMOKE OK upload rows=1$' upload "$work/up-src" "$work/up-dst"
[ "$(sha256sum < "$work/up-src/g.bin")" = "$(sha256sum < "$work/up-dst/g.bin")" ] || fail "upload: checksum of the uploaded file differs"

mkdir -p "$work/ops"
smoke '^SMOKE OK fileops$' fileops "$work/ops"
[ -z "$(ls -A "$work/ops")" ] || fail "fileops: expected an empty folder but found $(ls -A "$work/ops")"

# Quitting must end the process by itself. A hang is a failure, so the timeout is short.
mkdir -p "$work/win-one" "$work/win-two" && touch "$work/win-one/a" "$work/win-one/b" "$work/win-two/x"
prefs_before="$(stat -c '%i %y' "$HOME/.duck/cyberduck.properties")"
out="$(timeout 30 "$bin" --smoke windows "$work/win-one" "$work/win-two" 2>&1)" || { echo "$out"; fail "windows: failed or did not exit by itself within 30 seconds"; }
echo "$out" | grep -q '^SMOKE OK windows$' || { echo "$out"; fail "windows: expected 'SMOKE OK windows'"; }
[ "$(stat -c '%i %y' "$HOME/.duck/cyberduck.properties")" != "$prefs_before" ] || fail "windows: preferences were not saved when quitting"
echo "ok windows"

# A server with a certificate that nobody trusts. The connection must not use a proxy from the environment.
tls_port="${CYBERDUCK_SMOKE_TLS_PORT:-18443}"
openssl req -x509 -newkey rsa:2048 -nodes -subj /CN=localhost -days 2 -keyout "$work/key.pem" -out "$work/cert.pem" 2>/dev/null || fail "tls: openssl could not create a certificate"
python3 linux/tls-server.py "$tls_port" "$work/cert.pem" "$work/key.pem" &
pids="$pids $!"
for _ in $(seq 1 50); do (exec 3<>"/dev/tcp/127.0.0.1/$tls_port") 2>/dev/null && break; sleep 0.2; done
out="$(env -u HTTPS_PROXY -u https_proxy -u HTTP_PROXY -u http_proxy -u ALL_PROXY -u all_proxy timeout 130 "$bin" --smoke tls "$tls_port" 2>&1)" || { echo "$out"; fail "tls failed"; }
echo "$out" | grep -q '^SMOKE OK tls$' || { echo "$out"; fail "tls: expected 'SMOKE OK tls'"; }
echo "ok tls"

# The usable minimum against a real SFTP server in a container, when Docker is available
if docker info >/dev/null 2>&1; then
    image="${CYBERDUCK_SMOKE_SFTP_IMAGE:-atmoz/sftp:alpine}"
    container="$(docker run -d --rm -p 127.0.0.1::22 "$image" foo:pass:::upload)" || fail "sftp: could not start $image"
    sftp_port="$(docker port "$container" 22/tcp | head -1 | sed 's/.*://')"
    for _ in $(seq 1 50); do
        timeout 3 bash -c "exec 3<>/dev/tcp/127.0.0.1/$sftp_port; read -t 2 line <&3; [[ \$line == SSH-* ]]" 2>/dev/null && break
        sleep 0.5
    done
    mkdir -p "$work/sftp"
    smoke '^SMOKE OK sftp$' sftp 127.0.0.1 "$sftp_port" foo pass "$work/sftp"
else
    echo "skip sftp (Docker is not available)"
fi

smoke '^SMOKE OK url 3$' url "$work/list"

echo "SMOKE SCRIPT OK"
