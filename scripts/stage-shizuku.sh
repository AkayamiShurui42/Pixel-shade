#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VENDOR_DIR="$ROOT_DIR/third_party/shizuku-plus"

command -v java >/dev/null 2>&1 || {
    printf 'Pixel Shade build: JDK 17 is required.\n' >&2
    exit 1
}

COMMON_ARGS=(
    --no-daemon
    --stacktrace
    "--max-workers=${PIXEL_SHADE_MAX_WORKERS:-1}"
    -PpixelShadeClientOnly=true
)

AAPT2="${AAPT2_OVERRIDE:-}"
if [[ -z "$AAPT2" && -n "${TERMUX_VERSION:-}" ]]; then
    AAPT2=$(command -v aapt2 || true)
    [[ -n "$AAPT2" ]] || {
        printf 'Pixel Shade build: native Termux requires its ARM-compatible aapt2 package.\n' >&2
        exit 1
    }
fi
if [[ -n "$AAPT2" ]]; then
    COMMON_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
fi

chmod +x "$VENDOR_DIR/gradlew"
(
    cd "$VENDOR_DIR"
    ./gradlew "${COMMON_ARGS[@]}" \
        :aidl:assembleRelease \
        :shared:assembleRelease \
        :api:assembleRelease \
        :provider:assembleRelease
)

LIB_DIR="$ROOT_DIR/app/libs"
mkdir -p "$LIB_DIR"
cp "$VENDOR_DIR/aidl/build/outputs/aar/aidl-release.aar" "$LIB_DIR/shizuku-plus-aidl.aar"
cp "$VENDOR_DIR/shared/build/outputs/aar/shared-release.aar" "$LIB_DIR/shizuku-plus-shared.aar"
cp "$VENDOR_DIR/api/build/outputs/aar/api-release.aar" "$LIB_DIR/shizuku-plus-api.aar"
cp "$VENDOR_DIR/provider/build/outputs/aar/provider-release.aar" "$LIB_DIR/shizuku-plus-provider.aar"

printf 'Staged Shizuku Plus AARs in %s\n' "$LIB_DIR"
