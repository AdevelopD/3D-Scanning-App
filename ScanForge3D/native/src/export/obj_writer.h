#pragma once
#include "../point_cloud/point_cloud.h"
#include <fstream>

namespace scanforge {

class OBJWriter {
public:
    bool write(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath);
        if (!file.is_open()) return false;

        file << "# ScanForge3D OBJ Export\n";
        file << "# Vertices: " << mesh.vertexCount() << "\n";
        file << "# Faces: " << mesh.triangleCount() << "\n\n";

        // Write vertices
        for (size_t i = 0; i < mesh.vertexCount(); i++) {
            const auto& v = mesh.getVertex(i);
            file << "v " << v.x << " " << v.y << " " << v.z << "\n";
        }

        // Write vertex normals
        auto normals = mesh.computeVertexNormals();
        for (const auto& n : normals) {
            file << "vn " << n.x << " " << n.y << " " << n.z << "\n";
        }

        file << "\n";

        // Write faces (OBJ indices are 1-based)
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& t = mesh.getTriangle(i);
            file << "f "
                 << (t.a + 1) << "//" << (t.a + 1) << " "
                 << (t.b + 1) << "//" << (t.b + 1) << " "
                 << (t.c + 1) << "//" << (t.c + 1) << "\n";
        }

        file.close();
        return true;
    }
};

} // namespace scanforge
