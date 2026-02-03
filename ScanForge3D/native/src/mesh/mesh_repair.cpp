#include "mesh_repair.h"
#include <android/log.h>
#include <unordered_map>
#include <unordered_set>
#include <queue>
#include <cmath>
#include <algorithm>

#define LOG_TAG "ScanForge_Repair"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace scanforge {

// Shared edge key helper
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

// Directed half-edge key
struct HalfEdgeKey {
    int from, to;
    HalfEdgeKey(int f, int t) : from(f), to(t) {}
    bool operator==(const HalfEdgeKey& o) const { return from == o.from && to == o.to; }
};
struct HalfEdgeKeyHash {
    size_t operator()(const HalfEdgeKey& k) const {
        return std::hash<long long>()((long long)k.from * 1000003LL + k.to);
    }
};

void MeshRepair::removeDegenerate(TriangleMesh& mesh) const {
    std::vector<Triangle> valid;
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        // Remove triangles with duplicate indices
        if (t.a != t.b && t.b != t.c && t.a != t.c) {
            // Check for zero-area triangles
            Vec3f e1 = mesh.getVertex(t.b) - mesh.getVertex(t.a);
            Vec3f e2 = mesh.getVertex(t.c) - mesh.getVertex(t.a);
            float area = e1.cross(e2).length() * 0.5f;
            if (area > 1e-10f) {
                valid.push_back(t);
            }
        }
    }

    size_t removed = mesh.triangleCount() - valid.size();
    if (removed > 0) {
        LOGI("Removed %zu degenerate triangles", removed);
        mesh.triangles().clear();
        for (const auto& t : valid) {
            mesh.addTriangle(t);
        }
    }
}

void MeshRepair::removeDuplicateVertices(TriangleMesh& mesh) const {
    float epsilon = 1e-6f;
    float inv_cell = 1.0f / epsilon;

    struct GridKey {
        int x, y, z;
        bool operator==(const GridKey& o) const {
            return x == o.x && y == o.y && z == o.z;
        }
    };
    struct GridKeyHash {
        size_t operator()(const GridKey& k) const {
            size_t h = 0;
            h ^= std::hash<int>{}(k.x) + 0x9e3779b9 + (h << 6) + (h >> 2);
            h ^= std::hash<int>{}(k.y) + 0x9e3779b9 + (h << 6) + (h >> 2);
            h ^= std::hash<int>{}(k.z) + 0x9e3779b9 + (h << 6) + (h >> 2);
            return h;
        }
    };

    // Map from grid cell to canonical vertex index
    std::unordered_map<GridKey, int, GridKeyHash> grid_map;
    // Map from old vertex index to new vertex index
    std::vector<int> remap(mesh.vertexCount(), -1);
    std::vector<Vec3f> new_vertices;

    for (size_t i = 0; i < mesh.vertexCount(); i++) {
        const Vec3f& v = mesh.getVertex(i);
        GridKey key{
            static_cast<int>(std::floor(v.x * inv_cell)),
            static_cast<int>(std::floor(v.y * inv_cell)),
            static_cast<int>(std::floor(v.z * inv_cell))
        };

        auto it = grid_map.find(key);
        if (it != grid_map.end()) {
            remap[i] = it->second;
        } else {
            int new_idx = static_cast<int>(new_vertices.size());
            new_vertices.push_back(v);
            grid_map[key] = new_idx;
            remap[i] = new_idx;
        }
    }

    size_t removed = mesh.vertexCount() - new_vertices.size();
    if (removed > 0) {
        LOGI("Merged %zu duplicate vertices (%zu -> %zu)",
             removed, mesh.vertexCount(), new_vertices.size());

        // Rebuild mesh with remapped indices
        std::vector<Triangle> old_triangles;
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            old_triangles.push_back(mesh.getTriangle(i));
        }

        mesh.vertices().clear();
        mesh.triangles().clear();

        for (const auto& v : new_vertices) {
            mesh.addVertex(v);
        }

        for (const auto& t : old_triangles) {
            int a = remap[t.a];
            int b = remap[t.b];
            int c = remap[t.c];
            // Skip degenerate after remapping
            if (a != b && b != c && a != c) {
                mesh.addTriangle(a, b, c);
            }
        }
    }
}

void MeshRepair::makeManifold(TriangleMesh& mesh) const {
    // Count edge usage: edges shared by more than 2 triangles are non-manifold
    std::unordered_map<EdgeKey, std::vector<int>, EdgeKeyHash> edge_triangles;

    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        int verts[3] = {t.a, t.b, t.c};
        for (int e = 0; e < 3; e++) {
            EdgeKey ek(verts[e], verts[(e + 1) % 3]);
            edge_triangles[ek].push_back(static_cast<int>(i));
        }
    }

    // Mark triangles to remove (those contributing to non-manifold edges)
    std::unordered_set<int> remove_tris;
    for (const auto& [edge, tris] : edge_triangles) {
        if (tris.size() > 2) {
            // Keep the first 2 triangles, remove the rest
            for (size_t i = 2; i < tris.size(); i++) {
                remove_tris.insert(tris[i]);
            }
        }
    }

    if (!remove_tris.empty()) {
        LOGI("Removing %zu triangles for manifold repair", remove_tris.size());
        std::vector<Triangle> valid;
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            if (remove_tris.find(static_cast<int>(i)) == remove_tris.end()) {
                valid.push_back(mesh.getTriangle(i));
            }
        }
        mesh.triangles().clear();
        for (const auto& t : valid) {
            mesh.addTriangle(t);
        }
    }
}

void MeshRepair::fillHoles(TriangleMesh& mesh) const {
    // Find boundary edges (edges used by exactly 1 triangle)
    // A boundary edge has a half-edge with no opposite
    std::unordered_map<HalfEdgeKey, int, HalfEdgeKeyHash> halfedge_tri;

    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        halfedge_tri[{t.a, t.b}] = static_cast<int>(i);
        halfedge_tri[{t.b, t.c}] = static_cast<int>(i);
        halfedge_tri[{t.c, t.a}] = static_cast<int>(i);
    }

    // Find boundary half-edges (those without opposite)
    std::unordered_map<int, int> boundary_next; // from -> to
    for (const auto& [he, tri] : halfedge_tri) {
        HalfEdgeKey opposite(he.to, he.from);
        if (halfedge_tri.find(opposite) == halfedge_tri.end()) {
            boundary_next[he.to] = he.from; // reverse direction for boundary loop
        }
    }

    if (boundary_next.empty()) {
        LOGI("fillHoles: mesh is already closed");
        return;
    }

    // Trace boundary loops
    std::unordered_set<int> visited;
    int holes_filled = 0;

    for (const auto& [start, next] : boundary_next) {
        if (visited.count(start)) continue;

        // Trace loop
        std::vector<int> loop;
        int current = start;
        bool valid_loop = true;

        while (true) {
            if (visited.count(current)) {
                if (current == start && loop.size() >= 3) {
                    break; // closed loop
                }
                valid_loop = false;
                break;
            }
            visited.insert(current);
            loop.push_back(current);

            auto it = boundary_next.find(current);
            if (it == boundary_next.end()) {
                valid_loop = false;
                break;
            }
            current = it->second;

            if (loop.size() > 1000) {
                valid_loop = false;
                break;
            }
        }

        if (!valid_loop || loop.size() < 3) continue;

        // Fill hole with fan triangulation from centroid
        // Compute centroid of boundary loop
        Vec3f centroid(0, 0, 0);
        for (int vi : loop) {
            centroid = centroid + mesh.getVertex(vi);
        }
        centroid = centroid / static_cast<float>(loop.size());

        // Add centroid vertex
        int centroid_idx = static_cast<int>(mesh.vertexCount());
        mesh.addVertex(centroid);

        // Create fan triangles
        for (size_t i = 0; i < loop.size(); i++) {
            int v0 = loop[i];
            int v1 = loop[(i + 1) % loop.size()];
            mesh.addTriangle(v0, v1, centroid_idx);
        }

        holes_filled++;
    }

    LOGI("fillHoles: filled %d holes", holes_filled);
}

void MeshRepair::orientNormals(TriangleMesh& mesh) const {
    if (mesh.triangleCount() == 0) return;

    // Build adjacency: for each triangle, find neighbors sharing an edge
    std::unordered_map<EdgeKey, std::vector<int>, EdgeKeyHash> edge_tris;
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        int verts[3] = {t.a, t.b, t.c};
        for (int e = 0; e < 3; e++) {
            edge_tris[EdgeKey(verts[e], verts[(e + 1) % 3])].push_back(static_cast<int>(i));
        }
    }

    // BFS from triangle 0, propagating consistent orientation
    std::vector<bool> visited(mesh.triangleCount(), false);
    std::vector<bool> flip(mesh.triangleCount(), false);
    std::queue<int> queue;

    visited[0] = true;
    queue.push(0);
    int flipped_count = 0;

    while (!queue.empty()) {
        int ti = queue.front();
        queue.pop();

        const auto& t = mesh.getTriangle(ti);
        int verts[3] = {t.a, t.b, t.c};

        // Check each edge neighbor
        for (int e = 0; e < 3; e++) {
            int v0 = verts[e];
            int v1 = verts[(e + 1) % 3];
            EdgeKey ek(v0, v1);

            auto it = edge_tris.find(ek);
            if (it == edge_tris.end()) continue;

            for (int ni : it->second) {
                if (ni == ti || visited[ni]) continue;

                // Check if neighbor has consistent orientation
                // Two adjacent triangles should have opposite half-edge directions
                // on their shared edge for consistent orientation
                const auto& nt = mesh.getTriangle(ni);
                int nverts[3] = {nt.a, nt.b, nt.c};

                // Find shared edge in neighbor
                bool same_direction = false;
                for (int ne = 0; ne < 3; ne++) {
                    int nv0 = nverts[ne];
                    int nv1 = nverts[(ne + 1) % 3];
                    // In current triangle (possibly flipped), edge goes v0->v1
                    // For consistent normals, neighbor should have v1->v0
                    int cur_v0 = flip[ti] ? v1 : v0;
                    int cur_v1 = flip[ti] ? v0 : v1;
                    if (nv0 == cur_v0 && nv1 == cur_v1) {
                        same_direction = true;
                        break;
                    }
                }

                visited[ni] = true;
                if (same_direction) {
                    flip[ni] = true;
                    flipped_count++;
                } else {
                    flip[ni] = flip[ti] ? false : false; // keep as is relative to parent
                }
                queue.push(ni);
            }
        }
    }

    // Apply flips
    if (flipped_count > 0) {
        LOGI("orientNormals: flipping %d triangles for consistent orientation", flipped_count);
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            if (flip[i]) {
                const auto& t = mesh.getTriangle(i);
                // Swap b and c to flip winding
                mesh.triangles()[i] = Triangle(t.a, t.c, t.b);
            }
        }
    }

    // Determine majority orientation: most triangles should have outward-facing normals
    // Use a heuristic: normals should point away from mesh centroid
    Vec3f centroid(0, 0, 0);
    for (size_t i = 0; i < mesh.vertexCount(); i++) {
        centroid = centroid + mesh.getVertex(i);
    }
    centroid = centroid / static_cast<float>(mesh.vertexCount());

    int outward = 0, inward = 0;
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        Vec3f face_center = (mesh.getVertex(t.a) + mesh.getVertex(t.b) + mesh.getVertex(t.c)) / 3.0f;
        Vec3f to_center = centroid - face_center;
        Vec3f normal = mesh.computeTriangleNormal(i);
        if (normal.dot(to_center) < 0) {
            outward++;
        } else {
            inward++;
        }
    }

    // If majority points inward, flip all
    if (inward > outward) {
        LOGI("orientNormals: flipping all triangles (majority inward)");
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& t = mesh.getTriangle(i);
            mesh.triangles()[i] = Triangle(t.a, t.c, t.b);
        }
    }

    LOGI("orientNormals: %zu triangles processed", mesh.triangleCount());
}

} // namespace scanforge
