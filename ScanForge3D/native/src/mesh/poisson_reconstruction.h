#pragma once
#include "../point_cloud/point_cloud.h"
#include <vector>

namespace scanforge {

class PoissonReconstruction {
public:
    explicit PoissonReconstruction(int depth) : depth_(depth) {}

    // Reconstruct a watertight mesh from oriented point cloud
    // Points must have associated normals
    TriangleMesh reconstruct(const PointCloud& cloud,
                             const std::vector<Vec3f>& normals) const;

private:
    int depth_; // Octree depth (8-12)
};

} // namespace scanforge
