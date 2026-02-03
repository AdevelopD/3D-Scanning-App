#pragma once
#include "../point_cloud/point_cloud.h"

namespace scanforge {

class MeshRepair {
public:
    void removeDegenerate(TriangleMesh& mesh) const;
    void removeDuplicateVertices(TriangleMesh& mesh) const;
    void makeManifold(TriangleMesh& mesh) const;
    void fillHoles(TriangleMesh& mesh) const;
    void orientNormals(TriangleMesh& mesh) const;
};

} // namespace scanforge
