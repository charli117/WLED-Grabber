#!/usr/bin/env bash
# QA-1 local regression: JVM tests, lint, debug builds, androidTest compile,
# document links and release-artifact legacy/protobuf audit.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchains/jdk-17.0.21+9/Contents/Home}"

./gradlew \
  :common:testDebugUnitTest \
  :mobile:testDebugUnitTest \
  :tv:testDebugUnitTest \
  :common:lintDebug \
  :mobile:lintDebug \
  :tv:lintDebug \
  :mobile:assembleDebug \
  :tv:assembleDebug \
  :common:assembleDebugAndroidTest \
  :mobile:assembleDebugAndroidTest \
  :tv:assembleDebugAndroidTest \
  :mobile:assembleRelease \
  :tv:assembleRelease

python3 -I "$ROOT/scripts/qa/check-doc-links.py"

if grep -R -n -E "com\.abrenoch\.hyperiongrabber|HyperionThread|HyperionProto|HyperionScreenService|HyperionScreenEncoder" \
    common/src mobile/src tv/src; then
  echo "legacy reference found" >&2
  exit 1
fi

for apk in mobile/build/outputs/apk/release/mobile-release-unsigned.apk \
           tv/build/outputs/apk/release/tv-release-unsigned.apk; do
  if [ -f "$apk" ] && unzip -l "$apk" | grep -qiE 'hyperiongrabber|protobuf'; then
    echo "legacy artifact entry in $apk" >&2
    exit 1
  fi
done

echo "QA-1 local regression passed"
