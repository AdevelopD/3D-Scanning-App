#pragma once

#include <jni.h>

extern "C" {

// Point cloud processing
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_voxelGridFilter(
    JNIEnv *env, jobject thiz, jfloatArray points_flat, jfloat voxel_size);

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_statisticalOutlierRemoval(
    JNIEnv *env, jobject thiz, jfloatArray points_flat,
    jint k_neighbors, jfloat std_ratio);

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_icpRegistration(
    JNIEnv *env, jobject thiz, jfloatArray source_flat, jfloatArray target_flat,
    jint max_iterations, jfloat tolerance);

// Normal estimation
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_estimateNormals(
    JNIEnv *env, jobject thiz, jfloatArray points_flat, jint k_neighbors);

// Mesh reconstruction
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_poissonReconstruction(
    JNIEnv *env, jobject thiz, jfloatArray points_with_normals, jint depth);

// Mesh post-processing
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_decimateMesh(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data, jfloat target_ratio);

JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_repairMesh(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data);

// Export
JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportSTL(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data, jstring file_path);

JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportOBJ(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data, jstring file_path);

JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportPLY(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data, jstring file_path);

} // extern "C"
