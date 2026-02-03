#include "kdtree.h"
#include <limits>
#include <queue>

namespace scanforge {

void KDTree::build(const PointCloud& cloud) {
    cloud_ = &cloud;
    nodes_.clear();

    if (cloud.empty()) return;

    std::vector<int> indices(cloud.size());
    for (size_t i = 0; i < cloud.size(); i++) {
        indices[i] = static_cast<int>(i);
    }

    buildRecursive(indices, 0);
}

int KDTree::buildRecursive(std::vector<int>& indices, int depth) {
    if (indices.empty()) return -1;

    int axis = depth % 3;

    // Sort by current axis
    std::sort(indices.begin(), indices.end(),
        [this, axis](int a, int b) {
            const auto& pa = cloud_->getPoint(a);
            const auto& pb = cloud_->getPoint(b);
            float va = (axis == 0) ? pa.x : (axis == 1) ? pa.y : pa.z;
            float vb = (axis == 0) ? pb.x : (axis == 1) ? pb.y : pb.z;
            return va < vb;
        });

    int mid = static_cast<int>(indices.size()) / 2;

    Node node;
    node.point_index = indices[mid];
    node.split_axis = axis;

    int node_idx = static_cast<int>(nodes_.size());
    nodes_.push_back(node);

    // Build left subtree
    std::vector<int> left_indices(indices.begin(), indices.begin() + mid);
    nodes_[node_idx].left = left_indices.empty() ? -1 : buildRecursive(left_indices, depth + 1);

    // Build right subtree
    std::vector<int> right_indices(indices.begin() + mid + 1, indices.end());
    nodes_[node_idx].right = right_indices.empty() ? -1 : buildRecursive(right_indices, depth + 1);

    return node_idx;
}

int KDTree::findNearest(const Vec3f& query) const {
    if (nodes_.empty()) return -1;

    int best_idx = -1;
    float best_dist = std::numeric_limits<float>::max();
    searchNearest(0, query, best_idx, best_dist);
    return best_idx;
}

void KDTree::searchNearest(int node_idx, const Vec3f& query,
                           int& best_idx, float& best_dist) const {
    if (node_idx < 0 || node_idx >= static_cast<int>(nodes_.size())) return;

    const auto& node = nodes_[node_idx];
    const auto& point = cloud_->getPoint(node.point_index);

    float dist = query.distanceTo(point);
    if (dist < best_dist) {
        best_dist = dist;
        best_idx = node.point_index;
    }

    int axis = node.split_axis;
    float query_val = (axis == 0) ? query.x : (axis == 1) ? query.y : query.z;
    float split_val = (axis == 0) ? point.x : (axis == 1) ? point.y : point.z;
    float diff = query_val - split_val;

    int near_child = diff < 0 ? node.left : node.right;
    int far_child = diff < 0 ? node.right : node.left;

    searchNearest(near_child, query, best_idx, best_dist);

    if (diff * diff < best_dist * best_dist) {
        searchNearest(far_child, query, best_idx, best_dist);
    }
}

std::vector<int> KDTree::findKNearest(const Vec3f& query, int k) const {
    if (nodes_.empty() || k <= 0) return {};

    // Max-heap: largest distance on top for efficient pruning
    std::priority_queue<std::pair<float, int>> heap;
    searchKNearest(0, query, k, heap);

    std::vector<int> result;
    result.reserve(heap.size());
    while (!heap.empty()) {
        result.push_back(heap.top().second);
        heap.pop();
    }
    // Reverse to get closest first
    std::reverse(result.begin(), result.end());
    return result;
}

void KDTree::searchKNearest(int node_idx, const Vec3f& query, int k,
                            std::priority_queue<std::pair<float, int>>& heap) const {
    if (node_idx < 0 || node_idx >= static_cast<int>(nodes_.size())) return;

    const auto& node = nodes_[node_idx];
    const auto& point = cloud_->getPoint(node.point_index);

    float dist = query.distanceTo(point);

    if (static_cast<int>(heap.size()) < k) {
        heap.push({dist, node.point_index});
    } else if (dist < heap.top().first) {
        heap.pop();
        heap.push({dist, node.point_index});
    }

    int axis = node.split_axis;
    float query_val = (axis == 0) ? query.x : (axis == 1) ? query.y : query.z;
    float split_val = (axis == 0) ? point.x : (axis == 1) ? point.y : point.z;
    float diff = query_val - split_val;

    int near_child = diff < 0 ? node.left : node.right;
    int far_child = diff < 0 ? node.right : node.left;

    searchKNearest(near_child, query, k, heap);

    // Only visit far child if the splitting plane is closer than the kth best
    float plane_dist = std::abs(diff);
    if (static_cast<int>(heap.size()) < k || plane_dist < heap.top().first) {
        searchKNearest(far_child, query, k, heap);
    }
}

} // namespace scanforge
