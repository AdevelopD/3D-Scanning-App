#!/bin/bash
# Setup third-party dependencies for native build

set -e

THIRD_PARTY_DIR="native/third_party"
mkdir -p $THIRD_PARTY_DIR

echo "=== Eigen 3.4 (Header-only) ==="
if [ ! -d "$THIRD_PARTY_DIR/eigen" ]; then
    git clone --branch 3.4.0 --depth 1 \
        https://gitlab.com/libeigen/eigen.git \
        $THIRD_PARTY_DIR/eigen
fi

echo "=== nanoflann (Header-only KD-Tree) ==="
if [ ! -d "$THIRD_PARTY_DIR/nanoflann" ]; then
    git clone --branch v1.5.4 --depth 1 \
        https://github.com/jlblancoc/nanoflann.git \
        $THIRD_PARTY_DIR/nanoflann
fi

echo "=== PoissonRecon ==="
if [ ! -d "$THIRD_PARTY_DIR/PoissonRecon" ]; then
    git clone --depth 1 \
        https://github.com/mkazhdan/PoissonRecon.git \
        $THIRD_PARTY_DIR/PoissonRecon
fi

echo "=== Done! ==="
echo "Third-party libraries installed in: $THIRD_PARTY_DIR/"
ls -la $THIRD_PARTY_DIR/
