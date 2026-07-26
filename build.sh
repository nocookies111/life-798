#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:-all}"
GRADLE_BIN="${GRADLE_BIN:-$ROOT_DIR/gradlew}"

if ! command -v "$GRADLE_BIN" >/dev/null 2>&1; then
  echo "ERROR: 未找到 Gradle Wrapper，请检查仓库文件，或通过 GRADLE_BIN 指定 Gradle 8.13。"
  exit 1
fi

cd "$ROOT_DIR"

run_tests() {
  "$GRADLE_BIN" testDebugUnitTest --no-daemon
}

build_debug() {
  "$GRADLE_BIN" assembleDebug --no-daemon
  echo "Debug APK: $ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
}

build_release() {
  "$GRADLE_BIN" assembleRelease --no-daemon
  echo "Release APK: $ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
}

case "$MODE" in
  test)
    run_tests
    ;;
  debug)
    run_tests
    build_debug
    ;;
  release)
    run_tests
    build_release
    ;;
  all)
    run_tests
    build_debug
    ;;
  *)
    echo "用法: ./build.sh [test|debug|release|all]"
    exit 2
    ;;
esac
