#pragma once

#include <cmath>
#include <array>
#include <algorithm>

namespace scanforge {

// 4x4 Matrix (column-major, kompatibel mit OpenGL/ARCore)
using Mat4f = std::array<float, 16>;

inline Mat4f identity4x4() {
    Mat4f m = {};
    m[0] = m[5] = m[10] = m[15] = 1.0f;
    return m;
}

inline float clamp(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

} // namespace scanforge
