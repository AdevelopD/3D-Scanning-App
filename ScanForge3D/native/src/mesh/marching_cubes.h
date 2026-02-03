#pragma once
#include "../point_cloud/point_cloud.h"
#include <vector>

namespace scanforge {

/**
 * Marching Cubes surface reconstruction from point clouds.
 *
 * Pipeline:
 * 1. Build 3D voxel grid from point cloud bounding box
 * 2. Compute signed distance field (SDF) at each grid vertex
 * 3. For each cube cell, determine surface intersection
 * 4. Use lookup tables to generate triangles
 */
class MarchingCubes {
public:
    MarchingCubes(float voxel_size, int padding = 2)
        : voxel_size_(voxel_size), padding_(padding) {}

    // Reconstruct surface from point cloud
    // Points should have normals for SDF computation
    TriangleMesh reconstruct(const PointCloud& cloud,
                             const std::vector<Vec3f>& normals) const;

private:
    float voxel_size_;
    int padding_;

    // Compute signed distance field on a 3D grid
    std::vector<float> computeSDF(
        const PointCloud& cloud, const std::vector<Vec3f>& normals,
        const Vec3f& grid_origin, int nx, int ny, int nz) const;

    // Interpolate vertex position on edge between two grid vertices
    Vec3f interpolateEdge(const Vec3f& p1, const Vec3f& p2,
                          float v1, float v2) const;

    // Edge table: maps cube configuration to active edges
    static const int EDGE_TABLE[256];

    // Triangle table: maps cube configuration to triangle vertex indices on edges
    static const int TRI_TABLE[256][16];
};

} // namespace scanforge
