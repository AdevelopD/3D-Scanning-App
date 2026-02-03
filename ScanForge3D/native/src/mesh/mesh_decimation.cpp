#include "mesh_decimation.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <functional>

#define LOG_TAG "ScanForge_Decimate"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace scanforge {

Vec3f MeshDecimation::Quadric::optimalVertex(const Vec3f& v1, const Vec3f& v2) const {
    // Solve 3x3 system: [a00 a01 a02] [x]   [-a03]
    //                    [a01 a11 a12] [y] = [-a13]
    //                    [a02 a12 a22] [z]   [-a23]
    float a00 = data[0], a01 = data[1], a02 = data[2], a03 = data[3];
    float a11 = data[4], a12 = data[5], a13 = data[6];
    float a22 = data[7], a23 = data[8];

    // Compute determinant of 3x3 submatrix
    float det = a00 * (a11 * a22 - a12 * a12)
              - a01 * (a01 * a22 - a12 * a02)
              + a02 * (a01 * a12 - a11 * a02);

    if (std::abs(det) < 1e-10f) {
        // Singular matrix: return midpoint
        return Vec3f(
            (v1.x + v2.x) * 0.5f,
            (v1.y + v2.y) * 0.5f,
            (v1.z + v2.z) * 0.5f
        );
    }

    float inv_det = 1.0f / det;

    // Cramer's rule
    float bx = -a03, by = -a13, bz = -a23;

    float x = inv_det * (bx * (a11 * a22 - a12 * a12)
                        - a01 * (by * a22 - a12 * bz)
                        + a02 * (by * a12 - a11 * bz));

    float y = inv_det * (a00 * (by * a22 - a12 * bz)
                        - bx * (a01 * a22 - a12 * a02)
                        + a02 * (a01 * bz - by * a02));

    float z = inv_det * (a00 * (a11 * bz - by * a12)
                        - a01 * (a01 * bz - by * a02)
                        + bx * (a01 * a12 - a11 * a02));

    // Sanity check: if result is too far from both vertices, use midpoint
    Vec3f result(x, y, z);
    Vec3f mid((v1.x + v2.x) * 0.5f, (v1.y + v2.y) * 0.5f, (v1.z + v2.z) * 0.5f);
    float edge_len = v1.distanceTo(v2);
    if (result.distanceTo(mid) > edge_len * 3.0f) {
        return mid;
    }

    return result;
}

TriangleMesh MeshDecimation::decimate(
    const TriangleMesh& input, int target_triangles) const {

    if (static_cast<int>(input.triangleCount()) <= target_triangles) {
        return input;
    }

    LOGI("QEM Decimation: %zu -> %d triangles", input.triangleCount(), target_triangles);

    // Working copy
    int n_verts = static_cast<int>(input.vertexCount());
    int n_tris = static_cast<int>(input.triangleCount());

    std::vector<Vec3f> vertices(n_verts);
    for (int i = 0; i < n_verts; i++) {
        vertices[i] = input.getVertex(i);
    }

    std::vector<Triangle> triangles(n_tris);
    for (int i = 0; i < n_tris; i++) {
        triangles[i] = input.getTriangle(i);
    }

    std::vector<bool> tri_valid(n_tris, true);
    std::vector<bool> vert_valid(n_verts, true);
    int active_tris = n_tris;

    // Compute initial quadrics per vertex
    std::vector<Quadric> quadrics(n_verts);
    for (int ti = 0; ti < n_tris; ti++) {
        const auto& t = triangles[ti];
        Vec3f e1 = vertices[t.b] - vertices[t.a];
        Vec3f e2 = vertices[t.c] - vertices[t.a];
        Vec3f n = e1.cross(e2).normalized();
        float d = -n.dot(vertices[t.a]);
        quadrics[t.a].addPlane(n.x, n.y, n.z, d);
        quadrics[t.b].addPlane(n.x, n.y, n.z, d);
        quadrics[t.c].addPlane(n.x, n.y, n.z, d);
    }

    // Build vertex adjacency (which triangles reference each vertex)
    std::vector<std::unordered_set<int>> vert_tris(n_verts);
    for (int ti = 0; ti < n_tris; ti++) {
        const auto& t = triangles[ti];
        vert_tris[t.a].insert(ti);
        vert_tris[t.b].insert(ti);
        vert_tris[t.c].insert(ti);
    }

    // Edge key for priority queue
    struct EdgeKey {
        int v0, v1;
        EdgeKey(int a, int b) : v0(std::min(a, b)), v1(std::max(a, b)) {}
        bool operator==(const EdgeKey& o) const { return v0 == o.v0 && v1 == o.v1; }
    };
    struct EdgeKeyHash {
        size_t operator()(const EdgeKey& k) const {
            return std::hash<long long>()((long long)k.v0 * 1000003LL + k.v1);
        }
    };

    // Priority queue entry
    struct EdgeCost {
        float cost;
        int v0, v1;
        int version; // for lazy deletion
        bool operator>(const EdgeCost& o) const { return cost > o.cost; }
    };

    // Version counter per vertex (incremented on collapse for lazy deletion)
    std::vector<int> vert_version(n_verts, 0);

    // Compute edge costs
    std::priority_queue<EdgeCost, std::vector<EdgeCost>, std::greater<EdgeCost>> pq;
    std::unordered_set<EdgeKey, EdgeKeyHash> processed_edges;

    auto computeEdgeCost = [&](int v0, int v1) -> float {
        Quadric q = quadrics[v0] + quadrics[v1];
        Vec3f optimal = q.optimalVertex(vertices[v0], vertices[v1]);
        return q.evaluate(optimal);
    };

    auto addEdge = [&](int v0, int v1) {
        EdgeKey ek(v0, v1);
        if (processed_edges.count(ek)) return;
        processed_edges.insert(ek);

        float cost = computeEdgeCost(ek.v0, ek.v1);
        pq.push({cost, ek.v0, ek.v1, vert_version[ek.v0] + vert_version[ek.v1]});
    };

    // Initialize all edges
    for (int ti = 0; ti < n_tris; ti++) {
        const auto& t = triangles[ti];
        addEdge(t.a, t.b);
        addEdge(t.b, t.c);
        addEdge(t.c, t.a);
    }

    // Main collapse loop
    while (active_tris > target_triangles && !pq.empty()) {
        EdgeCost ec = pq.top();
        pq.pop();

        // Lazy deletion: skip if vertices have been modified since edge was added
        if (!vert_valid[ec.v0] || !vert_valid[ec.v1]) continue;
        if (ec.version != vert_version[ec.v0] + vert_version[ec.v1]) continue;

        int keep = ec.v0;
        int remove = ec.v1;

        // Compute optimal position
        Quadric q = quadrics[keep] + quadrics[remove];
        Vec3f optimal = q.optimalVertex(vertices[keep], vertices[remove]);

        // Update kept vertex
        vertices[keep] = optimal;
        quadrics[keep] = q;
        vert_version[keep]++;

        // Mark removed vertex as invalid
        vert_valid[remove] = false;

        // Update triangles: replace references to 'remove' with 'keep'
        for (int ti : vert_tris[remove]) {
            if (!tri_valid[ti]) continue;

            Triangle& t = triangles[ti];

            // Replace vertex
            if (t.a == remove) t.a = keep;
            if (t.b == remove) t.b = keep;
            if (t.c == remove) t.c = keep;

            // Check if triangle became degenerate
            if (t.a == t.b || t.b == t.c || t.a == t.c) {
                tri_valid[ti] = false;
                active_tris--;
                // Remove from adjacency
                vert_tris[t.a].erase(ti);
                vert_tris[t.b].erase(ti);
                vert_tris[t.c].erase(ti);
            } else {
                // Move triangle to kept vertex adjacency
                vert_tris[keep].insert(ti);
            }
        }

        // Clear removed vertex adjacency
        vert_tris[remove].clear();

        // Re-insert edges around kept vertex
        processed_edges.clear(); // Simple approach: allow re-evaluation
        for (int ti : vert_tris[keep]) {
            if (!tri_valid[ti]) continue;
            const auto& t = triangles[ti];
            addEdge(t.a, t.b);
            addEdge(t.b, t.c);
            addEdge(t.c, t.a);
        }
    }

    // Build output mesh with compacted vertices
    TriangleMesh result;
    std::vector<int> vert_remap(n_verts, -1);

    for (int ti = 0; ti < n_tris; ti++) {
        if (!tri_valid[ti]) continue;

        const auto& t = triangles[ti];
        int verts[3] = {t.a, t.b, t.c};

        for (int v : verts) {
            if (vert_remap[v] == -1) {
                vert_remap[v] = static_cast<int>(result.vertexCount());
                result.addVertex(vertices[v]);
            }
        }

        result.addTriangle(vert_remap[t.a], vert_remap[t.b], vert_remap[t.c]);
    }

    LOGI("QEM Decimation result: %zu vertices, %zu triangles",
         result.vertexCount(), result.triangleCount());

    return result;
}

} // namespace scanforge
