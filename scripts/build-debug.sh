#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

command -v java >/dev/null 2>&1 || {
    printf 'Pixel Shade build: JDK 17 is required.\n' >&2
    exit 1
}
[[ -n "$SDK_ROOT" ]] || {
    printf 'Pixel Shade build: set ANDROID_SDK_ROOT or ANDROID_HOME.\n' >&2
    exit 1
}
for required in \
    "$SDK_ROOT/platforms/android-35/android.jar" \
    "$SDK_ROOT/platforms/android-36/android.jar" \
    "$SDK_ROOT/build-tools/36.0.0"
do
    [[ -e "$required" ]] || {
        printf 'Pixel Shade build: missing Android SDK component: %s\n' "$required" >&2
        exit 1
    }
done

ROOT_GRADLE_ARGS=(
    --no-daemon
    "--max-workers=${PIXEL_SHADE_MAX_WORKERS:-1}"
    --stacktrace
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
    [[ -x "$AAPT2" ]] || {
        printf 'Pixel Shade build: AAPT2 override is not executable: %s\n' "$AAPT2" >&2
        exit 1
    }
    export AAPT2_OVERRIDE="$AAPT2"
    ROOT_GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
fi

bash "$ROOT_DIR/scripts/stage-shizuku.sh"

KEYSTORE="$ROOT_DIR/app/pixelshade-debug.keystore"
if base64 --help 2>&1 | grep -q -- '--decode'; then
    base64 --decode "$ROOT_DIR/ci/pixelshade-debug.keystore.b64" > "$KEYSTORE"
else
    base64 -d "$ROOT_DIR/ci/pixelshade-debug.keystore.b64" > "$KEYSTORE"
fi
keytool -list \
    -storetype JKS \
    -keystore "$KEYSTORE" \
    -storepass pixelshade-debug \
    -alias pixelshade-debug >/dev/null

bash "$ROOT_DIR/gradlew" "${ROOT_GRADLE_ARGS[@]}" \
    :app:testDebugUnitTest \
    :app:lintDebug \
    :app:assembleDebug

APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
printf 'Pixel Shade debug APK: %s\n' "$APK"
