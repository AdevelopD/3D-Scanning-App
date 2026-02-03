#pragma once
#include "point_cloud.h"
#include <unordered_map>

namespace scanforge {

class VoxelGridFilter {
public:
    explicit VoxelGridFilter(float voxel_size) : voxel_size_(voxel_size) {}

    PointCloud apply(const PointCloud& input) const {
        struct VoxelKey {
            int x, y, z;
            bool operator==(const VoxelKey& o) const {
                return x == o.x && y == o.y && z == o.z;
            }
        };

        struct VoxelKeyHash {
            size_t operator()(const VoxelKey& k) const {
                size_t h = 0;
                h ^= std::hash<int>{}(k.x) + 0x9e3779b9 + (h << 6) + (h >> 2);
                h ^= std::hash<int>{}(k.y) + 0x9e3779b9 + (h << 6) + (h >> 2);
                h ^= std::hash<int>{}(k.z) + 0x9e3779b9 + (h << 6) + (h >> 2);
                return h;
            }
        };

        struct VoxelAccum {
            float sx = 0, sy = 0, sz = 0;
            int count = 0;
        };

        float inv_size = 1.0f / voxel_size_;
        std::unordered_map<VoxelKey, VoxelAccum, VoxelKeyHash> voxels;

        for (size_t i = 0; i < input.size(); i++) {
            const auto& p = input.getPoint(i);
            VoxelKey key {
                static_cast<int>(std::floor(p.x * inv_size)),
                static_cast<int>(std::floor(p.y * inv_size)),
                static_cast<int>(std::floor(p.z * inv_size))
            };
            auto& acc = voxels[key];
            acc.sx += p.x; acc.sy += p.y; acc.sz += p.z;
            acc.count++;
        }

        PointCloud result;
        result.reserve(voxels.size());
        for (const auto& [key, acc] : voxels) {
            result.addPoint({
                acc.sx / acc.count,
                acc.sy / acc.count,
                acc.sz / acc.count
            });
        }
        return result;
    }

private:
    float voxel_size_;
};

} // namespace scanforge
