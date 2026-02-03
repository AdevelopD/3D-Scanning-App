#pragma once
#include "../point_cloud/point_cloud.h"
#include <vector>
#include <algorithm>
#include <queue>

namespace scanforge {

// Simple KD-Tree for nearest-neighbor queries
// For production, use nanoflann third_party library instead
class KDTree {
public:
    struct Node {
        int point_index;
        int left = -1;
        int right = -1;
        int split_axis;
    };

    void build(const PointCloud& cloud);
    int findNearest(const Vec3f& query) const;
    std::vector<int> findKNearest(const Vec3f& query, int k) const;

    const PointCloud* getCloud() const { return cloud_; }

private:
    std::vector<Node> nodes_;
    const PointCloud* cloud_ = nullptr;

    int buildRecursive(std::vector<int>& indices, int depth);
    void searchNearest(int node_idx, const Vec3f& query,
                       int& best_idx, float& best_dist) const;
    void searchKNearest(int node_idx, const Vec3f& query, int k,
                        std::priority_queue<std::pair<float, int>>& heap) const;
};

} // namespace scanforge
