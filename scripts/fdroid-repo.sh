#!/usr/bin/env bash
# Builds an F-Droid repository (index + APK) for one APK, signed with the release key.
#   scripts/fdroid-repo.sh <apk> <output dir> <repo url>
# Needs: fdroidserver (pip install fdroidserver), the Android SDK (ANDROID_HOME), and the signing variables
# SIGNING_KEYSTORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD.
set -euo pipefail

apk="$(readlink -f "$1")"
out="$(mkdir -p "$2" && cd "$2" && pwd)"
url="$3"
root="$(cd "$(dirname "$0")/.." && pwd)"
: "${SIGNING_KEYSTORE_FILE:?}" "${SIGNING_STORE_PASSWORD:?}" "${SIGNING_KEY_ALIAS:?}" "${SIGNING_KEY_PASSWORD:?}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -r "$root/fdroid/metadata" "$work/metadata"
mkdir -p "$work/repo/icons"
cp "$apk" "$work/repo/"
cp "$root/fdroid/metadata/app.voicekb.tinyasr/en-US/icon.png" "$work/repo/icons/icon.png"

# The passwords stay in the environment; the file only names the variables.
cat > "$work/config.yml" <<CONFIG
repo_url: $url
repo_name: TinyASR Voice Keyboard
repo_description: Offline voice keyboard for Android, built from https://github.com/yaroslav-khomyak/tinyasr-keyboard
archive_older: 0
${ANDROID_HOME:+sdk_path: $ANDROID_HOME}
keystore: $(readlink -f "$SIGNING_KEYSTORE_FILE")
repo_keyalias: $SIGNING_KEY_ALIAS
keystorepass: {env: SIGNING_STORE_PASSWORD}
keypass: {env: SIGNING_KEY_PASSWORD}
CONFIG
chmod 600 "$work/config.yml"

(cd "$work" && fdroid update --pretty)

rm -rf "$out/repo"
cp -r "$work/repo" "$out/repo"
echo "F-Droid repository: $out/repo"
