#include "poisson_reconstruction.h"
#include "marching_cubes.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "ScanForge_Poisson"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace scanforge {

TriangleMesh PoissonReconstruction::reconstruct(
    const PointCloud& cloud,
    const std::vector<Vec3f>& normals) const {

    LOGI("Surface reconstruction: %zu points, depth=%d", cloud.size(), depth_);

    if (cloud.empty() || normals.empty()) {
        return TriangleMesh();
    }

    // Convert octree depth to voxel size:
    // depth_ controls the resolution. Higher depth = finer voxels.
    // We compute voxel size from the bounding box diagonal and depth.
    //   grid_resolution = 2^depth_
    //   voxel_size = diagonal / grid_resolution
    Vec3f min_bound, max_bound;
    cloud.computeBounds(min_bound, max_bound);

    float dx = max_bound.x - min_bound.x;
    float dy = max_bound.y - min_bound.y;
    float dz = max_bound.z - min_bound.z;
    float diagonal = std::sqrt(dx * dx + dy * dy + dz * dz);

    if (diagonal < 1e-8f) {
        LOGI("Point cloud has zero extent, cannot reconstruct");
        return TriangleMesh();
    }

    // Clamp depth to safe range for mobile (max grid ~200^3)
    int effective_depth = std::max(4, std::min(depth_, 12));
    float grid_resolution = static_cast<float>(1 << effective_depth);
    float voxel_size = diagonal / grid_resolution;

    // Ensure voxel size produces a manageable grid
    float max_dim = std::max({dx, dy, dz});
    int estimated_cells = static_cast<int>(max_dim / voxel_size);
    if (estimated_cells > 200) {
        voxel_size = max_dim / 200.0f;
        LOGI("Clamped voxel size to %.6f for mobile (max 200 cells)", voxel_size);
    }

    LOGI("Voxel size: %.6f (depth=%d, diagonal=%.4f)", voxel_size, effective_depth, diagonal);

    // Use Marching Cubes for surface reconstruction
    // MarchingCubes computes an SDF from the oriented point cloud
    // and extracts the zero-isosurface as a triangle mesh
    int padding = 2;
    MarchingCubes mc(voxel_size, padding);
    TriangleMesh mesh = mc.reconstruct(cloud, normals);

    LOGI("Reconstruction result: %zu vertices, %zu triangles",
         mesh.vertexCount(), mesh.triangleCount());

    return mesh;
}

} // namespace scanforge
