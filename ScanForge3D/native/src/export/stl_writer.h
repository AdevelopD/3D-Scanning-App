#pragma once
#include "../point_cloud/point_cloud.h"
#include <fstream>
#include <cstdint>
#include <cstring>

namespace scanforge {

class STLWriter {
public:
    bool writeBinary(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath, std::ios::binary);
        if (!file.is_open()) return false;

        char header[80] = {};
        std::strncpy(header, "ScanForge3D Binary STL Export", 79);
        file.write(header, 80);

        uint32_t tri_count = static_cast<uint32_t>(mesh.triangleCount());
        file.write(reinterpret_cast<const char*>(&tri_count), 4);

        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& tri = mesh.getTriangle(i);
            const Vec3f& v0 = mesh.getVertex(tri.a);
            const Vec3f& v1 = mesh.getVertex(tri.b);
            const Vec3f& v2 = mesh.getVertex(tri.c);

            Vec3f normal = mesh.computeTriangleNormal(i);

            file.write(reinterpret_cast<const char*>(&normal.x), 4);
            file.write(reinterpret_cast<const char*>(&normal.y), 4);
            file.write(reinterpret_cast<const char*>(&normal.z), 4);

            file.write(reinterpret_cast<const char*>(&v0.x), 4);
            file.write(reinterpret_cast<const char*>(&v0.y), 4);
            file.write(reinterpret_cast<const char*>(&v0.z), 4);

            file.write(reinterpret_cast<const char*>(&v1.x), 4);
            file.write(reinterpret_cast<const char*>(&v1.y), 4);
            file.write(reinterpret_cast<const char*>(&v1.z), 4);

            file.write(reinterpret_cast<const char*>(&v2.x), 4);
            file.write(reinterpret_cast<const char*>(&v2.y), 4);
            file.write(reinterpret_cast<const char*>(&v2.z), 4);

            uint16_t attr = 0;
            file.write(reinterpret_cast<const char*>(&attr), 2);
        }

        file.close();
        return true;
    }

    bool writeASCII(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath);
        if (!file.is_open()) return false;

        file << "solid ScanForge3D\n";
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& tri = mesh.getTriangle(i);
            Vec3f n = mesh.computeTriangleNormal(i);
            const Vec3f& v0 = mesh.getVertex(tri.a);
            const Vec3f& v1 = mesh.getVertex(tri.b);
            const Vec3f& v2 = mesh.getVertex(tri.c);

            file << "  facet normal " << n.x << " " << n.y << " " << n.z << "\n";
            file << "    outer loop\n";
            file << "      vertex " << v0.x << " " << v0.y << " " << v0.z << "\n";
            file << "      vertex " << v1.x << " " << v1.y << " " << v1.z << "\n";
            file << "      vertex " << v2.x << " " << v2.y << " " << v2.z << "\n";
            file << "    endloop\n";
            file << "  endfacet\n";
        }
        file << "endsolid ScanForge3D\n";
        file.close();
        return true;
    }
};

} // namespace scanforge
