#include "jni_bridge.h"
#include <android/log.h>
#include "point_cloud/point_cloud.h"
#include "point_cloud/icp_registration.h"
#include "point_cloud/voxel_grid_filter.h"
#include "point_cloud/statistical_outlier_removal.h"
#include "mesh/poisson_reconstruction.h"
#include "mesh/mesh_decimation.h"
#include "mesh/mesh_repair.h"
#include "mesh/mesh_smoothing.h"
#include "point_cloud/normal_estimation.h"
#include "export/stl_writer.h"
#include "export/obj_writer.h"
#include "export/ply_writer.h"

#define LOG_TAG "ScanForge_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace scanforge;

// Helper: deserialize flat float array to TriangleMesh
static TriangleMesh deserializeMesh(jfloat *data) {
    int vcount = static_cast<int>(data[0]);
    int tcount = static_cast<int>(data[1]);

    TriangleMesh mesh;
    int offset = 2;
    for (int i = 0; i < vcount; i++) {
        mesh.addVertex({data[offset], data[offset+1], data[offset+2]});
        offset += 3;
    }
    for (int i = 0; i < tcount; i++) {
        mesh.addTriangle(
            static_cast<int>(data[offset]),
            static_cast<int>(data[offset+1]),
            static_cast<int>(data[offset+2])
        );
        offset += 3;
    }
    return mesh;
}

// Helper: serialize TriangleMesh to flat float array
static jfloatArray serializeMesh(JNIEnv *env, const TriangleMesh& mesh) {
    size_t result_size = 2 + mesh.vertexCount() * 3 + mesh.triangleCount() * 3;
    std::vector<float> flat(result_size);
    flat[0] = static_cast<float>(mesh.vertexCount());
    flat[1] = static_cast<float>(mesh.triangleCount());

    size_t off = 2;
    for (size_t i = 0; i < mesh.vertexCount(); i++) {
        const auto& v = mesh.getVertex(i);
        flat[off++] = v.x; flat[off++] = v.y; flat[off++] = v.z;
    }
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        flat[off++] = static_cast<float>(t.a);
        flat[off++] = static_cast<float>(t.b);
        flat[off++] = static_cast<float>(t.c);
    }

    jfloatArray result = env->NewFloatArray(flat.size());
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());
    return result;
}

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_voxelGridFilter(
    JNIEnv *env, jobject thiz,
    jfloatArray points_flat, jfloat voxel_size) {

    jfloat *points = env->GetFloatArrayElements(points_flat, nullptr);
    jsize len = env->GetArrayLength(points_flat);
    int num_points = len / 3;

    LOGI("Voxel filter: %d points, voxel_size=%.4f", num_points, voxel_size);

    PointCloud cloud;
    cloud.reserve(num_points);
    for (int i = 0; i < num_points; i++) {
        cloud.addPoint({points[i*3], points[i*3+1], points[i*3+2]});
    }
    env->ReleaseFloatArrayElements(points_flat, points, 0);

    VoxelGridFilter filter(voxel_size);
    PointCloud filtered = filter.apply(cloud);

    LOGI("Voxel filter result: %d -> %zu points", num_points, filtered.size());

    jfloatArray result = env->NewFloatArray(filtered.size() * 3);
    std::vector<float> flat_result(filtered.size() * 3);
    for (size_t i = 0; i < filtered.size(); i++) {
        const auto& p = filtered.getPoint(i);
        flat_result[i*3] = p.x;
        flat_result[i*3+1] = p.y;
        flat_result[i*3+2] = p.z;
    }
    env->SetFloatArrayRegion(result, 0, flat_result.size(), flat_result.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_statisticalOutlierRemoval(
    JNIEnv *env, jobject thiz,
    jfloatArray points_flat, jint k_neighbors, jfloat std_ratio) {

    jfloat *points = env->GetFloatArrayElements(points_flat, nullptr);
    jsize len = env->GetArrayLength(points_flat);
    int num_points = len / 3;

    PointCloud cloud;
    cloud.reserve(num_points);
    for (int i = 0; i < num_points; i++) {
        cloud.addPoint({points[i*3], points[i*3+1], points[i*3+2]});
    }
    env->ReleaseFloatArrayElements(points_flat, points, 0);

    StatisticalOutlierRemoval sor(k_neighbors, std_ratio);
    PointCloud cleaned = sor.apply(cloud);

    LOGI("SOR: %d -> %zu points", num_points, cleaned.size());

    jfloatArray result = env->NewFloatArray(cleaned.size() * 3);
    std::vector<float> flat(cleaned.size() * 3);
    for (size_t i = 0; i < cleaned.size(); i++) {
        const auto& p = cleaned.getPoint(i);
        flat[i*3] = p.x; flat[i*3+1] = p.y; flat[i*3+2] = p.z;
    }
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_icpRegistration(
    JNIEnv *env, jobject thiz,
    jfloatArray source_flat, jfloatArray target_flat,
    jint max_iterations, jfloat tolerance) {

    jfloat *src = env->GetFloatArrayElements(source_flat, nullptr);
    jsize src_len = env->GetArrayLength(source_flat);
    PointCloud source;
    for (int i = 0; i < src_len / 3; i++) {
        source.addPoint({src[i*3], src[i*3+1], src[i*3+2]});
    }
    env->ReleaseFloatArrayElements(source_flat, src, 0);

    jfloat *tgt = env->GetFloatArrayElements(target_flat, nullptr);
    jsize tgt_len = env->GetArrayLength(target_flat);
    PointCloud target;
    for (int i = 0; i < tgt_len / 3; i++) {
        target.addPoint({tgt[i*3], tgt[i*3+1], tgt[i*3+2]});
    }
    env->ReleaseFloatArrayElements(target_flat, tgt, 0);

    ICPRegistration icp(max_iterations, tolerance);
    auto result_matrix = icp.align(source, target);

    LOGI("ICP converged: fitness=%.6f, rmse=%.6f",
         result_matrix.fitness, result_matrix.rmse);

    jfloatArray result = env->NewFloatArray(16);
    env->SetFloatArrayRegion(result, 0, 16, result_matrix.transformation.data());
    return result;
}

/**
 * PCA Normal Estimation: Computes surface normals for a point cloud
 *
 * Uses KD-tree k-NN search + PCA covariance eigendecomposition.
 * Normals are oriented consistently via BFS propagation.
 *
 * @param points_flat Float-Array [x0,y0,z0, x1,y1,z1, ...]
 * @param k_neighbors Number of neighbors for PCA (typical: 15)
 * @return Float-Array [x0,y0,z0,nx0,ny0,nz0, x1,y1,z1,nx1,ny1,nz1, ...]
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_estimateNormals(
    JNIEnv *env, jobject thiz,
    jfloatArray points_flat, jint k_neighbors) {

    jfloat *points = env->GetFloatArrayElements(points_flat, nullptr);
    jsize len = env->GetArrayLength(points_flat);
    int num_points = len / 3;

    LOGI("Normal estimation: %d points, k=%d", num_points, k_neighbors);

    PointCloud cloud;
    cloud.reserve(num_points);
    for (int i = 0; i < num_points; i++) {
        cloud.addPoint({points[i*3], points[i*3+1], points[i*3+2]});
    }
    env->ReleaseFloatArrayElements(points_flat, points, 0);

    NormalEstimation estimator(k_neighbors);
    std::vector<Vec3f> normals = estimator.estimate(cloud);

    // Pack result: [x,y,z,nx,ny,nz, ...]
    jfloatArray result = env->NewFloatArray(num_points * 6);
    std::vector<float> flat(num_points * 6);
    for (int i = 0; i < num_points; i++) {
        const auto& p = cloud.getPoint(i);
        flat[i*6]   = p.x;
        flat[i*6+1] = p.y;
        flat[i*6+2] = p.z;
        flat[i*6+3] = normals[i].x;
        flat[i*6+4] = normals[i].y;
        flat[i*6+5] = normals[i].z;
    }
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());

    LOGI("Normal estimation complete: %d normals", num_points);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_poissonReconstruction(
    JNIEnv *env, jobject thiz,
    jfloatArray points_with_normals, jint depth) {

    jfloat *data = env->GetFloatArrayElements(points_with_normals, nullptr);
    jsize len = env->GetArrayLength(points_with_normals);
    int num_points = len / 6;

    LOGI("Poisson reconstruction: %d points, depth=%d", num_points, depth);

    PointCloud cloud;
    std::vector<Vec3f> normals;
    cloud.reserve(num_points);
    normals.reserve(num_points);

    for (int i = 0; i < num_points; i++) {
        cloud.addPoint({data[i*6], data[i*6+1], data[i*6+2]});
        normals.push_back({data[i*6+3], data[i*6+4], data[i*6+5]});
    }
    env->ReleaseFloatArrayElements(points_with_normals, data, 0);

    PoissonReconstruction poisson(depth);
    TriangleMesh mesh = poisson.reconstruct(cloud, normals);

    LOGI("Poisson result: %zu vertices, %zu triangles",
         mesh.vertexCount(), mesh.triangleCount());

    return serializeMesh(env, mesh);
}

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_decimateMesh(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jfloat target_ratio) {

    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    int tcount = static_cast<int>(data[1]);
    TriangleMesh mesh = deserializeMesh(data);
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    int target_triangles = static_cast<int>(tcount * target_ratio);
    MeshDecimation decimator;
    TriangleMesh decimated = decimator.decimate(mesh, target_triangles);

    LOGI("Decimation: %d -> %zu triangles", tcount, decimated.triangleCount());

    return serializeMesh(env, decimated);
}

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_repairMesh(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data) {

    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    TriangleMesh mesh = deserializeMesh(data);
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    MeshRepair repair;
    repair.removeDegenerate(mesh);
    repair.removeDuplicateVertices(mesh);
    repair.makeManifold(mesh);
    repair.fillHoles(mesh);
    repair.orientNormals(mesh);

    LOGI("Repair: %zu vertices, %zu triangles, manifold=%s, watertight=%s",
         mesh.vertexCount(), mesh.triangleCount(),
         mesh.isManifold() ? "yes" : "no",
         mesh.isWatertight() ? "yes" : "no");

    return serializeMesh(env, mesh);
}

JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportSTL(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    TriangleMesh mesh = deserializeMesh(data);
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    STLWriter writer;
    bool success = writer.writeBinary(mesh, path);

    LOGI("STL export: %s (%zu triangles) -> %s",
         success ? "SUCCESS" : "FAILED", mesh.triangleCount(), path);

    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportOBJ(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    TriangleMesh mesh = deserializeMesh(data);
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    OBJWriter writer;
    bool success = writer.write(mesh, path);
    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportPLY(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    TriangleMesh mesh = deserializeMesh(data);
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    PLYWriter writer;
    bool success = writer.writeBinary(mesh, path);
    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
