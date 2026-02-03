#include "icp_registration.h"
#include <cmath>
#include <limits>
#include <android/log.h>

#define LOG_TAG "ScanForge_ICP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace scanforge {

std::vector<std::pair<int, float>> ICPRegistration::findCorrespondences(
    const PointCloud& source, const KDTree& target_tree,
    const PointCloud& target) const {

    std::vector<std::pair<int, float>> correspondences(source.size());

    for (size_t i = 0; i < source.size(); i++) {
        int nearest = target_tree.findNearest(source.getPoint(i));
        float dist = source.getPoint(i).distanceTo(target.getPoint(nearest));
        correspondences[i] = {nearest, dist};
    }

    return correspondences;
}

void ICPRegistration::svd3x3(const float H[9], float U[9], float S[3], float V[9]) const {
    // Jacobi SVD for 3x3 matrices
    // Compute A^T * A first for eigenvalue decomposition
    float ATA[9];
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            ATA[i * 3 + j] = 0;
            for (int k = 0; k < 3; k++) {
                ATA[i * 3 + j] += H[k * 3 + i] * H[k * 3 + j]; // H^T * H
            }
        }
    }

    // Initialize V as identity
    for (int i = 0; i < 9; i++) V[i] = 0;
    V[0] = V[4] = V[8] = 1.0f;

    // Jacobi eigenvalue iterations on ATA -> V, eigenvalues
    for (int sweep = 0; sweep < 30; sweep++) {
        for (int p = 0; p < 3; p++) {
            for (int q = p + 1; q < 3; q++) {
                float app = ATA[p * 3 + p];
                float aqq = ATA[q * 3 + q];
                float apq = ATA[p * 3 + q];

                if (std::abs(apq) < 1e-10f) continue;

                float tau = (aqq - app) / (2.0f * apq);
                float t;
                if (tau >= 0) {
                    t = 1.0f / (tau + std::sqrt(1.0f + tau * tau));
                } else {
                    t = -1.0f / (-tau + std::sqrt(1.0f + tau * tau));
                }
                float c = 1.0f / std::sqrt(1.0f + t * t);
                float s = t * c;

                // Rotate ATA
                float new_ATA[9];
                for (int i = 0; i < 9; i++) new_ATA[i] = ATA[i];

                new_ATA[p * 3 + p] = c * c * app - 2 * s * c * apq + s * s * aqq;
                new_ATA[q * 3 + q] = s * s * app + 2 * s * c * apq + c * c * aqq;
                new_ATA[p * 3 + q] = 0;
                new_ATA[q * 3 + p] = 0;

                for (int i = 0; i < 3; i++) {
                    if (i != p && i != q) {
                        float aip = ATA[i * 3 + p];
                        float aiq = ATA[i * 3 + q];
                        new_ATA[i * 3 + p] = c * aip - s * aiq;
                        new_ATA[p * 3 + i] = new_ATA[i * 3 + p];
                        new_ATA[i * 3 + q] = s * aip + c * aiq;
                        new_ATA[q * 3 + i] = new_ATA[i * 3 + q];
                    }
                }
                for (int i = 0; i < 9; i++) ATA[i] = new_ATA[i];

                // Accumulate rotation into V
                float new_V[9];
                for (int i = 0; i < 9; i++) new_V[i] = V[i];
                for (int i = 0; i < 3; i++) {
                    new_V[i * 3 + p] = c * V[i * 3 + p] - s * V[i * 3 + q];
                    new_V[i * 3 + q] = s * V[i * 3 + p] + c * V[i * 3 + q];
                }
                for (int i = 0; i < 9; i++) V[i] = new_V[i];
            }
        }
    }

    // Singular values are sqrt of eigenvalues of ATA
    S[0] = std::sqrt(std::max(0.0f, ATA[0]));
    S[1] = std::sqrt(std::max(0.0f, ATA[4]));
    S[2] = std::sqrt(std::max(0.0f, ATA[8]));

    // Sort singular values in decreasing order
    for (int i = 0; i < 2; i++) {
        for (int j = i + 1; j < 3; j++) {
            if (S[j] > S[i]) {
                std::swap(S[i], S[j]);
                // Swap columns in V
                for (int k = 0; k < 3; k++) {
                    std::swap(V[k * 3 + i], V[k * 3 + j]);
                }
            }
        }
    }

    // Compute U = H * V * S^-1
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            U[i * 3 + j] = 0;
            if (S[j] > 1e-10f) {
                for (int k = 0; k < 3; k++) {
                    U[i * 3 + j] += H[i * 3 + k] * V[k * 3 + j];
                }
                U[i * 3 + j] /= S[j];
            }
        }
    }
}

void ICPRegistration::computeOptimalTransform(
    const PointCloud& source, const PointCloud& target,
    const std::vector<std::pair<int, float>>& correspondences,
    Vec3f& translation, float rotation[9]) const {

    size_t n = source.size();

    // Compute centroids
    Vec3f src_centroid(0, 0, 0);
    Vec3f tgt_centroid(0, 0, 0);
    for (size_t i = 0; i < n; i++) {
        src_centroid = src_centroid + source.getPoint(i);
        tgt_centroid = tgt_centroid + target.getPoint(correspondences[i].first);
    }
    float fn = static_cast<float>(n);
    src_centroid = src_centroid / fn;
    tgt_centroid = tgt_centroid / fn;

    // Build cross-covariance matrix H = sum((src - src_centroid) * (tgt - tgt_centroid)^T)
    float H[9] = {0};
    for (size_t i = 0; i < n; i++) {
        Vec3f s = source.getPoint(i) - src_centroid;
        Vec3f t = target.getPoint(correspondences[i].first) - tgt_centroid;
        H[0] += s.x * t.x; H[1] += s.x * t.y; H[2] += s.x * t.z;
        H[3] += s.y * t.x; H[4] += s.y * t.y; H[5] += s.y * t.z;
        H[6] += s.z * t.x; H[7] += s.z * t.y; H[8] += s.z * t.z;
    }

    // SVD: H = U * S * V^T
    float U[9], S_vals[3], V[9];
    svd3x3(H, U, S_vals, V);

    // Rotation R = V * U^T
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            rotation[i * 3 + j] = 0;
            for (int k = 0; k < 3; k++) {
                rotation[i * 3 + j] += V[i * 3 + k] * U[j * 3 + k]; // V * U^T
            }
        }
    }

    // Check for reflection: det(R) should be +1
    float det = rotation[0] * (rotation[4] * rotation[8] - rotation[5] * rotation[7])
              - rotation[1] * (rotation[3] * rotation[8] - rotation[5] * rotation[6])
              + rotation[2] * (rotation[3] * rotation[7] - rotation[4] * rotation[6]);

    if (det < 0) {
        // Flip sign of last column of V and recompute R
        for (int i = 0; i < 3; i++) {
            V[i * 3 + 2] = -V[i * 3 + 2];
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                rotation[i * 3 + j] = 0;
                for (int k = 0; k < 3; k++) {
                    rotation[i * 3 + j] += V[i * 3 + k] * U[j * 3 + k];
                }
            }
        }
    }

    // Translation t = tgt_centroid - R * src_centroid
    translation.x = tgt_centroid.x - (rotation[0] * src_centroid.x + rotation[1] * src_centroid.y + rotation[2] * src_centroid.z);
    translation.y = tgt_centroid.y - (rotation[3] * src_centroid.x + rotation[4] * src_centroid.y + rotation[5] * src_centroid.z);
    translation.z = tgt_centroid.z - (rotation[6] * src_centroid.x + rotation[7] * src_centroid.y + rotation[8] * src_centroid.z);
}

Vec3f ICPRegistration::transformPoint(const Vec3f& p, const float R[9], const Vec3f& t) const {
    return Vec3f(
        R[0] * p.x + R[1] * p.y + R[2] * p.z + t.x,
        R[3] * p.x + R[4] * p.y + R[5] * p.z + t.y,
        R[6] * p.x + R[7] * p.y + R[8] * p.z + t.z
    );
}

ICPResult ICPRegistration::align(
    const PointCloud& source, const PointCloud& target) const {

    ICPResult result;
    // Initialize as identity matrix (column-major)
    result.transformation.fill(0);
    result.transformation[0] = result.transformation[5] =
        result.transformation[10] = result.transformation[15] = 1.0f;
    result.fitness = 0;
    result.rmse = std::numeric_limits<float>::max();
    result.iterations = 0;

    if (source.empty() || target.empty()) return result;

    // Build KD-tree for target
    KDTree target_tree;
    target_tree.build(target);

    // Working copy of source points
    PointCloud current_source;
    for (size_t i = 0; i < source.size(); i++) {
        current_source.addPoint(source.getPoint(i));
    }

    // Accumulated transformation
    float accum_R[9] = {1, 0, 0, 0, 1, 0, 0, 0, 1}; // identity
    Vec3f accum_t(0, 0, 0);

    float prev_rmse = std::numeric_limits<float>::max();

    for (int iter = 0; iter < max_iterations_; iter++) {
        // Find correspondences using KD-tree
        auto correspondences = findCorrespondences(current_source, target_tree, target);

        // Filter outlier correspondences (reject pairs with distance > 3 * median)
        std::vector<float> dists;
        dists.reserve(correspondences.size());
        for (const auto& c : correspondences) {
            dists.push_back(c.second);
        }
        std::sort(dists.begin(), dists.end());
        float median_dist = dists[dists.size() / 2];
        float max_corr_dist = std::max(median_dist * 3.0f, 0.01f);

        // Build filtered source/target for this iteration
        PointCloud filtered_src, filtered_tgt;
        std::vector<std::pair<int, float>> filtered_corr;
        for (size_t i = 0; i < current_source.size(); i++) {
            if (correspondences[i].second <= max_corr_dist) {
                int new_idx = static_cast<int>(filtered_src.size());
                filtered_src.addPoint(current_source.getPoint(i));
                filtered_corr.push_back(correspondences[i]);
            }
        }

        if (filtered_src.size() < 3) break;

        // Compute RMSE
        float rmse_sum = 0;
        for (size_t i = 0; i < filtered_src.size(); i++) {
            float d = filtered_corr[i].second;
            rmse_sum += d * d;
        }
        float current_rmse = std::sqrt(rmse_sum / static_cast<float>(filtered_src.size()));

        // Check convergence
        if (std::abs(prev_rmse - current_rmse) < tolerance_) {
            result.rmse = current_rmse;
            result.iterations = iter;
            break;
        }

        prev_rmse = current_rmse;
        result.rmse = current_rmse;
        result.iterations = iter + 1;

        // Compute optimal rotation + translation via SVD
        Vec3f step_t;
        float step_R[9];
        computeOptimalTransform(filtered_src, target, filtered_corr, step_t, step_R);

        // Accumulate: R_total = R_step * R_accum, t_total = R_step * t_accum + t_step
        float new_R[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                new_R[i * 3 + j] = 0;
                for (int k = 0; k < 3; k++) {
                    new_R[i * 3 + j] += step_R[i * 3 + k] * accum_R[k * 3 + j];
                }
            }
        }
        Vec3f new_t(
            step_R[0] * accum_t.x + step_R[1] * accum_t.y + step_R[2] * accum_t.z + step_t.x,
            step_R[3] * accum_t.x + step_R[4] * accum_t.y + step_R[5] * accum_t.z + step_t.y,
            step_R[6] * accum_t.x + step_R[7] * accum_t.y + step_R[8] * accum_t.z + step_t.z
        );

        for (int i = 0; i < 9; i++) accum_R[i] = new_R[i];
        accum_t = new_t;

        // Apply step transformation to current source
        PointCloud new_source;
        for (size_t i = 0; i < current_source.size(); i++) {
            new_source.addPoint(transformPoint(current_source.getPoint(i), step_R, step_t));
        }
        current_source = new_source;
    }

    // Build 4x4 column-major transformation matrix
    // Column-major: [R00, R10, R20, 0, R01, R11, R21, 0, R02, R12, R22, 0, tx, ty, tz, 1]
    result.transformation[0]  = accum_R[0]; // col 0
    result.transformation[1]  = accum_R[3];
    result.transformation[2]  = accum_R[6];
    result.transformation[3]  = 0;
    result.transformation[4]  = accum_R[1]; // col 1
    result.transformation[5]  = accum_R[4];
    result.transformation[6]  = accum_R[7];
    result.transformation[7]  = 0;
    result.transformation[8]  = accum_R[2]; // col 2
    result.transformation[9]  = accum_R[5];
    result.transformation[10] = accum_R[8];
    result.transformation[11] = 0;
    result.transformation[12] = accum_t.x;  // col 3
    result.transformation[13] = accum_t.y;
    result.transformation[14] = accum_t.z;
    result.transformation[15] = 1.0f;

    // Compute final fitness (fraction of inliers within threshold)
    float inlier_threshold = 0.01f; // 1cm
    int inlier_count = 0;
    auto final_corr = findCorrespondences(current_source, target_tree, target);
    for (size_t i = 0; i < current_source.size(); i++) {
        if (final_corr[i].second < inlier_threshold) inlier_count++;
    }
    result.fitness = static_cast<float>(inlier_count) /
        static_cast<float>(current_source.size());

    LOGI("ICP converged: iter=%d, fitness=%.4f, rmse=%.6f",
         result.iterations, result.fitness, result.rmse);

    return result;
}

} // namespace scanforge
