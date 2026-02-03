#!/bin/bash
# scripts/build_native.sh
# Builds the native C++ code via Gradle's CMake integration.
# Prerequisites: Android NDK installed, ANDROID_HOME set.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== ScanForge3D Native Build ==="
echo "Project: $PROJECT_DIR"

# Check third-party dependencies
THIRD_PARTY="$PROJECT_DIR/native/third_party"
if [ ! -d "$THIRD_PARTY/eigen/Eigen" ] || [ ! -d "$THIRD_PARTY/nanoflann/include" ]; then
    echo "Third-party dependencies missing. Running setup_ndk.sh first..."
    bash "$SCRIPT_DIR/setup_ndk.sh"
fi

# Build via Gradle
cd "$PROJECT_DIR"

if [ "$1" = "release" ]; then
    echo "Building native (Release)..."
    ./gradlew :app:externalNativeBuildRelease
else
    echo "Building native (Debug)..."
    ./gradlew :app:externalNativeBuildDebug
fi

echo "=== Native build complete ==="

# Show output libraries
echo "Built libraries:"
find app/build -name "libscanforge_native.so" 2>/dev/null | while read lib; do
    echo "  $lib ($(du -h "$lib" | cut -f1))"
done
