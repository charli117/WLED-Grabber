#!/usr/bin/env bash
# QA-2 on-device measurement helper.
#
# Captures the software-side gates (sampling / send-interval percentiles emitted
# by WledScreenService every 300 sent frames) and samples the WLED show-layer
# fps. Physical FPS and end-to-end latency still require a light sensor or
# high-speed camera; this script records software evidence, never a physical
# verdict.
#
# Usage:
#   scripts/qa/measure-qa2.sh <adb-serial> <wled-host> [seconds] [out-dir]
#
# The capture itself needs the system consent dialog, so start it on the TV
# before running this script:
#   adb -s <serial> shell monkey -p com.abrenoch.wledgrabber \
#       -c android.intent.category.LEANBACK_LAUNCHER 1
# then press the power button and accept the dialog.
set -euo pipefail

SERIAL="${1:-}"
WLED_HOST="${2:-}"
SECONDS_TO_SAMPLE="${3:-30}"
OUT_DIR="${4:-docs/verification/wled/qa-2}"
PKG="com.abrenoch.wledgrabber"

if [ -z "$SERIAL" ] || [ -z "$WLED_HOST" ]; then
  echo "usage: $0 <adb-serial> <wled-host> [seconds] [out-dir]" >&2
  exit 2
fi
command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl not found" >&2; exit 1; }

mkdir -p "$OUT_DIR"
STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
OUT="$OUT_DIR/measure-$STAMP.txt"

if [ "$(adb -s "$SERIAL" shell "dumpsys activity services $PKG | grep -c isForeground=true" | tr -d '\r')" = "0" ]; then
  echo "capture is not running on $SERIAL; start it and accept the consent dialog first" >&2
  exit 1
fi

{
  echo "# QA-2 software-side measurement $STAMP"
  echo "serial=$SERIAL wled=$WLED_HOST duration=${SECONDS_TO_SAMPLE}s"
  echo "device=$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r') api=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "config=$(adb -s "$SERIAL" shell "run-as $PKG grep -hE 'pref_key_framerate|pref_key_capture_max_dimension|pref_key_wled_physical_leds' /data/data/$PKG/shared_prefs/${PKG}_preferences.xml" | tr -d '\r' | tr '\n' ' ')"
  echo
  echo "## service qa2 percentiles (sampling_us / interval_us)"
  adb -s "$SERIAL" logcat -c
  echo "## WLED show-layer fps samples (1 per second, NOT physical evidence)"
} > "$OUT"

end=$(( $(date +%s) + SECONDS_TO_SAMPLE ))
while [ "$(date +%s)" -lt "$end" ]; do
  curl -s -m 3 "http://$WLED_HOST/json/info" \
    | tr ',' '\n' \
    | grep -E '"(live|lm|lip)"' \
    | tr '\n' ' ' >> "$OUT" || true
  curl -s -m 3 "http://$WLED_HOST/json/info" \
    | sed -n 's/.*"fps":\([0-9-]*\).*/fps=\1/p' | tr -d '\n' >> "$OUT" || true
  echo >> "$OUT"
  sleep 1
done

{
  echo
  echo "## qa2 logcat lines"
  adb -s "$SERIAL" logcat -d -s WledScreenService | grep "qa2" || true
  echo
  echo "## note"
  echo "Physical FPS and end-to-end latency are NOT measured here."
  echo "Record light-sensor / high-speed-camera evidence separately before QA-2 can pass."
} >> "$OUT"

echo "wrote $OUT"
