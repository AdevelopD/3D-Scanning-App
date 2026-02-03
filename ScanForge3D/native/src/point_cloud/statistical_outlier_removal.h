#pragma once
#include "point_cloud.h"
#include "../util/kdtree.h"
#include <vector>
#include <cmath>
#include <algorithm>

namespace scanforge {

class StatisticalOutlierRemoval {
public:
    StatisticalOutlierRemoval(int k_neighbors, float std_ratio)
        : k_neighbors_(k_neighbors), std_ratio_(std_ratio) {}

    PointCloud apply(const PointCloud& input) const {
        if (input.size() < static_cast<size_t>(k_neighbors_ + 1)) return input;

        size_t n = input.size();
        std::vector<float> mean_distances(n);

        // Build KD-tree for O(n log n) k-NN queries
        KDTree tree;
        tree.build(input);

        // Compute mean distance to k nearest neighbors for each point
        for (size_t i = 0; i < n; i++) {
            const Vec3f& p = input.getPoint(i);
            // findKNearest returns k points including the query point itself
            // so we request k+1 and skip the first (distance 0)
            std::vector<int> neighbors = tree.findKNearest(p, k_neighbors_ + 1);

            float sum = 0;
            int count = 0;
            for (int ni : neighbors) {
                if (ni == static_cast<int>(i)) continue;
                sum += p.distanceTo(input.getPoint(ni));
                count++;
                if (count >= k_neighbors_) break;
            }
            mean_distances[i] = (count > 0) ? sum / count : 0;
        }

        // Compute global mean and standard deviation
        float global_mean = 0;
        for (float d : mean_distances) global_mean += d;
        global_mean /= n;

        float variance = 0;
        for (float d : mean_distances) {
            float diff = d - global_mean;
            variance += diff * diff;
        }
        variance /= n;
        float std_dev = std::sqrt(variance);

        float threshold = global_mean + std_ratio_ * std_dev;

        // Filter points
        PointCloud result;
        for (size_t i = 0; i < n; i++) {
            if (mean_distances[i] <= threshold) {
                result.addPoint(input.getPoint(i));
            }
        }

        return result;
    }

private:
    int k_neighbors_;
    float std_ratio_;
};

} // namespace scanforge
