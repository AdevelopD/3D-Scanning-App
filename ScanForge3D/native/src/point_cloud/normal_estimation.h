#pragma once
#include "point_cloud.h"
#include <vector>

namespace scanforge {

/**
 * PCA-based normal estimation for point clouds.
 *
 * For each point, finds k nearest neighbors via KD-tree,
 * computes the 3x3 covariance matrix of the local neighborhood,
 * and extracts the eigenvector corresponding to the smallest
 * eigenvalue as the surface normal.
 *
 * Normal orientation is propagated via a minimum spanning tree
 * of the k-NN graph to ensure global consistency.
 */
class NormalEstimation {
public:
    explicit NormalEstimation(int k_neighbors = 15)
        : k_neighbors_(k_neighbors) {}

    /**
     * Estimate normals for all points in the cloud.
     * Returns a vector of unit normals, one per point.
     */
    std::vector<Vec3f> estimate(const PointCloud& cloud) const;

private:
    int k_neighbors_;

    /**
     * Jacobi eigenvalue decomposition for a 3x3 symmetric matrix.
     * Returns eigenvalues in ascending order and corresponding eigenvectors
     * as columns of V.
     *
     * @param A  Input symmetric matrix (row-major, 9 floats)
     * @param eigenvalues  Output eigenvalues [3]
     * @param eigenvectors Output eigenvectors (column-major, 9 floats)
     */
    static void eigenDecomposition3x3(const float A[9],
                                       float eigenvalues[3],
                                       float eigenvectors[9]);

    /**
     * Orient normals consistently using a propagation approach.
     * Uses k-NN graph and BFS to propagate orientation from a seed point.
     */
    void orientNormals(const PointCloud& cloud,
                       std::vector<Vec3f>& normals) const;
};

} // namespace scanforge
