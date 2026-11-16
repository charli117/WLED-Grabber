#!/usr/bin/env bash
#
# Android TV test launcher for the WLED lab device.
#
# Default flow:
#   1. compile the TV debug APK and the instrumented test APK
#   2. run the three JVM unit-test suites
#   3. boot an Android TV AVD (or use the connected TV) and run :tv:connectedDebugAndroidTest
#
# The WLED endpoint is passed to the tests as instrumentation arguments. Tests
# that need the physical device use Assume, so an unreachable device is skipped
# with an explicit message instead of being reported as a pass.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

HOST="10.245.0.49"
HTTP_PORT="80"
UDP_PORT="21324"
DDP_PORT="4048"
RUN_UNIT=1
RUN_DEVICE=1
INSTALL_TV_IMAGE=0
ALLOW_NO_DEVICE=0
REQUIRE_WLED=0
AVD_NAME="${WLED_TV_AVD:-wled-tv-api34}"
TV_IMAGE="${WLED_TV_IMAGE:-system-images;android-34;android-tv;arm64-v8a}"
TV_DEVICE_PROFILE="${WLED_TV_DEVICE_PROFILE:-tv_1080p}"

usage() {
    cat <<EOF
Usage: scripts/run-tv-tests.sh [options]

Options:
  --host HOST              WLED device address (default: $HOST)
  --http-port PORT         WLED HTTP port (default: $HTTP_PORT)
  --udp-port PORT          WLED realtime UDP port (default: $UDP_PORT)
  --ddp-port PORT          WLED DDP port (default: $DDP_PORT)
  --avd-name NAME          Android TV AVD name (default: $AVD_NAME)
  --device-profile NAME    AVD device profile (default: $TV_DEVICE_PROFILE)
  --unit-only              Run JVM unit tests and APK compilation only
  --device-only            Skip JVM unit tests
  --install-tv-image       Download the Android TV system image when the AVD is missing
  --allow-no-device        Do not fail when no TV/emulator is available
  --require-wled           Fail instead of skipping when the WLED device is unreachable
  -h, --help               Show this help

Environment:
  JAVA_HOME                Defaults to .toolchains/jdk-17.0.21+9/Contents/Home
  ANDROID_HOME             Defaults to sdk.dir from local.properties
  WLED_TV_AVD              Alternative AVD name
  WLED_TV_IMAGE            Alternative system image package
  WLED_TV_DEVICE_PROFILE   Alternative AVD device profile (default: tv_1080p)
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST="${2:?--host needs a value}"; shift 2 ;;
        --http-port) HTTP_PORT="${2:?--http-port needs a value}"; shift 2 ;;
        --udp-port) UDP_PORT="${2:?--udp-port needs a value}"; shift 2 ;;
        --ddp-port) DDP_PORT="${2:?--ddp-port needs a value}"; shift 2 ;;
        --avd-name) AVD_NAME="${2:?--avd-name needs a value}"; shift 2 ;;
        --device-profile) TV_DEVICE_PROFILE="${2:?--device-profile needs a value}"; shift 2 ;;
        --unit-only) RUN_DEVICE=0; shift ;;
        --device-only) RUN_UNIT=0; shift ;;
        --install-tv-image) INSTALL_TV_IMAGE=1; shift ;;
        --allow-no-device) ALLOW_NO_DEVICE=1; shift ;;
        --require-wled) REQUIRE_WLED=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    esac
done

if [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
    JAVA_HOME="$ROOT_DIR/.toolchains/jdk-17.0.21+9/Contents/Home"
fi
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "JDK 17 not found. Set JAVA_HOME or restore .toolchains/jdk-17.0.21+9." >&2
    exit 1
fi
export JAVA_HOME

SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n1)"
if [ -z "$SDK_DIR" ]; then
    SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
fi
if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
    echo "Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME." >&2
    exit 1
fi
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVDMANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

for tool in "$ADB" "$EMULATOR" "$AVDMANAGER" "$SDKMANAGER"; do
    if [ ! -x "$tool" ]; then
        echo "Required Android SDK tool not found: $tool" >&2
        echo "Install 'platform-tools', 'emulator' and 'cmdline-tools;latest'." >&2
        exit 1
    fi
done

gradle() {
    ./gradlew "$@" --console=plain
}

device_serial() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}'
}

warn_if_host_cannot_reach_device() {
    if ! command -v curl >/dev/null 2>&1; then
        return 0
    fi
    if ! curl -fsS -m 3 "http://$HOST:$HTTP_PORT/json/info" >/dev/null 2>&1; then
        echo "WARN: this host cannot reach http://$HOST:$HTTP_PORT/json/info." >&2
        echo "      The TV/emulator must be on the same reachable LAN; device tests will skip." >&2
        return 1
    fi
    return 0
}

ensure_avd() {
    if "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
        return 0
    fi
    if [ "$INSTALL_TV_IMAGE" -ne 1 ]; then
        cat >&2 <<EOF
No Android TV AVD named '$AVD_NAME' and no connected TV/emulator.

Install it explicitly, then re-run:
  "$SDKMANAGER" "$TV_IMAGE"
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$TV_IMAGE" -d "$TV_DEVICE_PROFILE"

Or re-run with --install-tv-image.
EOF
        return 1
    fi
    echo "== Installing $TV_IMAGE =="
    set +o pipefail
    yes | "$SDKMANAGER" "$TV_IMAGE"
    set -o pipefail
    if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
        echo "== Creating AVD $AVD_NAME =="
        set +o pipefail
        if ! echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$TV_IMAGE" -d "$TV_DEVICE_PROFILE" --force; then
            echo "== Retrying AVD creation without a device profile =="
            echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$TV_IMAGE" --force
        fi
        set -o pipefail
    fi
}

start_avd() {
    echo "== Booting $AVD_NAME =="
    "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot >/tmp/wled-tv-emulator.log 2>&1 &
    "$ADB" wait-for-device
    local deadline=$((SECONDS + 240))
    while [ "$SECONDS" -lt "$deadline" ]; do
        local boot=""
        boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
        if [ "$boot" = "1" ]; then
            echo "== Emulator booted =="
            return 0
        fi
        sleep 2
    done
    echo "Emulator boot timed out. Log: /tmp/wled-tv-emulator.log" >&2
    return 1
}

run_device_tests() {
    local serial
    serial="$(device_serial)"
    if [ -z "$serial" ]; then
        if ! ensure_avd; then
            return 1
        fi
        if ! start_avd; then
            return 1
        fi
        serial="$(device_serial)"
    fi
    if [ -z "$serial" ]; then
        echo "No adb device after boot attempt." >&2
        return 1
    fi
    echo "== Running TV instrumented tests on $serial (WLED $HOST:$HTTP_PORT) =="
    gradle :tv:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.wledHost="$HOST" \
        -Pandroid.testInstrumentationRunnerArguments.wledHttpPort="$HTTP_PORT" \
        -Pandroid.testInstrumentationRunnerArguments.wledUdpPort="$UDP_PORT" \
        -Pandroid.testInstrumentationRunnerArguments.wledDdpPort="$DDP_PORT"
}

echo "== Compiling TV debug + instrumented test APKs =="
gradle :tv:assembleDebug :tv:assembleDebugAndroidTest

if [ "$RUN_UNIT" -eq 1 ]; then
    echo "== Running JVM unit tests =="
    gradle :common:testDebugUnitTest :tv:testDebugUnitTest :mobile:testDebugUnitTest
fi

if [ "$RUN_DEVICE" -eq 1 ]; then
    if ! warn_if_host_cannot_reach_device; then
        if [ "$REQUIRE_WLED" -eq 1 ]; then
            echo "BLOCKED: WLED device $HOST:$HTTP_PORT is unreachable (--require-wled)." >&2
            exit 3
        fi
    fi
    if ! run_device_tests; then
        if [ "$ALLOW_NO_DEVICE" -eq 1 ]; then
            echo "WARN: TV device tests were not run (no reachable device/AVD)." >&2
            exit 0
        fi
        echo "BLOCKED: TV device tests need a connected Android TV or an installed TV AVD." >&2
        exit 2
    fi
fi

echo "== TV test run complete =="
