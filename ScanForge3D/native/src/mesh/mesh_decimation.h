#pragma once
#include "../point_cloud/point_cloud.h"
#include <vector>
#include <queue>
#include <unordered_set>
#include <unordered_map>

namespace scanforge {

/**
 * Mesh decimation via Quadric Error Metrics (QEM).
 *
 * Algorithm (Garland & Heckbert 1997):
 * 1. Compute error quadric Q for each vertex from adjacent face planes
 * 2. For each edge (v1, v2), compute cost = vbar^T * (Q1 + Q2) * vbar
 *    where vbar is the optimal contraction target
 * 3. Place all edges in a min-heap by cost
 * 4. Collapse cheapest edge, update neighbors
 * 5. Repeat until target triangle count reached
 */
class MeshDecimation {
public:
    TriangleMesh decimate(const TriangleMesh& input, int target_triangles) const;

private:
    // Symmetric 4x4 matrix stored as 10 unique elements
    struct Quadric {
        float data[10]; // a00, a01, a02, a03, a11, a12, a13, a22, a23, a33

        Quadric() { for (int i = 0; i < 10; i++) data[i] = 0; }

        void addPlane(float a, float b, float c, float d) {
            data[0] += a*a; data[1] += a*b; data[2] += a*c; data[3] += a*d;
            data[4] += b*b; data[5] += b*c; data[6] += b*d;
            data[7] += c*c; data[8] += c*d;
            data[9] += d*d;
        }

        Quadric operator+(const Quadric& o) const {
            Quadric r;
            for (int i = 0; i < 10; i++) r.data[i] = data[i] + o.data[i];
            return r;
        }

        // Evaluate quadric error for vertex v: v^T * Q * v
        float evaluate(const Vec3f& v) const {
            return data[0]*v.x*v.x + 2*data[1]*v.x*v.y + 2*data[2]*v.x*v.z + 2*data[3]*v.x
                 + data[4]*v.y*v.y + 2*data[5]*v.y*v.z + 2*data[6]*v.y
                 + data[7]*v.z*v.z + 2*data[8]*v.z
                 + data[9];
        }

        // Find optimal vertex position minimizing error
        // Solves the 3x3 linear system from dQ/dv = 0
        // Returns midpoint if system is singular
        Vec3f optimalVertex(const Vec3f& v1, const Vec3f& v2) const;
    };
};

} // namespace scanforge
