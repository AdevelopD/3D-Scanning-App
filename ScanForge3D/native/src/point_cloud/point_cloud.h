#pragma once

#include <vector>
#include <cmath>
#include <algorithm>
#include <array>

namespace scanforge {

struct Vec3f {
    float x, y, z;

    Vec3f() : x(0), y(0), z(0) {}
    Vec3f(float x, float y, float z) : x(x), y(y), z(z) {}

    Vec3f operator+(const Vec3f& o) const { return {x+o.x, y+o.y, z+o.z}; }
    Vec3f operator-(const Vec3f& o) const { return {x-o.x, y-o.y, z-o.z}; }
    Vec3f operator*(float s) const { return {x*s, y*s, z*s}; }
    Vec3f operator/(float s) const { return {x/s, y/s, z/s}; }

    float dot(const Vec3f& o) const { return x*o.x + y*o.y + z*o.z; }
    Vec3f cross(const Vec3f& o) const {
        return {y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x};
    }
    float length() const { return std::sqrt(x*x + y*y + z*z); }
    Vec3f normalized() const {
        float l = length();
        return l > 1e-8f ? *this / l : Vec3f(0, 0, 0);
    }
    float distanceTo(const Vec3f& o) const { return (*this - o).length(); }
};

struct Triangle {
    int a, b, c;
    Triangle() : a(0), b(0), c(0) {}
    Triangle(int a, int b, int c) : a(a), b(b), c(c) {}
};

class PointCloud {
public:
    void reserve(size_t n) { points_.reserve(n); }
    void addPoint(const Vec3f& p) { points_.push_back(p); }
    const Vec3f& getPoint(size_t i) const { return points_[i]; }
    size_t size() const { return points_.size(); }
    bool empty() const { return points_.empty(); }

    void clear() { points_.clear(); }
    const std::vector<Vec3f>& getPoints() const { return points_; }

    void computeBounds(Vec3f& min_bound, Vec3f& max_bound) const {
        if (points_.empty()) return;
        min_bound = max_bound = points_[0];
        for (const auto& p : points_) {
            min_bound.x = std::min(min_bound.x, p.x);
            min_bound.y = std::min(min_bound.y, p.y);
            min_bound.z = std::min(min_bound.z, p.z);
            max_bound.x = std::max(max_bound.x, p.x);
            max_bound.y = std::max(max_bound.y, p.y);
            max_bound.z = std::max(max_bound.z, p.z);
        }
    }

private:
    std::vector<Vec3f> points_;
};

class TriangleMesh {
public:
    void addVertex(const Vec3f& v) { vertices_.push_back(v); }
    void addTriangle(int a, int b, int c) { triangles_.push_back({a, b, c}); }
    void addTriangle(const Triangle& t) { triangles_.push_back(t); }

    const Vec3f& getVertex(size_t i) const { return vertices_[i]; }
    const Triangle& getTriangle(size_t i) const { return triangles_[i]; }
    size_t vertexCount() const { return vertices_.size(); }
    size_t triangleCount() const { return triangles_.size(); }

    std::vector<Vec3f>& vertices() { return vertices_; }
    std::vector<Triangle>& triangles() { return triangles_; }
    const std::vector<Vec3f>& vertices() const { return vertices_; }
    const std::vector<Triangle>& triangles() const { return triangles_; }

    Vec3f computeTriangleNormal(size_t tri_idx) const {
        const auto& t = triangles_[tri_idx];
        Vec3f e1 = vertices_[t.b] - vertices_[t.a];
        Vec3f e2 = vertices_[t.c] - vertices_[t.a];
        return e1.cross(e2).normalized();
    }

    std::vector<Vec3f> computeVertexNormals() const {
        std::vector<Vec3f> normals(vertices_.size(), {0, 0, 0});
        for (size_t i = 0; i < triangles_.size(); i++) {
            Vec3f n = computeTriangleNormal(i);
            normals[triangles_[i].a] = normals[triangles_[i].a] + n;
            normals[triangles_[i].b] = normals[triangles_[i].b] + n;
            normals[triangles_[i].c] = normals[triangles_[i].c] + n;
        }
        for (auto& n : normals) n = n.normalized();
        return normals;
    }

    bool isManifold() const;
    bool isWatertight() const;

private:
    std::vector<Vec3f> vertices_;
    std::vector<Triangle> triangles_;
};

} // namespace scanforge
