#include "mesh_smoothing.h"
#include <unordered_set>
#include <unordered_map>

namespace scanforge {

void MeshSmoothing::laplacianSmooth(
    TriangleMesh& mesh, int iterations, float lambda) {

    // Build adjacency: for each vertex, find its neighbors
    std::unordered_map<int, std::unordered_set<int>> adjacency;
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        adjacency[t.a].insert(t.b);
        adjacency[t.a].insert(t.c);
        adjacency[t.b].insert(t.a);
        adjacency[t.b].insert(t.c);
        adjacency[t.c].insert(t.a);
        adjacency[t.c].insert(t.b);
    }

    for (int iter = 0; iter < iterations; iter++) {
        std::vector<Vec3f> new_positions(mesh.vertexCount());

        for (size_t i = 0; i < mesh.vertexCount(); i++) {
            const auto& neighbors = adjacency[static_cast<int>(i)];
            if (neighbors.empty()) {
                new_positions[i] = mesh.getVertex(i);
                continue;
            }

            // Compute average of neighbors
            Vec3f avg(0, 0, 0);
            for (int n : neighbors) {
                avg = avg + mesh.getVertex(n);
            }
            avg = avg / static_cast<float>(neighbors.size());

            // Move vertex towards average
            Vec3f current = mesh.getVertex(i);
            new_positions[i] = current + (avg - current) * lambda;
        }

        // Apply new positions
        for (size_t i = 0; i < mesh.vertexCount(); i++) {
            mesh.vertices()[i] = new_positions[i];
        }
    }
}

void MeshSmoothing::taubinSmooth(
    TriangleMesh& mesh, int iterations, float lambda, float mu) {

    for (int iter = 0; iter < iterations; iter++) {
        // Shrink step
        laplacianSmooth(mesh, 1, lambda);
        // Expand step (negative lambda to inflate)
        laplacianSmooth(mesh, 1, mu);
    }
}

} // namespace scanforge
