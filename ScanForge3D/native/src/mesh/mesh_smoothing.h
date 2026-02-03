#pragma once
#include "../point_cloud/point_cloud.h"

namespace scanforge {

class MeshSmoothing {
public:
    // Laplacian smoothing: moves each vertex towards the average of its neighbors
    static void laplacianSmooth(TriangleMesh& mesh, int iterations, float lambda = 0.5f);

    // Taubin smoothing: alternating shrink/expand to prevent volume loss
    static void taubinSmooth(TriangleMesh& mesh, int iterations,
                             float lambda = 0.5f, float mu = -0.53f);
};

} // namespace scanforge
