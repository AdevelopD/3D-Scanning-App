#include "point_cloud.h"
#include <unordered_map>
#include <unordered_set>

namespace scanforge {

bool TriangleMesh::isManifold() const {
    // An edge is manifold if it's shared by exactly 1 or 2 triangles
    struct EdgeKey {
        int v0, v1;
        bool operator==(const EdgeKey& o) const {
            return v0 == o.v0 && v1 == o.v1;
        }
    };
    struct EdgeHash {
        size_t operator()(const EdgeKey& k) const {
            return std::hash<long long>()(
                (long long)k.v0 * 1000003LL + k.v1
            );
        }
    };

    std::unordered_map<EdgeKey, int, EdgeHash> edgeCount;

    for (const auto& t : triangles_) {
        int verts[3] = {t.a, t.b, t.c};
        for (int i = 0; i < 3; i++) {
            int v0 = std::min(verts[i], verts[(i+1)%3]);
            int v1 = std::max(verts[i], verts[(i+1)%3]);
            edgeCount[{v0, v1}]++;
        }
    }

    for (const auto& [edge, count] : edgeCount) {
        if (count > 2) return false;
    }
    return true;
}

bool TriangleMesh::isWatertight() const {
    // A mesh is watertight if every edge is shared by exactly 2 triangles
    struct EdgeKey {
        int v0, v1;
        bool operator==(const EdgeKey& o) const {
            return v0 == o.v0 && v1 == o.v1;
        }
    };
    struct EdgeHash {
        size_t operator()(const EdgeKey& k) const {
            return std::hash<long long>()(
                (long long)k.v0 * 1000003LL + k.v1
            );
        }
    };

    std::unordered_map<EdgeKey, int, EdgeHash> edgeCount;

    for (const auto& t : triangles_) {
        int verts[3] = {t.a, t.b, t.c};
        for (int i = 0; i < 3; i++) {
            int v0 = std::min(verts[i], verts[(i+1)%3]);
            int v1 = std::max(verts[i], verts[(i+1)%3]);
            edgeCount[{v0, v1}]++;
        }
    }

    for (const auto& [edge, count] : edgeCount) {
        if (count != 2) return false;
    }
    return true;
}

} // namespace scanforge
