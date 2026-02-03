#include "normal_estimation.h"
#include "../util/kdtree.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <queue>
#include <vector>

#define LOG_TAG "ScanForge_Normals"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace scanforge {

void NormalEstimation::eigenDecomposition3x3(const float A[9],
                                              float eigenvalues[3],
                                              float eigenvectors[9]) {
    // Jacobi eigenvalue algorithm for 3x3 symmetric matrix
    // A is stored row-major: A[row*3 + col]
    // eigenvectors stored column-major: V[row*3 + col] = col-th eigenvector's row component

    // Work on a copy
    float S[9];
    for (int i = 0; i < 9; i++) S[i] = A[i];

    // Initialize V to identity
    for (int i = 0; i < 9; i++) eigenvectors[i] = 0;
    eigenvectors[0] = eigenvectors[4] = eigenvectors[8] = 1.0f;

    const int max_iter = 50;

    for (int iter = 0; iter < max_iter; iter++) {
        // Find largest off-diagonal element
        int p = 0, q = 1;
        float max_val = std::abs(S[1]);
        if (std::abs(S[2]) > max_val) { p = 0; q = 2; max_val = std::abs(S[2]); }
        if (std::abs(S[5]) > max_val) { p = 1; q = 2; max_val = std::abs(S[5]); }

        if (max_val < 1e-10f) break; // Converged

        // Compute rotation angle
        float app = S[p * 3 + p];
        float aqq = S[q * 3 + q];
        float apq = S[p * 3 + q];

        float theta;
        if (std::abs(app - aqq) < 1e-12f) {
            theta = 3.14159265f / 4.0f;
        } else {
            theta = 0.5f * std::atan2(2.0f * apq, app - aqq);
        }

        float c = std::cos(theta);
        float s = std::sin(theta);

        // Apply Jacobi rotation: S' = G^T * S * G
        // Only update affected rows/columns
        float new_S[9];
        for (int i = 0; i < 9; i++) new_S[i] = S[i];

        new_S[p * 3 + p] = c * c * app + 2 * c * s * apq + s * s * aqq;
        new_S[q * 3 + q] = s * s * app - 2 * c * s * apq + c * c * aqq;
        new_S[p * 3 + q] = 0;
        new_S[q * 3 + p] = 0;

        // Update the third row/column (r != p, r != q)
        int r = 3 - p - q; // the remaining index
        float srp = S[r * 3 + p];
        float srq = S[r * 3 + q];
        new_S[r * 3 + p] = c * srp + s * srq;
        new_S[p * 3 + r] = new_S[r * 3 + p];
        new_S[r * 3 + q] = -s * srp + c * srq;
        new_S[q * 3 + r] = new_S[r * 3 + q];

        for (int i = 0; i < 9; i++) S[i] = new_S[i];

        // Update eigenvectors: V' = V * G
        for (int i = 0; i < 3; i++) {
            float vip = eigenvectors[i * 3 + p];
            float viq = eigenvectors[i * 3 + q];
            eigenvectors[i * 3 + p] = c * vip + s * viq;
            eigenvectors[i * 3 + q] = -s * vip + c * viq;
        }
    }

    // Extract eigenvalues from diagonal
    float evals[3] = { S[0], S[4], S[8] };
    // Sort by ascending eigenvalue
    int order[3] = {0, 1, 2};
    if (evals[order[0]] > evals[order[1]]) std::swap(order[0], order[1]);
    if (evals[order[1]] > evals[order[2]]) std::swap(order[1], order[2]);
    if (evals[order[0]] > evals[order[1]]) std::swap(order[0], order[1]);

    eigenvalues[0] = evals[order[0]];
    eigenvalues[1] = evals[order[1]];
    eigenvalues[2] = evals[order[2]];

    // Reorder eigenvectors (stored as columns)
    float sorted_V[9];
    for (int i = 0; i < 3; i++) {
        // Column j of sorted_V = column order[j] of eigenvectors
        for (int row = 0; row < 3; row++) {
            sorted_V[row * 3 + i] = eigenvectors[row * 3 + order[i]];
        }
    }
    for (int i = 0; i < 9; i++) eigenvectors[i] = sorted_V[i];
}

std::vector<Vec3f> NormalEstimation::estimate(const PointCloud& cloud) const {
    int n = static_cast<int>(cloud.size());
    std::vector<Vec3f> normals(n, {0, 1, 0});

    if (n < 3) return normals;

    LOGI("Normal estimation: %d points, k=%d", n, k_neighbors_);

    // Build KD-tree
    KDTree tree;
    tree.build(cloud);

    int k = std::min(k_neighbors_, n);

    for (int i = 0; i < n; i++) {
        const Vec3f& p = cloud.getPoint(i);
        std::vector<int> neighbors = tree.findKNearest(p, k);

        if (static_cast<int>(neighbors.size()) < 3) {
            normals[i] = Vec3f(0, 1, 0);
            continue;
        }

        // Compute centroid of neighborhood
        Vec3f centroid(0, 0, 0);
        for (int ni : neighbors) {
            centroid = centroid + cloud.getPoint(ni);
        }
        centroid = centroid / static_cast<float>(neighbors.size());

        // Build 3x3 covariance matrix (symmetric)
        float cov[9] = {0};
        for (int ni : neighbors) {
            Vec3f d = cloud.getPoint(ni) - centroid;
            cov[0] += d.x * d.x; cov[1] += d.x * d.y; cov[2] += d.x * d.z;
            cov[3] += d.y * d.x; cov[4] += d.y * d.y; cov[5] += d.y * d.z;
            cov[6] += d.z * d.x; cov[7] += d.z * d.y; cov[8] += d.z * d.z;
        }

        // Eigendecomposition
        float eigenvalues[3];
        float eigenvectors[9];
        eigenDecomposition3x3(cov, eigenvalues, eigenvectors);

        // Normal = eigenvector with smallest eigenvalue (column 0 after sorting)
        Vec3f normal(eigenvectors[0], eigenvectors[3], eigenvectors[6]);
        float len = normal.length();
        if (len > 1e-8f) {
            normals[i] = normal / len;
        } else {
            normals[i] = Vec3f(0, 1, 0);
        }
    }

    // Orient normals consistently
    orientNormals(cloud, normals);

    LOGI("Normal estimation complete: %d normals computed", n);
    return normals;
}

void NormalEstimation::orientNormals(const PointCloud& cloud,
                                     std::vector<Vec3f>& normals) const {
    int n = static_cast<int>(cloud.size());
    if (n == 0) return;

    // Compute centroid of the entire point cloud
    Vec3f centroid(0, 0, 0);
    for (int i = 0; i < n; i++) {
        centroid = centroid + cloud.getPoint(i);
    }
    centroid = centroid / static_cast<float>(n);

    // Initial orientation: normals should point away from centroid
    for (int i = 0; i < n; i++) {
        Vec3f to_point = cloud.getPoint(i) - centroid;
        if (normals[i].dot(to_point) < 0) {
            normals[i] = normals[i] * (-1.0f);
        }
    }

    // BFS propagation for local consistency using k-NN graph
    KDTree tree;
    tree.build(cloud);

    int k = std::min(k_neighbors_, n);
    std::vector<bool> visited(n, false);
    std::queue<int> queue;

    // Start from the point farthest from centroid (most reliable orientation)
    int seed = 0;
    float max_dist = 0;
    for (int i = 0; i < n; i++) {
        float d = cloud.getPoint(i).distanceTo(centroid);
        if (d > max_dist) {
            max_dist = d;
            seed = i;
        }
    }

    visited[seed] = true;
    queue.push(seed);

    while (!queue.empty()) {
        int idx = queue.front();
        queue.pop();

        std::vector<int> neighbors = tree.findKNearest(cloud.getPoint(idx), k);
        for (int ni : neighbors) {
            if (visited[ni]) continue;
            visited[ni] = true;

            // Propagate orientation: neighbor's normal should roughly agree
            if (normals[ni].dot(normals[idx]) < 0) {
                normals[ni] = normals[ni] * (-1.0f);
            }

            queue.push(ni);
        }
    }
}

} // namespace scanforge
