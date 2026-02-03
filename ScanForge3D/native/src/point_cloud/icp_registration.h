#pragma once
#include "point_cloud.h"
#include "../util/kdtree.h"
#include <array>

namespace scanforge {

struct ICPResult {
    std::array<float, 16> transformation; // 4x4 column-major
    float fitness;
    float rmse;
    int iterations;
};

class ICPRegistration {
public:
    ICPRegistration(int max_iterations, float tolerance)
        : max_iterations_(max_iterations), tolerance_(tolerance) {}

    ICPResult align(const PointCloud& source, const PointCloud& target) const;

private:
    int max_iterations_;
    float tolerance_;

    // Find closest point in target for each source point using KD-tree
    std::vector<std::pair<int, float>> findCorrespondences(
        const PointCloud& source, const KDTree& target_tree,
        const PointCloud& target) const;

    // Compute optimal rotation via SVD of cross-covariance matrix
    // Uses Eigen-free 3x3 SVD via Jacobi rotations
    void computeOptimalTransform(
        const PointCloud& source, const PointCloud& target,
        const std::vector<std::pair<int, float>>& correspondences,
        Vec3f& translation, float rotation[9]) const;

    // 3x3 SVD via iterative Jacobi rotations (no Eigen dependency)
    void svd3x3(const float H[9], float U[9], float S[3], float V[9]) const;

    // Apply 3x3 rotation + translation to a point
    Vec3f transformPoint(const Vec3f& p, const float R[9], const Vec3f& t) const;
};

} // namespace scanforge
