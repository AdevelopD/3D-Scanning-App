#pragma once
#include "point_cloud.h"
#include <vector>
#include <cmath>
#include <algorithm>
#include <numeric>

namespace scanforge {

class StatisticalOutlierRemoval {
public:
    StatisticalOutlierRemoval(int k_neighbors, float std_ratio)
        : k_neighbors_(k_neighbors), std_ratio_(std_ratio) {}

    PointCloud apply(const PointCloud& input) const {
        if (input.size() < static_cast<size_t>(k_neighbors_ + 1)) return input;

        size_t n = input.size();
        std::vector<float> mean_distances(n);

        // Compute mean distance to k nearest neighbors for each point
        // Using brute-force O(n^2) for simplicity; production should use KD-tree
        for (size_t i = 0; i < n; i++) {
            std::vector<float> distances;
            distances.reserve(n - 1);

            for (size_t j = 0; j < n; j++) {
                if (i == j) continue;
                float d = input.getPoint(i).distanceTo(input.getPoint(j));
                distances.push_back(d);
            }

            // Partial sort to get k smallest distances
            std::partial_sort(distances.begin(),
                distances.begin() + k_neighbors_,
                distances.end());

            float sum = 0;
            for (int k = 0; k < k_neighbors_; k++) {
                sum += distances[k];
            }
            mean_distances[i] = sum / k_neighbors_;
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
