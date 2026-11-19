#!/usr/bin/env bash
# Builds the JNI bridge for this machine (Linux or macOS), so that BridgeHostTest can run the real model in the
# unit tests. Run scripts/prepare.sh first. Needs: cargo, a C compiler, a JDK (JAVA_HOME or javac on the PATH).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/third_party/tinyasr"
out="$root/app/build/tinyasr/host"
[ -f "$src/Cargo.toml" ] || { echo "run scripts/prepare.sh first" >&2; exit 1; }

cargo build --manifest-path "$src/Cargo.toml" --release --features c-api

java_home="${JAVA_HOME:-}"
if [ -z "$java_home" ]; then
    java_home="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
case "$(uname -s)" in
    Darwin) os=darwin; lib=libtinyasr_jni.dylib; extra="" ;;
    *) os=linux; lib=libtinyasr_jni.so; extra="-lm -ldl -lpthread" ;;
esac

mkdir -p "$out"
# shellcheck disable=SC2086
"${CC:-cc}" -shared -fPIC -O2 -Wall -Wextra \
    -I"$java_home/include" -I"$java_home/include/$os" -I"$src/include" \
    "$root/app/src/main/cpp/tinyasr_jni.c" "$src/target/release/libtinyasr.a" $extra -o "$out/$lib"
echo "host bridge: $out/$lib"
