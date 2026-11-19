#!/usr/bin/env bash
# Fetches tinyasr at the pinned commit, builds its static library for Android and unpacks the models.
# Needs: git, gzip, rustup (cargo). No NDK is needed for this step: a static library is not linked.
set -euo pipefail

TINYASR_REPO="https://github.com/dkaraush/tinyasr.git"
TINYASR_REV="054bd19e2dcf1a021d8808a75e974ad675ceba03"
MODEL="en-ru-uk"

root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/third_party/tinyasr"
out="$root/app/build/tinyasr"

if [ ! -d "$src/.git" ]; then
    mkdir -p "$src"
    git -C "$src" init -q
    git -C "$src" remote add origin "$TINYASR_REPO"
fi
if [ "$(git -C "$src" rev-parse -q --verify HEAD 2>/dev/null || true)" != "$TINYASR_REV" ]; then
    git -C "$src" fetch -q --depth 1 origin "$TINYASR_REV"
    git -C "$src" checkout -q --detach FETCH_HEAD
fi

# ABI -> Rust target
build() {
    local abi="$1" target="$2"
    rustup target add "$target"
    cargo build --manifest-path "$src/Cargo.toml" --release --features c-api --target "$target"
    mkdir -p "$out/$abi"
    cp "$src/target/$target/release/libtinyasr.a" "$out/$abi/libtinyasr.a"
}
build arm64-v8a aarch64-linux-android

mkdir -p "$out/assets"
gzip -dc "$src/models/$MODEL.tzf.gz" > "$out/assets/$MODEL.tzf"
gzip -dc "$src/models/silero_vad.onnx.gz" > "$out/assets/silero_vad.onnx"

echo "tinyasr $TINYASR_REV ready in $out"
