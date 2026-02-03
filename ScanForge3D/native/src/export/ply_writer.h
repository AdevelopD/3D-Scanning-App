#pragma once
#include "../point_cloud/point_cloud.h"
#include <fstream>
#include <cstdint>

namespace scanforge {

class PLYWriter {
public:
    bool writeBinary(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath, std::ios::binary);
        if (!file.is_open()) return false;

        // PLY Header (ASCII)
        file << "ply\n";
        file << "format binary_little_endian 1.0\n";
        file << "comment ScanForge3D PLY Export\n";
        file << "element vertex " << mesh.vertexCount() << "\n";
        file << "property float x\n";
        file << "property float y\n";
        file << "property float z\n";
        file << "element face " << mesh.triangleCount() << "\n";
        file << "property list uchar int vertex_indices\n";
        file << "end_header\n";

        // Binary vertex data
        for (size_t i = 0; i < mesh.vertexCount(); i++) {
            const auto& v = mesh.getVertex(i);
            file.write(reinterpret_cast<const char*>(&v.x), 4);
            file.write(reinterpret_cast<const char*>(&v.y), 4);
            file.write(reinterpret_cast<const char*>(&v.z), 4);
        }

        // Binary face data
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& t = mesh.getTriangle(i);
            uint8_t count = 3;
            file.write(reinterpret_cast<const char*>(&count), 1);
            int32_t indices[3] = {t.a, t.b, t.c};
            file.write(reinterpret_cast<const char*>(indices), 12);
        }

        file.close();
        return true;
    }
};

} // namespace scanforge
