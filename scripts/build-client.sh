#!/usr/bin/env bash
# Build the WLED Grabber client APKs without signing, installing or publishing.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODULE="all"
VARIANT="debug"
OUT="$ROOT/dist"
CLEAN=0

usage() {
  cat <<'EOF'
Usage: scripts/build-client.sh [options]

Options:
  -m, --module mobile|tv|all     Client module to build (default: all)
  -v, --variant debug|release|both
                                 Build variant(s) (default: debug)
  -o, --out DIR                  Artifact directory (default: ./dist)
      --clean                    Run Gradle clean before building
  -h, --help                     Show this help

Notes:
  - Debug APKs are signed with the debug key and are installable for testing.
  - Release APKs are unsigned; signing/publishing is a separate authorized step.
  - Outputs are copied to DIR with SHA256SUMS.txt and build-info.txt.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -m|--module) MODULE="${2:-}"; shift 2 ;;
    -v|--variant) VARIANT="${2:-}"; shift 2 ;;
    -o|--out) OUT="${2:-}"; shift 2 ;;
    --clean) CLEAN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$MODULE" in mobile|tv|all) ;; *) echo "invalid module: $MODULE" >&2; exit 2 ;; esac
case "$VARIANT" in debug|release|both) ;; *) echo "invalid variant: $VARIANT" >&2; exit 2 ;; esac

if [ ! -x "$ROOT/gradlew" ]; then
  echo "gradlew not found/executable at $ROOT/gradlew" >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  LOCAL_JDK="$ROOT/.toolchains/jdk-17.0.21+9/Contents/Home"
  if [ -x "$LOCAL_JDK/bin/java" ]; then
    export JAVA_HOME="$LOCAL_JDK"
  else
    echo "JAVA_HOME is not set and project-local JDK 17 was not found." >&2
    echo "Set JAVA_HOME to a JDK 17 installation." >&2
    exit 1
  fi
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$ROOT/local.properties" ]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -1)"
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
  exit 1
fi

mkdir -p "$OUT"
LOG="$OUT/build.log"
: > "$LOG"

MODULES=()
case "$MODULE" in
  mobile) MODULES=(mobile) ;;
  tv) MODULES=(tv) ;;
  all) MODULES=(mobile tv) ;;
esac

VARIANTS=()
case "$VARIANT" in
  debug) VARIANTS=(debug) ;;
  release) VARIANTS=(release) ;;
  both) VARIANTS=(debug release) ;;
esac

GRADLE_TASKS=()
if [ "$CLEAN" -eq 1 ]; then
  GRADLE_TASKS+=(clean)
fi
GRADLE_TASKS+=(:common:testDebugUnitTest)
for module in "${MODULES[@]}"; do
  for variant in "${VARIANTS[@]}"; do
    suffix="$(printf '%s' "${variant:0:1}" | tr '[:lower:]' '[:upper:]')${variant:1}"
    GRADLE_TASKS+=(":$module:assemble$suffix")
  done
done

echo "Building module=$MODULE variant=$VARIANT"
echo "JAVA_HOME=$JAVA_HOME"
echo "SDK=$SDK"
echo "Tasks: ${GRADLE_TASKS[*]}"

set +e
./gradlew --console=plain "${GRADLE_TASKS[@]}" 2>&1 | tee -a "$LOG"
GRADLE_STATUS=${PIPESTATUS[0]}
set -e
if [ "$GRADLE_STATUS" -ne 0 ]; then
  echo "Gradle build failed (exit $GRADLE_STATUS). Log: $LOG" >&2
  exit "$GRADLE_STATUS"
fi

AAPT2=""
for candidate in "$SDK"/build-tools/*/aapt2; do
  [ -x "$candidate" ] && AAPT2="$candidate"
done
if [ -z "$AAPT2" ]; then
  echo "aapt2 not found under $SDK/build-tools; cannot audit APK metadata." >&2
  exit 1
fi

version_name() {
  awk -F'"' '/versionName/{print $2; exit}' "$1/build.gradle"
}
version_code() {
  awk '/versionCode/{print $2; exit}' "$1/build.gradle"
}

ARTIFACTS=()
for module in "${MODULES[@]}"; do
  for variant in "${VARIANTS[@]}"; do
    suffix="$(printf '%s' "${variant:0:1}" | tr '[:lower:]' '[:upper:]')${variant:1}"
    if [ "$variant" = "debug" ]; then
      src="$module/build/outputs/apk/debug/$module-debug.apk"
      unsigned=""
    else
      src="$module/build/outputs/apk/release/$module-release-unsigned.apk"
      unsigned="-unsigned"
    fi
    if [ ! -f "$src" ]; then
      echo "expected artifact missing: $src" >&2
      exit 1
    fi
    dest="$OUT/wled-grabber-$module-$(version_name "$module")-$(version_code "$module")-$variant$unsigned.apk"
    cp -f "$src" "$dest"
    if unzip -l "$dest" | grep -qiE 'hyperiongrabber|protobuf'; then
      echo "APK contains legacy identity/protobuf entries: $dest" >&2
      exit 1
    fi
    package_line="$("$AAPT2" dump badging "$dest" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
    if [ "$package_line" != "com.abrenoch.wledgrabber" ]; then
      echo "unexpected package '$package_line' in $dest" >&2
      exit 1
    fi
    ARTIFACTS+=("$dest")
    echo "Built $dest"
  done
done

(
  cd "$OUT"
  : > SHA256SUMS.txt
  for artifact in "${ARTIFACTS[@]}"; do
    shasum -a 256 "$(basename "$artifact")" >> SHA256SUMS.txt
  done
)

{
  echo "built_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "branch=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
  echo "commit=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "dirty=$(git -C "$ROOT" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  echo "jdk=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
  echo "gradle=$("$ROOT/gradlew" --version 2>/dev/null | sed -n 's/^Gradle //p' | head -1)"
  echo "module=$MODULE"
  echo "variant=$VARIANT"
  for artifact in "${ARTIFACTS[@]}"; do
    echo
    echo "artifact=$(basename "$artifact")"
    "$AAPT2" dump badging "$artifact" | grep -E "^(package:|sdkVersion:|targetSdkVersion:|application-label:)" | head -8
  done
} > "$OUT/build-info.txt"

echo
echo "Artifacts in $OUT:"
ls -lh "${ARTIFACTS[@]}"
echo "Checksums: $OUT/SHA256SUMS.txt"
echo "Metadata:  $OUT/build-info.txt"
if [ "$VARIANT" != "debug" ]; then
  echo "NOTE: release APKs are unsigned; signing/publishing requires separate authorization."
fi
