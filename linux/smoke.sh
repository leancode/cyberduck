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
ftp_container=""
cleanup() {
    # shellcheck disable=SC2086
    [ -z "$pids" ] || kill $pids 2>/dev/null
    [ -z "$container" ] || docker rm -f "$container" >/dev/null 2>&1
    [ -z "$ftp_container" ] || docker rm -f "$ftp_container" >/dev/null 2>&1
    rm -rf "$work"
}
trap cleanup EXIT
# Preferences, bookmarks and logs are written to the home folder. Never touch the real one.
export HOME="$work/home"
# The scenarios look for English labels. The language scenarios set their own language.
export LC_ALL=C.UTF-8
unset LANGUAGE
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

# The labels follow the language of the session. A language list as set by the desktop session is enough.
smoke '^SMOKE OK locale refresh=Refresh$' locale "$work/list"
LANGUAGE=de smoke '^SMOKE OK locale refresh=Aktualisieren$' locale "$work/list"
LANGUAGE=xx:fr:de smoke '^SMOKE OK locale refresh=Actualiser$' locale "$work/list"
# The language chosen in the preferences wins over the session
mkdir -p "$work/home-lang/.duck"
printf 'factory.passwordstore.class=ch.cyberduck.core.UnsecureHostPasswordStore\nlinux.language=fr\n' > "$work/home-lang/.duck/cyberduck.properties"
HOME="$work/home-lang" LANGUAGE=de smoke '^SMOKE OK locale refresh=Actualiser$' locale "$work/list"

# A change in the preferences window is saved at once and the next start shows it
smoke '^SMOKE OK preferences$' preferences
grep -qx 'connection.timeout.seconds=45' "$HOME/.duck/cyberduck.properties" || fail "preferences: the timeout was not saved to cyberduck.properties"
grep -qx 'connection.retry=2' "$HOME/.duck/cyberduck.properties" || fail "preferences: the retries were not saved to cyberduck.properties"
grep -qx 'queue.download.bandwidth.bytes=256000' "$HOME/.duck/cyberduck.properties" || fail "preferences: the download limit was not saved"
grep -qx 'queue.upload.bandwidth.bytes=1048576' "$HOME/.duck/cyberduck.properties" || fail "preferences: the upload limit was not saved"
grep -qx 'logging=WARN' "$HOME/.duck/cyberduck.properties" || fail "preferences: the log level was not saved"
! grep -q 'linux.language' "$HOME/.duck/cyberduck.properties" || fail "preferences: the system default language was saved as a choice"
smoke '^SMOKE OK preferences-check timeout=45$' preferences-check

# The scenarios that start transfers have a home of their own, because a finished transfer stays in the list of
# transfers of the next start, and the download scenario counts the transfers in the list.
mkdir -p "$work/vault" "$work/home-vault/.duck"
cp "$HOME/.duck/cyberduck.properties" "$work/home-vault/.duck/cyberduck.properties"

# A Cryptomator vault: create it, unlock it, upload into it. The name and the text must not be readable on disk.
echo "the amount due is 1234 francs" > "$work/invoice-2026.txt"
HOME="$work/home-vault" smoke '^SMOKE OK vault listed=invoice-2026.txt ondisk=encrypted$' vault "$work/vault" "$work/invoice-2026.txt"
[ -f "$work/vault/secret/masterkey.cryptomator" ] || fail "vault: no master key was written"
! grep -rl "invoice" "$work/vault" >/dev/null 2>&1 || fail "vault: the file name is on disk in clear text"
! grep -rl "1234 francs" "$work/vault" >/dev/null 2>&1 || fail "vault: the file content is on disk in clear text"
! find "$work/vault" -iname '*invoice*' | grep -q . || fail "vault: a file name on disk shows the clear text name"

# A copy on the server with the Duplicate command
mkdir -p "$work/dup" && echo hello > "$work/dup/f.txt"
smoke '^SMOKE OK duplicate files=2$' duplicate "$work/dup"
[ -f "$work/dup/f copy.txt" ] || fail "duplicate: the copy is not on disk"

# Both directions: each side has a file the other lacks, and a file that is newer locally
mkdir -p "$work/sync-remote" "$work/sync-local"
echo "from server" > "$work/sync-remote/r.txt"
echo "old" > "$work/sync-remote/both.txt"
echo "from local" > "$work/sync-local/l.txt"
sleep 2
echo "newer" > "$work/sync-local/both.txt"
HOME="$work/home-vault" smoke '^SMOKE OK sync remote=3 local=3$' sync "$work/sync-remote" "$work/sync-local"

mkdir -p "$work/nav/a/b" && echo hello > "$work/nav/a/b/file.txt"
echo one > "$work/nav/alpha.txt"; echo two > "$work/nav/beta.txt"; echo secret > "$work/nav/.hidden"
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

# Change permissions in the info window: boxes, octal number and a folder with what it contains
mkdir -p "$work/perm/d" && echo x > "$work/perm/f.txt" && chmod 640 "$work/perm/f.txt" && echo y > "$work/perm/d/inside.txt"
smoke '^SMOKE OK chmod boxes=600 octal=664 recursive=700$' chmod "$work/perm" f.txt

# Edit with the program that is set for the type of the file. The programs wait a moment, like a person, and then change
# the file they are given. The change must arrive on the server. Own home, because the settings are changed.
mkdir -p "$work/edit"
for f in a.txt b.md c.dat; do printf original > "$work/edit/$f"; done
printf '#!/bin/sh\nsleep 2\nprintf " by text" >> "$1"\n' > "$work/edit-text.sh"
printf '#!/bin/sh\nsleep 2\nprintf " by markdown" >> "$1"\n' > "$work/edit-markdown.sh"
chmod +x "$work/edit-text.sh" "$work/edit-markdown.sh"
HOME="$work/home-vault" smoke '^SMOKE OK edit text=ok markdown=ok chosen=ok default=ok$' edit "$work/edit" "$work/edit-text.sh" "$work/edit-markdown.sh"

# Compare with a program: the file on this computer first and a new copy of the server file second
mkdir -p "$work/cmp-remote" "$work/cmp-down"
printf 'remote content' > "$work/cmp-remote/f.txt"
printf 'remote g' > "$work/cmp-remote/g.txt"
printf 'local content' > "$work/cmp-down/f.txt"
printf '#!/bin/sh\nprintf "%%s\\n%%s\\n" "$1" "$2" >> "%s/cmp-log.txt"\n' "$work" > "$work/cmp-tool.sh"
chmod +x "$work/cmp-tool.sh"
HOME="$work/home-vault" smoke '^SMOKE OK compare launched=2 downloaded=g.txt$' compare "$work/cmp-remote" "$work/cmp-down" "$work/cmp-tool.sh" "$work/cmp-log.txt"

# New file, copy, cut, paste, download to a folder and with a name, the address, the size of a folder and a drag onto a folder
mkdir -p "$work/files/d" "$work/files/t" "$work/files/big"
printf 'file content' > "$work/files/f.txt"
printf '0123456789' > "$work/files/big/a.bin"
printf '01234' > "$work/files/big/b.bin"
HOME="$work/home-vault" smoke '^SMOKE OK files copied=ok moved=ok download=ok size=15 dragged=(ok|skipped)$' files "$work/files"

# Drag a file and a folder out of the listing with the mouse into another window. Needs xdotool for the mouse.
if command -v xdotool >/dev/null 2>&1; then
    mkdir -p "$work/drag/d"
    printf 'dragged content' > "$work/drag/f.txt"
    printf 'inner content' > "$work/drag/d/inner.txt"
    HOME="$work/home-vault" smoke '^SMOKE OK dragout f.txt=ok d=ok$' dragout "$work/drag"
else
    echo "skip dragout (xdotool is not installed)"
fi

# The menus of the right mouse button on a file, a folder and the empty area
mkdir -p "$work/ctx/sub" && echo x > "$work/ctx/f.txt"
smoke '^SMOKE OK context file=[0-9]+ folder=[0-9]+ empty=[0-9]+$' context "$work/ctx"

# The properties of a file: size from the listing, permissions and address read from the session
chmod 640 "$work/down-src/f.bin"
smoke '^SMOKE OK info size=1048576 permissions=rw-r----- \(640\) url=file://' info "$work/down-src" f.bin
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
    # Log in with a private key instead of a password. The container takes the public key from its keys folder.
    if command -v ssh-keygen >/dev/null 2>&1; then
        mkdir -p "$work/keys" && ssh-keygen -q -t ed25519 -N "" -f "$work/keys/id_ed25519" >/dev/null
        key_container="$(docker run -d --rm -p 127.0.0.1::22 -v "$work/keys/id_ed25519.pub:/home/foo/.ssh/keys/id_ed25519.pub:ro" "$image" foo::::upload)" || fail "sshkey: could not start $image"
        key_port="$(docker port "$key_container" 22/tcp | head -1 | sed 's/.*://')"
        for _ in $(seq 1 50); do
            timeout 3 bash -c "exec 3<>/dev/tcp/127.0.0.1/$key_port; read -t 2 line <&3; [[ \$line == SSH-* ]]" 2>/dev/null && break
            sleep 0.5
        done
        # Open in Terminal starts a program that writes down what it was asked to run
        printf '#!/bin/sh\necho "$*" > "$0.log"\n' > "$work/keys/fake-terminal.sh"
        chmod +x "$work/keys/fake-terminal.sh"
        smoke '^SMOKE OK sshkey ' sshkey 127.0.0.1 "$key_port" foo "$work/keys/id_ed25519" "$work/keys/fake-terminal.sh"
        docker stop "$key_container" >/dev/null 2>&1
    else
        echo "skip sshkey (ssh-keygen is not installed)"
    fi
    # FTP with a bookmark that sends text as ASCII and the rest as binary. The data connection of the server is on fixed
    # ports, because the server tells them to the client.
    ftp_image="${CYBERDUCK_SMOKE_FTP_IMAGE:-delfer/alpine-ftp-server}"
    if ftp_container="$(docker run -d --rm -p 127.0.0.1:2121:21 -p 127.0.0.1:21000-21010:21000-21010 -e ADDRESS=127.0.0.1 -e USERS="foo|pass|/ftp/foo" "$ftp_image" 2>/dev/null)"; then
        for _ in $(seq 1 50); do
            timeout 3 bash -c "exec 3<>/dev/tcp/127.0.0.1/2121; read -t 2 line <&3; [[ \$line == 220* ]]" 2>/dev/null && break
            sleep 0.5
        done
        mkdir -p "$work/ftp" "$work/home-ftp/.duck"
        cp "$HOME/.duck/cyberduck.properties" "$work/home-ftp/.duck/cyberduck.properties"
        printf 'line one\nline two\n' > "$work/ftp/text.txt"
        printf 'bin one\nbin two\n' > "$work/ftp/data.bin"
        HOME="$work/home-ftp" smoke '^SMOKE OK ftp uploaded=2$' ftp 127.0.0.1 2121 foo pass "$work/ftp"
        # 18 bytes with line feeds became 20 bytes with carriage returns, and the binary file is as it was
        [ "$(docker exec "$ftp_container" stat -c %s /ftp/foo/text.txt)" = 20 ] || fail "ftp: the text file was not sent as ASCII"
        [ "$(docker exec "$ftp_container" stat -c %s /ftp/foo/data.bin)" = 16 ] || fail "ftp: the binary file was changed"
    else
        echo "skip ftp (could not start $ftp_image)"
    fi
else
    echo "skip sftp and ftp (Docker is not available)"
fi

smoke '^SMOKE OK url 3$' url "$work/list"

echo "SMOKE SCRIPT OK"
