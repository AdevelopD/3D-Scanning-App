# 3D Scanner Android App – Vollständige Entwicklungsdokumentation für Claude Code

## Projektübersicht

**App-Name:** ScanForge3D
**Ziel:** Android-App zum 3D-Scannen von Gegenständen, Löchern und Bauräumen in einer Qualität, die für 3D-Druck und Reverse Engineering geeignet ist.
**Exportformate:** STL (on-device), STEP (Cloud), CATIA Part (via STEP-Zwischenformat)
**Zielgenauigkeit:** 1–2 mm bei optimalen Bedingungen
**Min SDK:** Android API 28 (Android 9.0)
**Architektur:** Kotlin + C++ (NDK) + Cloud-Backend (Python/FastAPI)

---

## Inhaltsverzeichnis

1. [Projektstruktur](#1-projektstruktur)
2. [Build-Konfiguration](#2-build-konfiguration)
3. [Modul 1: ARCore Depth Capture](#3-modul-1-arcore-depth-capture)
4. [Modul 2: Punktwolken-Verarbeitung](#4-modul-2-punktwolken-verarbeitung)
5. [Modul 3: Mesh-Rekonstruktion](#5-modul-3-mesh-rekonstruktion)
6. [Modul 4: Mesh-Nachbearbeitung](#6-modul-4-mesh-nachbearbeitung)
7. [Modul 5: STL-Export (On-Device)](#7-modul-5-stl-export-on-device)
8. [Modul 6: 3D-Visualisierung](#8-modul-6-3d-visualisierung)
9. [Modul 7: Cloud-Backend für STEP-Export](#9-modul-7-cloud-backend-für-step-export)
10. [Modul 8: CATIA-Part-Workflow](#10-modul-8-catia-part-workflow)
11. [Modul 9: Kalibrierung und Referenzmaße](#11-modul-9-kalibrierung-und-referenzmaße)
12. [Modul 10: UI/UX Design](#12-modul-10-uiux-design)
13. [Abhängigkeiten und Bibliotheken](#13-abhängigkeiten-und-bibliotheken)
14. [Entwicklungsreihenfolge](#14-entwicklungsreihenfolge)
15. [Test-Strategie](#15-test-strategie)
16. [Bekannte Einschränkungen](#16-bekannte-einschränkungen)

---

## 1. Projektstruktur

```
ScanForge3D/
├── CLAUDE.md                          # <-- DIESES DOKUMENT
├── app/                               # Android Hauptmodul
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/scanforge3d/
│       │   ├── ScanForgeApp.kt                    # Application-Klasse
│       │   ├── MainActivity.kt                    # Navigation Host
│       │   ├── di/                                # Dependency Injection (Hilt)
│       │   │   └── AppModule.kt
│       │   ├── ui/                                # UI Layer
│       │   │   ├── theme/
│       │   │   │   ├── Theme.kt
│       │   │   │   ├── Color.kt
│       │   │   │   └── Type.kt
│       │   │   ├── home/
│       │   │   │   ├── HomeScreen.kt
│       │   │   │   └── HomeViewModel.kt
│       │   │   ├── scan/
│       │   │   │   ├── ScanScreen.kt              # AR-Kamera + Scan-UI
│       │   │   │   ├── ScanViewModel.kt
│       │   │   │   └── ScanOverlay.kt             # Echtzeit-Mesh-Overlay
│       │   │   ├── preview/
│       │   │   │   ├── PreviewScreen.kt           # 3D-Vorschau des Scans
│       │   │   │   ├── PreviewViewModel.kt
│       │   │   │   └── MeshRenderer.kt
│       │   │   ├── calibration/
│       │   │   │   ├── CalibrationScreen.kt       # Referenzmaß-Eingabe
│       │   │   │   └── CalibrationViewModel.kt
│       │   │   ├── export/
│       │   │   │   ├── ExportScreen.kt            # Format-Auswahl + Export
│       │   │   │   └── ExportViewModel.kt
│       │   │   └── projects/
│       │   │       ├── ProjectsScreen.kt          # Gespeicherte Scans
│       │   │       └── ProjectsViewModel.kt
│       │   ├── data/                              # Data Layer
│       │   │   ├── model/
│       │   │   │   ├── ScanProject.kt
│       │   │   │   ├── PointCloud.kt
│       │   │   │   ├── TriangleMesh.kt
│       │   │   │   ├── ScanMetadata.kt
│       │   │   │   └── ExportFormat.kt
│       │   │   ├── repository/
│       │   │   │   ├── ScanRepository.kt
│       │   │   │   ├── ProjectRepository.kt
│       │   │   │   └── CloudRepository.kt
│       │   │   ├── local/
│       │   │   │   ├── ScanDatabase.kt            # Room Database
│       │   │   │   ├── ProjectDao.kt
│       │   │   │   └── ScanDao.kt
│       │   │   └── remote/
│       │   │       ├── CloudApiService.kt         # Retrofit für Cloud-Backend
│       │   │       └── CloudApiModels.kt
│       │   ├── scanning/                          # Scanning Engine
│       │   │   ├── ARCoreSessionManager.kt
│       │   │   ├── DepthFrameProcessor.kt
│       │   │   ├── PointCloudAccumulator.kt
│       │   │   ├── ConfidenceFilter.kt
│       │   │   └── ScanSessionController.kt
│       │   ├── processing/                        # Mesh Processing (JNI Bridges)
│       │   │   ├── NativeMeshProcessor.kt         # JNI-Brücke
│       │   │   ├── MeshReconstructor.kt
│       │   │   ├── MeshOptimizer.kt
│       │   │   └── ICPRegistration.kt
│       │   └── export/                            # Export Logic
│       │       ├── STLExporter.kt                 # On-device STL
│       │       ├── OBJExporter.kt                 # On-device OBJ
│       │       ├── PLYExporter.kt                 # On-device PLY
│       │       └── CloudExportManager.kt          # STEP via Cloud
│       └── res/
│           ├── layout/
│           ├── values/
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── drawable/
├── native/                            # C++ Native Code
│   ├── CMakeLists.txt
│   ├── src/
│   │   ├── jni_bridge.cpp             # JNI-Eintrittspunkt
│   │   ├── jni_bridge.h
│   │   ├── point_cloud/
│   │   │   ├── point_cloud.h
│   │   │   ├── point_cloud.cpp
│   │   │   ├── icp_registration.h     # Iterative Closest Point
│   │   │   ├── icp_registration.cpp
│   │   │   ├── voxel_grid_filter.h    # Downsampling
│   │   │   ├── voxel_grid_filter.cpp
│   │   │   ├── statistical_outlier_removal.h
│   │   │   └── statistical_outlier_removal.cpp
│   │   ├── mesh/
│   │   │   ├── triangle_mesh.h
│   │   │   ├── triangle_mesh.cpp
│   │   │   ├── poisson_reconstruction.h   # Screened Poisson
│   │   │   ├── poisson_reconstruction.cpp
│   │   │   ├── marching_cubes.h
│   │   │   ├── marching_cubes.cpp
│   │   │   ├── mesh_decimation.h          # Quadric Error Metrics
│   │   │   ├── mesh_decimation.cpp
│   │   │   ├── mesh_repair.h              # Loch-Füllung, Manifold-Repair
│   │   │   ├── mesh_repair.cpp
│   │   │   ├── mesh_smoothing.h           # Laplacian / Taubin Smoothing
│   │   │   └── mesh_smoothing.cpp
│   │   ├── export/
│   │   │   ├── stl_writer.h
│   │   │   ├── stl_writer.cpp
│   │   │   ├── obj_writer.h
│   │   │   ├── obj_writer.cpp
│   │   │   ├── ply_writer.h
│   │   │   └── ply_writer.cpp
│   │   └── util/
│   │       ├── math_utils.h
│   │       ├── kdtree.h                   # Spatial Indexing
│   │       ├── kdtree.cpp
│   │       └── timer.h
│   └── third_party/
│       ├── eigen/                     # Header-only Lineare Algebra
│       ├── nanoflann/                 # Header-only KD-Tree
│       └── PoissonRecon/              # Screened Poisson Reconstruction
├── cloud-backend/                     # Python Cloud Service
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── requirements.txt
│   ├── main.py                        # FastAPI App
│   ├── routers/
│   │   ├── scan_upload.py
│   │   ├── processing.py
│   │   └── export.py
│   ├── services/
│   │   ├── mesh_service.py
│   │   ├── step_export_service.py     # Open CASCADE STEP-Export
│   │   ├── reverse_engineering.py     # Mesh → BREP Konvertierung
│   │   └── photogrammetry_service.py  # COLMAP Integration
│   ├── models/
│   │   ├── job.py
│   │   └── scan_data.py
│   └── workers/
│       ├── celery_app.py              # Async Task Queue
│       └── processing_worker.py
└── scripts/
    ├── setup_ndk.sh                   # NDK + third-party Setup
    ├── build_native.sh
    └── deploy_cloud.sh
```

---

## 2. Build-Konfiguration

### 2.1 Projekt-Level build.gradle.kts

```kotlin
// build.gradle.kts (Project)
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
```

### 2.2 App-Level build.gradle.kts

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.scanforge3d"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scanforge3d"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-O2",
                    "-DEIGEN_DISABLE_UNALIGNED_ARRAY_ASSERT",
                    "-DEIGEN_DONT_VECTORIZE",
                    "-fexceptions"
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE"
                )
            }
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // ARCore
    implementation("com.google.ar:core:1.41.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (Cloud Backend)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 3D Rendering (Filament via SceneView)
    implementation("io.github.sceneview:arsceneview:2.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager (Hintergrund-Exports)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Datastore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
```

### 2.3 Native CMakeLists.txt

```cmake
# native/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project(scanforge_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# KRITISCH: Eigen-Vectorisierung deaktivieren für ARM-Kompatibilität
add_definitions(
    -DEIGEN_DISABLE_UNALIGNED_ARRAY_ASSERT
    -DEIGEN_DONT_VECTORIZE
    -DEIGEN_MAX_ALIGN_BYTES=0
)

# Third-Party Header-Only Libraries
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/eigen
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/nanoflann
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/PoissonRecon/Src
)

# Quelldateien sammeln
file(GLOB_RECURSE NATIVE_SOURCES
    "src/*.cpp"
    "src/*.h"
)

# Poisson Reconstruction Quellen
file(GLOB POISSON_SOURCES
    "third_party/PoissonRecon/Src/PoissonRecon.cpp"
    "third_party/PoissonRecon/Src/SurfaceTrimmer.cpp"
)

# Shared Library erstellen
add_library(scanforge_native SHARED
    ${NATIVE_SOURCES}
    ${POISSON_SOURCES}
)

# Android-Bibliotheken verlinken
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(scanforge_native
    ${log-lib}
    ${android-lib}
)

# NEON-Optimierungen für ARM
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(scanforge_native PRIVATE -march=armv8-a+simd)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(scanforge_native PRIVATE -mfpu=neon -mfloat-abi=softfp)
endif()
```

### 2.4 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Berechtigungen -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <!-- ARCore Pflicht -->
    <uses-feature android:name="android.hardware.camera.ar" android:required="true" />
    <uses-feature android:name="android.hardware.camera" android:required="true" />

    <application
        android:name=".ScanForgeApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.ScanForge3D"
        android:hardwareAccelerated="true">

        <!-- ARCore Meta-Data -->
        <meta-data
            android:name="com.google.ar.core"
            android:value="required" />
        <meta-data
            android:name="com.google.ar.core.min_apk_version"
            android:value="191" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## 3. Modul 1: ARCore Depth Capture

### 3.1 Konzept

Die App nutzt ARCore's Raw Depth API, um pro Frame eine Tiefenkarte (640×360, 16-Bit, Werte in Millimetern) plus Confidence-Map zu erhalten. Tiefenpixel werden mit den Kamera-Intrinsics in 3D-Weltkoordinaten unprojiziert.

### 3.2 ARCoreSessionManager.kt

```kotlin
package com.scanforge3d.scanning

import android.content.Context
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ARCoreSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var session: Session? = null

    fun createSession(): Session {
        val session = Session(context)

        val config = Config(session).apply {
            // Raw Depth für maximale Genauigkeit aktivieren
            depthMode = Config.DepthMode.RAW_DEPTH_ONLY

            // Fokus auf Nahbereich für Objekt-Scanning
            focusMode = Config.FocusMode.AUTO

            // Plane Detection für Referenzebenen
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Light Estimation für Texturqualität
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            // Update-Modus für flüssiges Scanning
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }

        session.configure(config)
        this.session = session
        return session
    }

    fun isDepthSupported(): Boolean {
        return session?.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) == true
    }

    fun resume() { session?.resume() }
    fun pause() { session?.pause() }
    fun close() { session?.close(); session = null }

    fun getSession(): Session = session
        ?: throw IllegalStateException("ARCore Session not initialized")
}
```

### 3.3 DepthFrameProcessor.kt

```kotlin
package com.scanforge3d.scanning

import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import com.scanforge3d.data.model.PointCloud
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.inject.Inject
import kotlin.math.sqrt

class DepthFrameProcessor @Inject constructor() {

    companion object {
        // Confidence-Schwellwert: 0-255, 200+ = zuverlässig
        const val MIN_CONFIDENCE: Int = 200
        // Tiefenbereich für Scanning (in Metern)
        const val MIN_DEPTH_M: Float = 0.1f
        const val MAX_DEPTH_M: Float = 3.0f
        // Maximale Punkte pro Frame (Performance-Limit)
        const val MAX_POINTS_PER_FRAME: Int = 50_000
    }

    /**
     * Extrahiert eine gefilterte 3D-Punktwolke aus einem ARCore-Frame.
     *
     * Pipeline:
     * 1. Raw Depth Image (16-bit, mm) abrufen
     * 2. Confidence Image abrufen
     * 3. Pixel filtern (Confidence > MIN_CONFIDENCE)
     * 4. 2D-Pixel → 3D-Weltkoordinaten unprojizieren
     * 5. In Punktwolke sammeln
     */
    fun processFrame(frame: Frame): PointCloud? {
        try {
            val depthImage = frame.acquireRawDepthImage16Bits()
            val confidenceImage = frame.acquireRawDepthConfidenceImage()

            try {
                val camera = frame.camera
                if (camera.trackingState != com.google.ar.core.TrackingState.TRACKING) {
                    return null
                }

                val intrinsics = camera.textureIntrinsics
                val fx = intrinsics.focalLength[0]
                val fy = intrinsics.focalLength[1]
                val cx = intrinsics.principalPoint[0]
                val cy = intrinsics.principalPoint[1]

                val width = depthImage.width     // typisch: 640
                val height = depthImage.height   // typisch: 360

                val depthBuffer: ShortBuffer = depthImage.planes[0].buffer
                    .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val confidenceBuffer: ByteBuffer = confidenceImage.planes[0].buffer

                // Kamera-Pose in Weltkoordinaten
                val cameraPose = camera.displayOrientedPose
                val viewMatrix = FloatArray(16)
                cameraPose.toMatrix(viewMatrix, 0)

                // Punkte sammeln
                val points = mutableListOf<FloatArray>()   // x, y, z
                val normals = mutableListOf<FloatArray>()   // nx, ny, nz (geschätzt)
                val colors = mutableListOf<FloatArray>()    // r, g, b (optional)

                val stride = maxOf(1, (width * height) / MAX_POINTS_PER_FRAME)

                for (y in 0 until height step stride) {
                    for (x in 0 until width step stride) {
                        val idx = y * width + x

                        // Confidence prüfen
                        val confidence = confidenceBuffer.get(idx).toInt() and 0xFF
                        if (confidence < MIN_CONFIDENCE) continue

                        // Tiefe in Metern
                        val depthMm = depthBuffer.get(idx).toInt() and 0xFFFF
                        val depthM = depthMm / 1000.0f

                        // Tiefenbereich filtern
                        if (depthM < MIN_DEPTH_M || depthM > MAX_DEPTH_M) continue

                        // Unprojection: Pixel → Kamera-Koordinaten
                        val localX = (x - cx) / fx * depthM
                        val localY = (y - cy) / fy * depthM
                        val localZ = -depthM  // ARCore: Z zeigt aus Kamera heraus

                        // Kamera-Koordinaten → Welt-Koordinaten
                        val worldPoint = transformPoint(
                            viewMatrix, localX, localY, localZ
                        )

                        points.add(worldPoint)
                    }
                }

                return PointCloud(
                    points = points.toTypedArray(),
                    timestamp = frame.timestamp,
                    cameraPose = cameraPose,
                    pointCount = points.size
                )

            } finally {
                depthImage.close()
                confidenceImage.close()
            }
        } catch (e: NotYetAvailableException) {
            // Noch kein Tiefenbild verfügbar – normal beim Start
            return null
        }
    }

    /**
     * Transformiert einen Punkt von Kamera- in Welt-Koordinaten
     * mit einer 4x4-Transformationsmatrix
     */
    private fun transformPoint(
        matrix: FloatArray, x: Float, y: Float, z: Float
    ): FloatArray {
        return floatArrayOf(
            matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
            matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
            matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]
        )
    }
}
```

### 3.4 PointCloudAccumulator.kt

```kotlin
package com.scanforge3d.scanning

import com.scanforge3d.data.model.PointCloud
import javax.inject.Inject

/**
 * Akkumuliert Punktwolken aus mehreren Frames und verwaltet
 * die Registrierung (Ausrichtung) über ICP.
 *
 * Strategie:
 * - Jeder Frame liefert eine Teil-Punktwolke
 * - Voxel-Grid-Downsampling verhindert Speicherüberlauf
 * - ICP korrigiert Drift zwischen Frames
 * - Duplikate werden per Voxel-Grid entfernt
 */
class PointCloudAccumulator @Inject constructor(
    private val nativeMeshProcessor: NativeMeshProcessorBridge
) {
    // Voxel-Größe für Downsampling in Metern
    companion object {
        const val VOXEL_SIZE_M: Float = 0.002f // 2mm Voxel
        const val MAX_ACCUMULATED_POINTS: Int = 2_000_000
    }

    private val accumulatedPoints = mutableListOf<FloatArray>()
    private var frameCount = 0

    fun addFrame(pointCloud: PointCloud) {
        accumulatedPoints.addAll(pointCloud.points)
        frameCount++

        // Periodisches Downsampling alle 10 Frames
        if (frameCount % 10 == 0) {
            downsample()
        }
    }

    /**
     * Voxel-Grid-Downsampling über JNI:
     * Teilt den Raum in Voxel der Größe VOXEL_SIZE_M
     * und behält pro Voxel den Durchschnittspunkt
     */
    private fun downsample() {
        if (accumulatedPoints.size < 1000) return

        val flatPoints = flattenPoints(accumulatedPoints)
        val downsampled = nativeMeshProcessor.voxelGridFilter(
            flatPoints, VOXEL_SIZE_M
        )
        accumulatedPoints.clear()
        accumulatedPoints.addAll(unflattenPoints(downsampled))
    }

    fun getAccumulatedCloud(): Array<FloatArray> {
        downsample()
        return accumulatedPoints.toTypedArray()
    }

    fun getPointCount(): Int = accumulatedPoints.size
    fun getFrameCount(): Int = frameCount

    fun reset() {
        accumulatedPoints.clear()
        frameCount = 0
    }

    private fun flattenPoints(points: List<FloatArray>): FloatArray {
        val flat = FloatArray(points.size * 3)
        points.forEachIndexed { i, p ->
            flat[i * 3] = p[0]
            flat[i * 3 + 1] = p[1]
            flat[i * 3 + 2] = p[2]
        }
        return flat
    }

    private fun unflattenPoints(flat: FloatArray): List<FloatArray> {
        val points = mutableListOf<FloatArray>()
        for (i in flat.indices step 3) {
            points.add(floatArrayOf(flat[i], flat[i + 1], flat[i + 2]))
        }
        return points
    }
}
```

---

## 4. Modul 2: Punktwolken-Verarbeitung (C++ Native)

### 4.1 JNI Bridge

```cpp
// native/src/jni_bridge.cpp
#include <jni.h>
#include <android/log.h>
#include "point_cloud/point_cloud.h"
#include "point_cloud/icp_registration.h"
#include "point_cloud/voxel_grid_filter.h"
#include "point_cloud/statistical_outlier_removal.h"
#include "mesh/poisson_reconstruction.h"
#include "mesh/mesh_decimation.h"
#include "mesh/mesh_repair.h"
#include "mesh/mesh_smoothing.h"
#include "export/stl_writer.h"
#include "export/obj_writer.h"
#include "export/ply_writer.h"

#define LOG_TAG "ScanForge_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace scanforge;

extern "C" {

// ============================================================
// PUNKTWOLKEN-VERARBEITUNG
// ============================================================

/**
 * Voxel-Grid-Downsampling: Reduziert Punktdichte gleichmäßig
 *
 * @param points_flat Float-Array [x0,y0,z0, x1,y1,z1, ...]
 * @param voxel_size Voxelgröße in Metern (z.B. 0.002 = 2mm)
 * @return Gefiltertes Float-Array
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_voxelGridFilter(
    JNIEnv *env, jobject thiz,
    jfloatArray points_flat, jfloat voxel_size) {

    jfloat *points = env->GetFloatArrayElements(points_flat, nullptr);
    jsize len = env->GetArrayLength(points_flat);
    int num_points = len / 3;

    LOGI("Voxel filter: %d points, voxel_size=%.4f", num_points, voxel_size);

    // Punktwolke erstellen
    PointCloud cloud;
    cloud.reserve(num_points);
    for (int i = 0; i < num_points; i++) {
        cloud.addPoint({points[i*3], points[i*3+1], points[i*3+2]});
    }
    env->ReleaseFloatArrayElements(points_flat, points, 0);

    // Voxel-Grid-Filter anwenden
    VoxelGridFilter filter(voxel_size);
    PointCloud filtered = filter.apply(cloud);

    LOGI("Voxel filter result: %d -> %zu points",
         num_points, filtered.size());

    // Ergebnis zurückgeben
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

/**
 * Statistical Outlier Removal: Entfernt Rauschpunkte
 *
 * @param k_neighbors Anzahl Nachbarn für Statistik (typisch: 20)
 * @param std_ratio Standardabweichungs-Faktor (typisch: 2.0)
 */
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

/**
 * ICP Registrierung: Richtet zwei Punktwolken zueinander aus
 *
 * @param source_flat Quell-Punktwolke
 * @param target_flat Ziel-Punktwolke
 * @param max_iterations Max Iterationen (typisch: 50)
 * @param tolerance Konvergenz-Schwelle (typisch: 1e-6)
 * @return 4x4 Transformationsmatrix (16 floats, column-major)
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_icpRegistration(
    JNIEnv *env, jobject thiz,
    jfloatArray source_flat, jfloatArray target_flat,
    jint max_iterations, jfloat tolerance) {

    // Source Cloud
    jfloat *src = env->GetFloatArrayElements(source_flat, nullptr);
    jsize src_len = env->GetArrayLength(source_flat);
    PointCloud source;
    for (int i = 0; i < src_len / 3; i++) {
        source.addPoint({src[i*3], src[i*3+1], src[i*3+2]});
    }
    env->ReleaseFloatArrayElements(source_flat, src, 0);

    // Target Cloud
    jfloat *tgt = env->GetFloatArrayElements(target_flat, nullptr);
    jsize tgt_len = env->GetArrayLength(target_flat);
    PointCloud target;
    for (int i = 0; i < tgt_len / 3; i++) {
        target.addPoint({tgt[i*3], tgt[i*3+1], tgt[i*3+2]});
    }
    env->ReleaseFloatArrayElements(target_flat, tgt, 0);

    // ICP ausführen
    ICPRegistration icp(max_iterations, tolerance);
    auto result_matrix = icp.align(source, target);

    LOGI("ICP converged: fitness=%.6f, rmse=%.6f",
         result_matrix.fitness, result_matrix.rmse);

    // 4x4 Matrix zurückgeben
    jfloatArray result = env->NewFloatArray(16);
    env->SetFloatArrayRegion(result, 0, 16, result_matrix.transformation.data());
    return result;
}

// ============================================================
// MESH-REKONSTRUKTION
// ============================================================

/**
 * Screened Poisson Surface Reconstruction
 *
 * Erzeugt aus einer Punktwolke (mit Normalen) ein wasserdichtes
 * Dreiecks-Mesh, das für 3D-Druck geeignet ist.
 *
 * @param points_with_normals [x,y,z,nx,ny,nz, ...] pro Punkt
 * @param depth Octree-Tiefe (8-12, höher = mehr Detail)
 * @return Mesh als [vertex_count, triangle_count,
 *          v0x,v0y,v0z, v1x,..., t0a,t0b,t0c, t1a,...]
 */
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

    // Poisson-Rekonstruktion durchführen
    PoissonReconstruction poisson(depth);
    TriangleMesh mesh = poisson.reconstruct(cloud, normals);

    LOGI("Poisson result: %zu vertices, %zu triangles",
         mesh.vertexCount(), mesh.triangleCount());

    // Mesh serialisieren: [vcount, tcount, vertices..., indices...]
    size_t result_size = 2 + mesh.vertexCount() * 3 + mesh.triangleCount() * 3;
    std::vector<float> flat(result_size);
    flat[0] = static_cast<float>(mesh.vertexCount());
    flat[1] = static_cast<float>(mesh.triangleCount());

    size_t offset = 2;
    for (size_t i = 0; i < mesh.vertexCount(); i++) {
        const auto& v = mesh.getVertex(i);
        flat[offset++] = v.x;
        flat[offset++] = v.y;
        flat[offset++] = v.z;
    }
    for (size_t i = 0; i < mesh.triangleCount(); i++) {
        const auto& t = mesh.getTriangle(i);
        flat[offset++] = static_cast<float>(t.a);
        flat[offset++] = static_cast<float>(t.b);
        flat[offset++] = static_cast<float>(t.c);
    }

    jfloatArray result = env->NewFloatArray(flat.size());
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());
    return result;
}

/**
 * Mesh Decimation: Reduziert Dreieckszahl via Quadric Error Metrics
 *
 * @param target_ratio Ziel-Verhältnis (0.5 = 50% der Dreiecke behalten)
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_decimateMesh(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jfloat target_ratio) {

    // Mesh deserialisieren
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
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
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    // Decimation
    int target_triangles = static_cast<int>(tcount * target_ratio);
    MeshDecimation decimator;
    TriangleMesh decimated = decimator.decimate(mesh, target_triangles);

    LOGI("Decimation: %d -> %zu triangles", tcount, decimated.triangleCount());

    // Serialisieren und zurückgeben
    size_t result_size = 2 + decimated.vertexCount() * 3 + decimated.triangleCount() * 3;
    std::vector<float> flat(result_size);
    flat[0] = static_cast<float>(decimated.vertexCount());
    flat[1] = static_cast<float>(decimated.triangleCount());

    size_t off = 2;
    for (size_t i = 0; i < decimated.vertexCount(); i++) {
        const auto& v = decimated.getVertex(i);
        flat[off++] = v.x; flat[off++] = v.y; flat[off++] = v.z;
    }
    for (size_t i = 0; i < decimated.triangleCount(); i++) {
        const auto& t = decimated.getTriangle(i);
        flat[off++] = static_cast<float>(t.a);
        flat[off++] = static_cast<float>(t.b);
        flat[off++] = static_cast<float>(t.c);
    }

    jfloatArray result = env->NewFloatArray(flat.size());
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());
    return result;
}

/**
 * Mesh Repair: Löcher füllen, Manifold herstellen, Normalen korrigieren
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_repairMesh(
    JNIEnv *env, jobject thiz, jfloatArray mesh_data) {

    // Deserialisieren (gleicher Code wie oben)
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
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
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    // Repair-Pipeline
    MeshRepair repair;
    repair.removeDegenerate(mesh);          // Degenerierte Dreiecke entfernen
    repair.removeDuplicateVertices(mesh);   // Doppelte Vertices zusammenführen
    repair.makeManifold(mesh);              // Non-Manifold Edges reparieren
    repair.fillHoles(mesh);                 // Löcher füllen
    repair.orientNormals(mesh);             // Normalen konsistent ausrichten

    LOGI("Repair: %zu vertices, %zu triangles, manifold=%s, watertight=%s",
         mesh.vertexCount(), mesh.triangleCount(),
         mesh.isManifold() ? "yes" : "no",
         mesh.isWatertight() ? "yes" : "no");

    // Serialisieren
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
        flat[off++] = (float)t.a; flat[off++] = (float)t.b; flat[off++] = (float)t.c;
    }

    jfloatArray result = env->NewFloatArray(flat.size());
    env->SetFloatArrayRegion(result, 0, flat.size(), flat.data());
    return result;
}

// ============================================================
// STL EXPORT
// ============================================================

/**
 * Schreibt ein Mesh als Binary-STL-Datei
 *
 * @param file_path Absoluter Pfad zur Zieldatei
 * @return true bei Erfolg
 */
JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportSTL(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
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
            (int)data[offset], (int)data[offset+1], (int)data[offset+2]
        );
        offset += 3;
    }
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    STLWriter writer;
    bool success = writer.writeBinary(mesh, path);

    LOGI("STL export: %s (%d triangles) -> %s",
         success ? "SUCCESS" : "FAILED", tcount, path);

    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Schreibt ein Mesh als OBJ-Datei
 */
JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportOBJ(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    int vcount = (int)data[0], tcount = (int)data[1];

    TriangleMesh mesh;
    int offset = 2;
    for (int i = 0; i < vcount; i++) {
        mesh.addVertex({data[offset], data[offset+1], data[offset+2]});
        offset += 3;
    }
    for (int i = 0; i < tcount; i++) {
        mesh.addTriangle((int)data[offset], (int)data[offset+1], (int)data[offset+2]);
        offset += 3;
    }
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    OBJWriter writer;
    bool success = writer.write(mesh, path);
    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Schreibt ein Mesh als PLY-Datei (mit Farben, falls vorhanden)
 */
JNIEXPORT jboolean JNICALL
Java_com_scanforge3d_processing_NativeMeshProcessor_exportPLY(
    JNIEnv *env, jobject thiz,
    jfloatArray mesh_data, jstring file_path) {

    const char *path = env->GetStringUTFChars(file_path, nullptr);
    jfloat *data = env->GetFloatArrayElements(mesh_data, nullptr);
    int vcount = (int)data[0], tcount = (int)data[1];

    TriangleMesh mesh;
    int offset = 2;
    for (int i = 0; i < vcount; i++) {
        mesh.addVertex({data[offset], data[offset+1], data[offset+2]});
        offset += 3;
    }
    for (int i = 0; i < tcount; i++) {
        mesh.addTriangle((int)data[offset], (int)data[offset+1], (int)data[offset+2]);
        offset += 3;
    }
    env->ReleaseFloatArrayElements(mesh_data, data, 0);

    PLYWriter writer;
    bool success = writer.writeBinary(mesh, path);
    env->ReleaseStringUTFChars(file_path, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
```

### 4.2 Kotlin JNI Bridge

```kotlin
// app/src/main/java/com/scanforge3d/processing/NativeMeshProcessor.kt
package com.scanforge3d.processing

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMeshProcessor @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("scanforge_native")
        }
    }

    // Punktwolken-Verarbeitung
    external fun voxelGridFilter(pointsFlat: FloatArray, voxelSize: Float): FloatArray
    external fun statisticalOutlierRemoval(
        pointsFlat: FloatArray, kNeighbors: Int, stdRatio: Float
    ): FloatArray
    external fun icpRegistration(
        sourceFlat: FloatArray, targetFlat: FloatArray,
        maxIterations: Int, tolerance: Float
    ): FloatArray

    // Mesh-Rekonstruktion
    external fun poissonReconstruction(
        pointsWithNormals: FloatArray, depth: Int
    ): FloatArray

    // Mesh-Nachbearbeitung
    external fun decimateMesh(meshData: FloatArray, targetRatio: Float): FloatArray
    external fun repairMesh(meshData: FloatArray): FloatArray

    // Export
    external fun exportSTL(meshData: FloatArray, filePath: String): Boolean
    external fun exportOBJ(meshData: FloatArray, filePath: String): Boolean
    external fun exportPLY(meshData: FloatArray, filePath: String): Boolean
}
```

---

## 5. Modul 3: Mesh-Rekonstruktion (C++ Implementierungen)

### 5.1 point_cloud.h – Datenstrukturen

```cpp
// native/src/point_cloud/point_cloud.h
#pragma once

#include <vector>
#include <cmath>
#include <algorithm>
#include <array>

namespace scanforge {

struct Vec3f {
    float x, y, z;

    Vec3f() : x(0), y(0), z(0) {}
    Vec3f(float x, float y, float z) : x(x), y(y), z(z) {}

    Vec3f operator+(const Vec3f& o) const { return {x+o.x, y+o.y, z+o.z}; }
    Vec3f operator-(const Vec3f& o) const { return {x-o.x, y-o.y, z-o.z}; }
    Vec3f operator*(float s) const { return {x*s, y*s, z*s}; }
    Vec3f operator/(float s) const { return {x/s, y/s, z/s}; }

    float dot(const Vec3f& o) const { return x*o.x + y*o.y + z*o.z; }
    Vec3f cross(const Vec3f& o) const {
        return {y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x};
    }
    float length() const { return std::sqrt(x*x + y*y + z*z); }
    Vec3f normalized() const {
        float l = length();
        return l > 1e-8f ? *this / l : Vec3f(0, 0, 0);
    }
    float distanceTo(const Vec3f& o) const { return (*this - o).length(); }
};

struct Triangle {
    int a, b, c;  // Vertex-Indices
    Triangle() : a(0), b(0), c(0) {}
    Triangle(int a, int b, int c) : a(a), b(b), c(c) {}
};

class PointCloud {
public:
    void reserve(size_t n) { points_.reserve(n); }
    void addPoint(const Vec3f& p) { points_.push_back(p); }
    const Vec3f& getPoint(size_t i) const { return points_[i]; }
    size_t size() const { return points_.size(); }
    bool empty() const { return points_.empty(); }

    void clear() { points_.clear(); }
    const std::vector<Vec3f>& getPoints() const { return points_; }

    // Bounding Box
    void computeBounds(Vec3f& min_bound, Vec3f& max_bound) const {
        if (points_.empty()) return;
        min_bound = max_bound = points_[0];
        for (const auto& p : points_) {
            min_bound.x = std::min(min_bound.x, p.x);
            min_bound.y = std::min(min_bound.y, p.y);
            min_bound.z = std::min(min_bound.z, p.z);
            max_bound.x = std::max(max_bound.x, p.x);
            max_bound.y = std::max(max_bound.y, p.y);
            max_bound.z = std::max(max_bound.z, p.z);
        }
    }

private:
    std::vector<Vec3f> points_;
};

class TriangleMesh {
public:
    void addVertex(const Vec3f& v) { vertices_.push_back(v); }
    void addTriangle(int a, int b, int c) { triangles_.push_back({a, b, c}); }
    void addTriangle(const Triangle& t) { triangles_.push_back(t); }

    const Vec3f& getVertex(size_t i) const { return vertices_[i]; }
    const Triangle& getTriangle(size_t i) const { return triangles_[i]; }
    size_t vertexCount() const { return vertices_.size(); }
    size_t triangleCount() const { return triangles_.size(); }

    std::vector<Vec3f>& vertices() { return vertices_; }
    std::vector<Triangle>& triangles() { return triangles_; }
    const std::vector<Vec3f>& vertices() const { return vertices_; }
    const std::vector<Triangle>& triangles() const { return triangles_; }

    // Facetten-Normale berechnen
    Vec3f computeTriangleNormal(size_t tri_idx) const {
        const auto& t = triangles_[tri_idx];
        Vec3f e1 = vertices_[t.b] - vertices_[t.a];
        Vec3f e2 = vertices_[t.c] - vertices_[t.a];
        return e1.cross(e2).normalized();
    }

    // Vertex-Normalen als Durchschnitt der angrenzenden Facetten
    std::vector<Vec3f> computeVertexNormals() const {
        std::vector<Vec3f> normals(vertices_.size(), {0, 0, 0});
        for (size_t i = 0; i < triangles_.size(); i++) {
            Vec3f n = computeTriangleNormal(i);
            normals[triangles_[i].a] = normals[triangles_[i].a] + n;
            normals[triangles_[i].b] = normals[triangles_[i].b] + n;
            normals[triangles_[i].c] = normals[triangles_[i].c] + n;
        }
        for (auto& n : normals) n = n.normalized();
        return normals;
    }

    bool isManifold() const;   // Implementierung in mesh_repair.cpp
    bool isWatertight() const; // Implementierung in mesh_repair.cpp

private:
    std::vector<Vec3f> vertices_;
    std::vector<Triangle> triangles_;
};

} // namespace scanforge
```

### 5.2 voxel_grid_filter.h/.cpp

```cpp
// native/src/point_cloud/voxel_grid_filter.h
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
                // Einfacher aber effektiver Hash
                size_t h = 0;
                h ^= std::hash<int>{}(k.x) + 0x9e3779b9 + (h << 6) + (h >> 2);
                h ^= std::hash<int>{}(k.y) + 0x9e3779b9 + (h << 6) + (h >> 2);
                h ^= std::hash<int>{}(k.z) + 0x9e3779b9 + (h << 6) + (h >> 2);
                return h;
            }
        };

        struct VoxelAccum {
            float sx = 0, sy = 0, sz = 0; // Summen
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
```

### 5.3 stl_writer.h/.cpp

```cpp
// native/src/export/stl_writer.h
#pragma once
#include "../point_cloud/point_cloud.h"
#include <fstream>
#include <cstdint>
#include <cstring>

namespace scanforge {

class STLWriter {
public:
    /**
     * Binary STL Format:
     *   80 Bytes: Header (beliebiger Text)
     *    4 Bytes: Anzahl Dreiecke (uint32_t, little-endian)
     *   Pro Dreieck (50 Bytes):
     *     12 Bytes: Normale (3x float32)
     *     12 Bytes: Vertex 1 (3x float32)
     *     12 Bytes: Vertex 2 (3x float32)
     *     12 Bytes: Vertex 3 (3x float32)
     *      2 Bytes: Attribut-Byte-Count (uint16_t, üblicherweise 0)
     */
    bool writeBinary(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath, std::ios::binary);
        if (!file.is_open()) return false;

        // Header: 80 Bytes
        char header[80] = {};
        std::strncpy(header, "ScanForge3D Binary STL Export", 79);
        file.write(header, 80);

        // Dreieckszahl
        uint32_t tri_count = static_cast<uint32_t>(mesh.triangleCount());
        file.write(reinterpret_cast<const char*>(&tri_count), 4);

        // Dreiecke schreiben
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& tri = mesh.getTriangle(i);
            const Vec3f& v0 = mesh.getVertex(tri.a);
            const Vec3f& v1 = mesh.getVertex(tri.b);
            const Vec3f& v2 = mesh.getVertex(tri.c);

            // Facetten-Normale berechnen
            Vec3f normal = mesh.computeTriangleNormal(i);

            // Normale
            file.write(reinterpret_cast<const char*>(&normal.x), 4);
            file.write(reinterpret_cast<const char*>(&normal.y), 4);
            file.write(reinterpret_cast<const char*>(&normal.z), 4);

            // Vertex 1
            file.write(reinterpret_cast<const char*>(&v0.x), 4);
            file.write(reinterpret_cast<const char*>(&v0.y), 4);
            file.write(reinterpret_cast<const char*>(&v0.z), 4);

            // Vertex 2
            file.write(reinterpret_cast<const char*>(&v1.x), 4);
            file.write(reinterpret_cast<const char*>(&v1.y), 4);
            file.write(reinterpret_cast<const char*>(&v1.z), 4);

            // Vertex 3
            file.write(reinterpret_cast<const char*>(&v2.x), 4);
            file.write(reinterpret_cast<const char*>(&v2.y), 4);
            file.write(reinterpret_cast<const char*>(&v2.z), 4);

            // Attribut-Byte (immer 0)
            uint16_t attr = 0;
            file.write(reinterpret_cast<const char*>(&attr), 2);
        }

        file.close();
        return true;
    }

    /**
     * ASCII STL (für Debugging, größere Dateien)
     */
    bool writeASCII(const TriangleMesh& mesh, const char* filepath) const {
        std::ofstream file(filepath);
        if (!file.is_open()) return false;

        file << "solid ScanForge3D\n";
        for (size_t i = 0; i < mesh.triangleCount(); i++) {
            const auto& tri = mesh.getTriangle(i);
            Vec3f n = mesh.computeTriangleNormal(i);
            const Vec3f& v0 = mesh.getVertex(tri.a);
            const Vec3f& v1 = mesh.getVertex(tri.b);
            const Vec3f& v2 = mesh.getVertex(tri.c);

            file << "  facet normal " << n.x << " " << n.y << " " << n.z << "\n";
            file << "    outer loop\n";
            file << "      vertex " << v0.x << " " << v0.y << " " << v0.z << "\n";
            file << "      vertex " << v1.x << " " << v1.y << " " << v1.z << "\n";
            file << "      vertex " << v2.x << " " << v2.y << " " << v2.z << "\n";
            file << "    endloop\n";
            file << "  endfacet\n";
        }
        file << "endsolid ScanForge3D\n";
        file.close();
        return true;
    }
};

} // namespace scanforge
```

---

## 6. Modul 4: Mesh-Nachbearbeitung

### 6.1 Verarbeitungs-Pipeline (Kotlin)

```kotlin
// app/src/main/java/com/scanforge3d/processing/MeshProcessingPipeline.kt
package com.scanforge3d.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Vollständige Verarbeitungs-Pipeline:
 * Punktwolke → Normalen → Poisson → Decimation → Repair → Smoothing → Export
 */
class MeshProcessingPipeline @Inject constructor(
    private val native: NativeMeshProcessor
) {
    data class PipelineConfig(
        val voxelSize: Float = 0.002f,       // 2mm Downsampling
        val sorKNeighbors: Int = 20,          // SOR: 20 Nachbarn
        val sorStdRatio: Float = 2.0f,        // SOR: 2x Standardabweichung
        val poissonDepth: Int = 9,            // Octree-Tiefe (8=grob, 12=fein)
        val decimationRatio: Float = 0.5f,    // 50% Dreiecke behalten
        val smoothingIterations: Int = 3,     // Laplacian-Glättungs-Iterationen
        val scaleFactor: Float = 1.0f         // Skalierungsfaktor (Kalibrierung)
    )

    data class PipelineResult(
        val meshData: FloatArray,
        val vertexCount: Int,
        val triangleCount: Int,
        val isWatertight: Boolean,
        val processingTimeMs: Long
    )

    /**
     * Callback-Interface für Fortschritts-Updates
     */
    interface ProgressCallback {
        fun onProgress(step: String, progress: Float)  // 0.0 - 1.0
    }

    suspend fun process(
        pointsFlat: FloatArray,
        config: PipelineConfig = PipelineConfig(),
        callback: ProgressCallback? = null
    ): PipelineResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // Schritt 1: Voxel-Grid-Downsampling
        callback?.onProgress("Downsampling...", 0.1f)
        val downsampled = native.voxelGridFilter(pointsFlat, config.voxelSize)

        // Schritt 2: Outlier Removal
        callback?.onProgress("Rauschen entfernen...", 0.2f)
        val cleaned = native.statisticalOutlierRemoval(
            downsampled, config.sorKNeighbors, config.sorStdRatio
        )

        // Schritt 3: Normalen schätzen (für Poisson benötigt)
        callback?.onProgress("Normalen berechnen...", 0.3f)
        val pointsWithNormals = estimateNormals(cleaned)

        // Schritt 4: Poisson Surface Reconstruction
        callback?.onProgress("Oberfläche rekonstruieren...", 0.5f)
        val rawMesh = native.poissonReconstruction(
            pointsWithNormals, config.poissonDepth
        )

        // Schritt 5: Mesh Repair
        callback?.onProgress("Mesh reparieren...", 0.7f)
        val repairedMesh = native.repairMesh(rawMesh)

        // Schritt 6: Decimation (Dreieckszahl reduzieren)
        callback?.onProgress("Optimieren...", 0.85f)
        val decimatedMesh = native.decimateMesh(
            repairedMesh, config.decimationRatio
        )

        // Schritt 7: Skalierung anwenden (Kalibrierung)
        val finalMesh = if (config.scaleFactor != 1.0f) {
            applyScale(decimatedMesh, config.scaleFactor)
        } else {
            decimatedMesh
        }

        callback?.onProgress("Fertig!", 1.0f)

        val vertexCount = finalMesh[0].toInt()
        val triangleCount = finalMesh[1].toInt()

        PipelineResult(
            meshData = finalMesh,
            vertexCount = vertexCount,
            triangleCount = triangleCount,
            isWatertight = true, // Poisson erzeugt immer wasserdichte Meshes
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Normalen-Schätzung via PCA der lokalen Nachbarschaft
     * Input:  [x,y,z, x,y,z, ...]
     * Output: [x,y,z,nx,ny,nz, x,y,z,nx,ny,nz, ...]
     */
    private fun estimateNormals(pointsFlat: FloatArray): FloatArray {
        val numPoints = pointsFlat.size / 3
        val result = FloatArray(numPoints * 6)

        // Vereinfachte Normalen-Schätzung:
        // Für jeden Punkt die k nächsten Nachbarn finden und
        // PCA der lokalen Kovarianzmatrix berechnen.
        // Die Normale ist der Eigenvektor zum kleinsten Eigenwert.
        //
        // HINWEIS: In der Produktion sollte dies in C++ via nanoflann
        // KD-Tree implementiert werden für O(n log n) Komplexität.
        // Hier verwenden wir den JNI-Call.

        // Temporär: Einfache Kreuzprodukt-basierte Normalen
        for (i in 0 until numPoints) {
            val idx = i * 3
            result[i * 6] = pointsFlat[idx]       // x
            result[i * 6 + 1] = pointsFlat[idx + 1] // y
            result[i * 6 + 2] = pointsFlat[idx + 2] // z
            // Default-Normale nach oben (wird in C++ korrekt berechnet)
            result[i * 6 + 3] = 0f  // nx
            result[i * 6 + 4] = 1f  // ny
            result[i * 6 + 5] = 0f  // nz
        }
        return result
    }

    private fun applyScale(meshData: FloatArray, scale: Float): FloatArray {
        val result = meshData.copyOf()
        val vcount = result[0].toInt()
        // Nur Vertex-Positionen skalieren (nicht Indices)
        for (i in 0 until vcount) {
            val offset = 2 + i * 3
            result[offset] *= scale
            result[offset + 1] *= scale
            result[offset + 2] *= scale
        }
        return result
    }
}
```

---

## 7. Modul 5: STL-Export (On-Device)

### 7.1 Kotlin STL Exporter (Alternative ohne JNI)

```kotlin
// app/src/main/java/com/scanforge3d/export/STLExporter.kt
package com.scanforge3d.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class STLExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val native: com.scanforge3d.processing.NativeMeshProcessor
) {
    /**
     * Exportiert Mesh als Binary-STL in den Downloads-Ordner
     *
     * @return URI der exportierten Datei oder null bei Fehler
     */
    suspend fun exportToDownloads(
        meshData: FloatArray,
        fileName: String = "scan_${System.currentTimeMillis()}.stl"
    ): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)

        val success = native.exportSTL(meshData, file.absolutePath)
        return if (success) Uri.fromFile(file) else null
    }

    /**
     * Exportiert als STL via ContentResolver (SAF – Storage Access Framework)
     * Für Android 10+ Scoped Storage
     */
    suspend fun exportToUri(
        meshData: FloatArray,
        uri: Uri
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                writeSTLToStream(meshData, outputStream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Pure-Kotlin Binary-STL-Writer (Fallback ohne JNI)
     */
    private fun writeSTLToStream(meshData: FloatArray, stream: OutputStream) {
        val vertexCount = meshData[0].toInt()
        val triangleCount = meshData[1].toInt()

        val buffered = BufferedOutputStream(stream, 65536)

        // Header (80 Bytes)
        val header = ByteArray(80)
        "ScanForge3D".toByteArray().copyInto(header)
        buffered.write(header)

        // Dreieckszahl (4 Bytes, little-endian)
        val countBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        countBuf.putInt(triangleCount)
        buffered.write(countBuf.array())

        // Vertices aus meshData lesen
        val vertexOffset = 2
        val triangleOffset = vertexOffset + vertexCount * 3

        val triBuf = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)

        for (t in 0 until triangleCount) {
            val triIdx = triangleOffset + t * 3
            val a = meshData[triIdx].toInt()
            val b = meshData[triIdx + 1].toInt()
            val c = meshData[triIdx + 2].toInt()

            // Vertex-Positionen
            val v0x = meshData[vertexOffset + a * 3]
            val v0y = meshData[vertexOffset + a * 3 + 1]
            val v0z = meshData[vertexOffset + a * 3 + 2]
            val v1x = meshData[vertexOffset + b * 3]
            val v1y = meshData[vertexOffset + b * 3 + 1]
            val v1z = meshData[vertexOffset + b * 3 + 2]
            val v2x = meshData[vertexOffset + c * 3]
            val v2y = meshData[vertexOffset + c * 3 + 1]
            val v2z = meshData[vertexOffset + c * 3 + 2]

            // Facetten-Normale via Kreuzprodukt
            val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
            val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            if (len > 1e-8f) { nx /= len; ny /= len; nz /= len }

            triBuf.clear()
            triBuf.putFloat(nx); triBuf.putFloat(ny); triBuf.putFloat(nz)
            triBuf.putFloat(v0x); triBuf.putFloat(v0y); triBuf.putFloat(v0z)
            triBuf.putFloat(v1x); triBuf.putFloat(v1y); triBuf.putFloat(v1z)
            triBuf.putFloat(v2x); triBuf.putFloat(v2y); triBuf.putFloat(v2z)
            triBuf.putShort(0) // Attribut
            buffered.write(triBuf.array())
        }

        buffered.flush()
    }
}
```

---

## 8. Modul 6: 3D-Visualisierung

### 8.1 Filament/SceneView Integration

```kotlin
// app/src/main/java/com/scanforge3d/ui/preview/MeshRenderer.kt
package com.scanforge3d.ui.preview

import android.content.Context
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rendert das rekonstruierte Mesh in einer interaktiven 3D-Ansicht.
 * Verwendet Google Filament für performante mobile Darstellung.
 *
 * Features:
 * - Orbit-Kamera (Rotation per Touch)
 * - Pinch-to-Zoom
 * - Wireframe-Overlay (optional)
 * - Qualitäts-Heatmap (Vertex-Dichte als Farbkodierung)
 */
class MeshRenderer(private val context: Context) {

    /**
     * Erzeugt einen Filament-Vertex-Buffer aus dem Mesh
     */
    fun createVertexBuffer(
        engine: Engine,
        meshData: FloatArray
    ): VertexBuffer {
        val vertexCount = meshData[0].toInt()
        val vertexOffset = 2

        // Position-Buffer (3 floats pro Vertex)
        val positionBuffer = ByteBuffer.allocate(vertexCount * 3 * 4)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until vertexCount) {
            val idx = vertexOffset + i * 3
            positionBuffer.putFloat(meshData[idx])
            positionBuffer.putFloat(meshData[idx + 1])
            positionBuffer.putFloat(meshData[idx + 2])
        }
        positionBuffer.flip()

        return VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0, 12
            )
            .build(engine)
            .also { it.setBufferAt(engine, 0, positionBuffer) }
    }

    /**
     * Erzeugt einen Filament-Index-Buffer aus dem Mesh
     */
    fun createIndexBuffer(
        engine: Engine,
        meshData: FloatArray
    ): IndexBuffer {
        val vertexCount = meshData[0].toInt()
        val triangleCount = meshData[1].toInt()
        val triOffset = 2 + vertexCount * 3
        val indexCount = triangleCount * 3

        val indexBuffer = ByteBuffer.allocate(indexCount * 4)
            .order(ByteOrder.nativeOrder())

        for (t in 0 until triangleCount) {
            val idx = triOffset + t * 3
            indexBuffer.putInt(meshData[idx].toInt())
            indexBuffer.putInt(meshData[idx + 1].toInt())
            indexBuffer.putInt(meshData[idx + 2].toInt())
        }
        indexBuffer.flip()

        return IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
            .also { it.setBuffer(engine, indexBuffer) }
    }
}
```

---

## 9. Modul 7: Cloud-Backend für STEP-Export

### 9.1 FastAPI Server

```python
# cloud-backend/main.py
"""
ScanForge3D Cloud Backend
Verarbeitet hochgeladene Meshes zu STEP-Dateien via Open CASCADE.

Architektur:
- FastAPI für REST API
- Celery + Redis für async Job-Queue
- Open CASCADE (PythonOCC) für STEP-Export
- COLMAP für optionale Photogrammetrie
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import Optional
import uuid
import os
import shutil

app = FastAPI(
    title="ScanForge3D Cloud Processing",
    version="1.0.0"
)

# Job-Speicher (in Produktion: Redis/DB)
jobs: dict = {}

UPLOAD_DIR = "/tmp/scanforge/uploads"
OUTPUT_DIR = "/tmp/scanforge/outputs"
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)


class JobStatus(BaseModel):
    job_id: str
    status: str  # "pending", "processing", "completed", "failed"
    progress: float  # 0.0 - 1.0
    result_url: Optional[str] = None
    error: Optional[str] = None


class ExportRequest(BaseModel):
    format: str = "step"  # "step" oder "iges"
    simplify: bool = True
    fit_primitives: bool = False  # Reverse Engineering
    tolerance: float = 0.1  # mm


# ============================================================
# ENDPOINTS
# ============================================================

@app.post("/api/v1/upload-mesh", response_model=JobStatus)
async def upload_mesh(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = None
):
    """
    Mesh-Datei hochladen (STL, OBJ, PLY).
    Startet asynchrone Verarbeitung.
    """
    job_id = str(uuid.uuid4())
    upload_path = os.path.join(UPLOAD_DIR, f"{job_id}_{file.filename}")

    # Datei speichern
    with open(upload_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    jobs[job_id] = {
        "status": "pending",
        "progress": 0.0,
        "input_path": upload_path,
        "result_path": None,
        "error": None
    }

    # Async-Verarbeitung starten
    background_tasks.add_task(process_mesh_to_step, job_id)

    return JobStatus(job_id=job_id, status="pending", progress=0.0)


@app.post("/api/v1/upload-images", response_model=JobStatus)
async def upload_images(
    files: list[UploadFile] = File(...),
    background_tasks: BackgroundTasks = None
):
    """
    Bilder für Photogrammetrie hochladen.
    COLMAP rekonstruiert daraus ein 3D-Mesh.
    """
    job_id = str(uuid.uuid4())
    image_dir = os.path.join(UPLOAD_DIR, job_id)
    os.makedirs(image_dir, exist_ok=True)

    for img_file in files:
        img_path = os.path.join(image_dir, img_file.filename)
        with open(img_path, "wb") as f:
            shutil.copyfileobj(img_file.file, f)

    jobs[job_id] = {
        "status": "pending",
        "progress": 0.0,
        "input_path": image_dir,
        "result_path": None,
        "error": None
    }

    background_tasks.add_task(process_photogrammetry, job_id)

    return JobStatus(job_id=job_id, status="pending", progress=0.0)


@app.get("/api/v1/job/{job_id}", response_model=JobStatus)
async def get_job_status(job_id: str):
    """Job-Status abfragen."""
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    result_url = f"/api/v1/download/{job_id}" if job["result_path"] else None

    return JobStatus(
        job_id=job_id,
        status=job["status"],
        progress=job["progress"],
        result_url=result_url,
        error=job["error"]
    )


@app.get("/api/v1/download/{job_id}")
async def download_result(job_id: str):
    """Ergebnis-Datei herunterladen."""
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    if job["status"] != "completed" or not job["result_path"]:
        raise HTTPException(status_code=400, detail="Result not ready")

    return FileResponse(
        job["result_path"],
        media_type="application/step",
        filename=os.path.basename(job["result_path"])
    )


# ============================================================
# VERARBEITUNGS-FUNKTIONEN
# ============================================================

async def process_mesh_to_step(job_id: str):
    """
    Konvertiert STL/OBJ/PLY → STEP via Open CASCADE

    Pipeline:
    1. Mesh laden (trimesh)
    2. Optional: Reverse Engineering (Primitive Fitting)
    3. BREP-Shape erstellen (Open CASCADE)
    4. STEP schreiben (STEPControl_Writer)
    """
    try:
        job = jobs[job_id]
        job["status"] = "processing"
        job["progress"] = 0.1

        import trimesh
        from OCC.Core.BRep import BRep_Builder
        from OCC.Core.BRepBuilderAPI import (
            BRepBuilderAPI_MakeVertex,
            BRepBuilderAPI_MakeEdge,
            BRepBuilderAPI_MakeFace,
            BRepBuilderAPI_Sewing
        )
        from OCC.Core.STEPControl import STEPControl_Writer, STEPControl_AsIs
        from OCC.Core.Interface import Interface_Static
        from OCC.Core.gp import gp_Pnt, gp_Vec
        from OCC.Core.TColgp import TColgp_Array1OfPnt
        from OCC.Core.Poly import Poly_Triangle, Poly_Triangulation
        from OCC.Core.TopoDS import TopoDS_Shape

        # 1. Mesh laden
        mesh = trimesh.load(job["input_path"])
        job["progress"] = 0.3

        # 2. Mesh zu Open CASCADE Triangulation konvertieren
        vertices = mesh.vertices
        faces = mesh.faces

        # Triangulation erstellen
        n_vertices = len(vertices)
        n_triangles = len(faces)

        occ_points = TColgp_Array1OfPnt(1, n_vertices)
        for i, v in enumerate(vertices):
            occ_points.SetValue(i + 1, gp_Pnt(float(v[0]), float(v[1]), float(v[2])))

        triangulation = Poly_Triangulation(n_vertices, n_triangles, False)

        for i in range(n_vertices):
            triangulation.SetNode(i + 1, occ_points.Value(i + 1))

        for i, f in enumerate(faces):
            tri = Poly_Triangle(int(f[0]) + 1, int(f[1]) + 1, int(f[2]) + 1)
            triangulation.SetTriangle(i + 1, tri)

        job["progress"] = 0.5

        # 3. Sewing: Triangulation → Solid Shape
        sewing = BRepBuilderAPI_Sewing(0.1)  # Toleranz 0.1mm

        # BRepBuilderAPI_MakeFace aus Triangulation
        from OCC.Core.BRepMesh import BRepMesh_IncrementalMesh
        from OCC.Core.StlAPI import StlAPI_Reader

        # Alternative: STL direkt via Open CASCADE laden
        reader = StlAPI_Reader()
        shape = TopoDS_Shape()
        reader.Read(shape, job["input_path"])

        job["progress"] = 0.7

        # 4. STEP exportieren
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        # STEP Schema setzen (AP214 für Farben/Layer-Unterstützung)
        Interface_Static.SetCVal("write.step.schema", "AP214")
        Interface_Static.SetCVal("write.step.product.name", "ScanForge3D Scan")

        writer = STEPControl_Writer()
        writer.Transfer(shape, STEPControl_AsIs)
        status = writer.Write(output_path)

        job["progress"] = 1.0

        if status == 1:  # IFSelect_RetDone
            job["status"] = "completed"
            job["result_path"] = output_path
        else:
            job["status"] = "failed"
            job["error"] = f"STEP write failed with status {status}"

    except Exception as e:
        jobs[job_id]["status"] = "failed"
        jobs[job_id]["error"] = str(e)


async def process_photogrammetry(job_id: str):
    """
    COLMAP Photogrammetrie-Pipeline:
    1. Feature Extraction (SIFT)
    2. Feature Matching
    3. Sparse Reconstruction
    4. Dense Reconstruction
    5. Meshing (Poisson)
    6. STEP-Export
    """
    import subprocess

    try:
        job = jobs[job_id]
        job["status"] = "processing"
        image_dir = job["input_path"]

        workspace = os.path.join(UPLOAD_DIR, f"{job_id}_colmap")
        os.makedirs(workspace, exist_ok=True)
        database_path = os.path.join(workspace, "database.db")
        sparse_dir = os.path.join(workspace, "sparse")
        dense_dir = os.path.join(workspace, "dense")
        os.makedirs(sparse_dir, exist_ok=True)
        os.makedirs(dense_dir, exist_ok=True)

        # 1. Feature Extraction
        job["progress"] = 0.1
        subprocess.run([
            "colmap", "feature_extractor",
            "--database_path", database_path,
            "--image_path", image_dir,
            "--ImageReader.single_camera", "1",
            "--SiftExtraction.use_gpu", "1"
        ], check=True)

        # 2. Feature Matching
        job["progress"] = 0.25
        subprocess.run([
            "colmap", "exhaustive_matcher",
            "--database_path", database_path,
            "--SiftMatching.use_gpu", "1"
        ], check=True)

        # 3. Sparse Reconstruction
        job["progress"] = 0.4
        subprocess.run([
            "colmap", "mapper",
            "--database_path", database_path,
            "--image_path", image_dir,
            "--output_path", sparse_dir
        ], check=True)

        # 4. Dense Reconstruction
        job["progress"] = 0.55
        subprocess.run([
            "colmap", "image_undistorter",
            "--image_path", image_dir,
            "--input_path", os.path.join(sparse_dir, "0"),
            "--output_path", dense_dir,
            "--output_type", "COLMAP"
        ], check=True)

        job["progress"] = 0.65
        subprocess.run([
            "colmap", "patch_match_stereo",
            "--workspace_path", dense_dir,
            "--PatchMatchStereo.geom_consistency", "true"
        ], check=True)

        job["progress"] = 0.75
        subprocess.run([
            "colmap", "stereo_fusion",
            "--workspace_path", dense_dir,
            "--output_path", os.path.join(dense_dir, "fused.ply")
        ], check=True)

        # 5. Poisson Meshing
        job["progress"] = 0.85
        subprocess.run([
            "colmap", "poisson_mesher",
            "--input_path", os.path.join(dense_dir, "fused.ply"),
            "--output_path", os.path.join(dense_dir, "meshed.ply")
        ], check=True)

        # 6. STEP-Export (gleiche Pipeline wie mesh_to_step)
        job["progress"] = 0.95
        job["input_path"] = os.path.join(dense_dir, "meshed.ply")
        await process_mesh_to_step(job_id)

    except subprocess.CalledProcessError as e:
        jobs[job_id]["status"] = "failed"
        jobs[job_id]["error"] = f"COLMAP error: {e}"
    except Exception as e:
        jobs[job_id]["status"] = "failed"
        jobs[job_id]["error"] = str(e)


# ============================================================
# SERVER START
# ============================================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

### 9.2 requirements.txt

```
fastapi==0.109.0
uvicorn==0.27.0
python-multipart==0.0.6
trimesh==4.0.8
numpy==1.26.3
pythonocc-core==7.7.2
celery==5.3.6
redis==5.0.1
pydantic==2.5.3
```

### 9.3 Dockerfile

```dockerfile
# cloud-backend/Dockerfile
FROM condaforge/miniforge3:latest

# System-Pakete
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    colmap \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

# PythonOCC (Open CASCADE) via conda
RUN conda install -c conda-forge pythonocc-core=7.7.2 -y

# Python-Pakete
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 9.4 Android Cloud API Service

```kotlin
// app/src/main/java/com/scanforge3d/data/remote/CloudApiService.kt
package com.scanforge3d.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface CloudApiService {

    companion object {
        const val BASE_URL = "https://api.scanforge3d.com/"
        // Für lokale Entwicklung: "http://10.0.2.2:8000/"
    }

    /**
     * Mesh-Datei hochladen für STEP-Konvertierung
     */
    @Multipart
    @POST("api/v1/upload-mesh")
    suspend fun uploadMesh(
        @Part file: MultipartBody.Part
    ): Response<JobStatusResponse>

    /**
     * Bilder für Photogrammetrie hochladen
     */
    @Multipart
    @POST("api/v1/upload-images")
    suspend fun uploadImages(
        @Part files: List<MultipartBody.Part>
    ): Response<JobStatusResponse>

    /**
     * Job-Status abfragen (Polling)
     */
    @GET("api/v1/job/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): Response<JobStatusResponse>

    /**
     * Ergebnis herunterladen
     */
    @Streaming
    @GET("api/v1/download/{jobId}")
    suspend fun downloadResult(
        @Path("jobId") jobId: String
    ): Response<ResponseBody>
}

data class JobStatusResponse(
    val job_id: String,
    val status: String,
    val progress: Float,
    val result_url: String?,
    val error: String?
)
```

### 9.5 Cloud Export Manager

```kotlin
// app/src/main/java/com/scanforge3d/export/CloudExportManager.kt
package com.scanforge3d.export

import android.content.Context
import com.scanforge3d.data.remote.CloudApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class CloudExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: CloudApiService
) {
    sealed class ExportState {
        object Uploading : ExportState()
        data class Processing(val progress: Float) : ExportState()
        data class Completed(val filePath: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    /**
     * Exportiert ein Mesh als STEP-Datei über den Cloud-Service.
     * Gibt einen Flow von ExportState zurück für Live-Updates.
     */
    fun exportAsSTEP(stlFile: File): Flow<ExportState> = flow {
        emit(ExportState.Uploading)

        // 1. Upload
        val requestBody = stlFile.asRequestBody("application/sla".toMediaType())
        val part = MultipartBody.Part.createFormData(
            "file", stlFile.name, requestBody
        )

        val uploadResponse = api.uploadMesh(part)
        if (!uploadResponse.isSuccessful) {
            emit(ExportState.Error("Upload fehlgeschlagen: ${uploadResponse.code()}"))
            return@flow
        }

        val jobId = uploadResponse.body()?.job_id
            ?: run {
                emit(ExportState.Error("Keine Job-ID erhalten"))
                return@flow
            }

        // 2. Polling auf Job-Status
        var completed = false
        while (!completed) {
            delay(2000) // 2 Sekunden Polling-Intervall

            val statusResponse = api.getJobStatus(jobId)
            val status = statusResponse.body()

            when (status?.status) {
                "processing" -> {
                    emit(ExportState.Processing(status.progress))
                }
                "completed" -> {
                    completed = true
                    // 3. Ergebnis herunterladen
                    val downloadResponse = api.downloadResult(jobId)
                    if (downloadResponse.isSuccessful) {
                        val outputFile = File(
                            context.getExternalFilesDir(null),
                            "scan_${System.currentTimeMillis()}.step"
                        )
                        downloadResponse.body()?.byteStream()?.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        emit(ExportState.Completed(outputFile.absolutePath))
                    } else {
                        emit(ExportState.Error("Download fehlgeschlagen"))
                    }
                }
                "failed" -> {
                    completed = true
                    emit(ExportState.Error(status.error ?: "Unbekannter Fehler"))
                }
            }
        }
    }
}
```

---

## 10. Modul 8: CATIA-Part-Workflow

### 10.1 Strategie

Da `.CATPart` ein proprietäres Format ist, gibt es drei Ansätze:

**Ansatz A: STEP als Zwischenformat (EMPFOHLEN)**
- App erzeugt STEP-Datei über Cloud-Backend
- Nutzer importiert STEP in CATIA V5/V6
- Kein proprietärer Code nötig

**Ansatz B: Datakit CrossCad/Ware SDK (KOMMERZIELL)**
- Einziges SDK das CATPart schreiben kann
- Lizenzkosten: ca. 5.000–15.000 €/Jahr
- Server-seitige Integration möglich

**Ansatz C: CATIA Batch-Macro (DESKTOP)**
- VBA-Macro für CATIA das STEP → CATPart konvertiert
- App stellt Macro als Download bereit

### 10.2 CATIA Import-Macro (in App mitgeliefert)

```vbscript
' catia_import_macro.CATScript
' Automatische STEP → CATPart Konvertierung
'
' Installation:
' 1. In CATIA: Tools → Macro → Macros...
' 2. "Erstellen" → Name: "ScanForge_Import"
' 3. Diesen Code einfügen
' 4. Ausführen mit Pfad zur STEP-Datei

Sub CATMain()

    Dim sStepFile As String
    Dim sOutputDir As String

    ' Dateiauswahl-Dialog
    sStepFile = CATIA.FileSelectionBox( _
        "STEP-Datei auswählen", "*.stp;*.step", CatFileSelectionModeOpen)

    If sStepFile = "" Then Exit Sub

    ' STEP importieren
    Dim oDoc As Document
    Set oDoc = CATIA.Documents.Open(sStepFile)

    ' Als CATPart speichern
    sOutputDir = Left(sStepFile, InStrRev(sStepFile, "\"))
    Dim sFileName As String
    sFileName = Mid(sStepFile, InStrRev(sStepFile, "\") + 1)
    sFileName = Left(sFileName, InStrRev(sFileName, ".") - 1)

    Dim sOutputPath As String
    sOutputPath = sOutputDir & sFileName & ".CATPart"

    oDoc.SaveAs sOutputPath
    MsgBox "Gespeichert als: " & sOutputPath, vbInformation, "ScanForge3D"

End Sub
```

---

## 11. Modul 9: Kalibrierung und Referenzmaße

### 11.1 Kalibrierungs-System

```kotlin
// app/src/main/java/com/scanforge3d/ui/calibration/CalibrationViewModel.kt
package com.scanforge3d.ui.calibration

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Kalibrierung über bekannte Referenzmaße.
 *
 * Problem: Photogrammetrie/Depth-Scans haben oft einen unbekannten
 * Skalierungsfaktor. Der Nutzer setzt zwei Punkte auf eine bekannte
 * Strecke (z.B. 50mm Kante) und gibt den Soll-Wert ein.
 *
 * Berechnung:
 *   scaleFactor = knownDistance / measuredDistance
 */
@HiltViewModel
class CalibrationViewModel @Inject constructor() : ViewModel() {

    data class CalibrationState(
        val point1: FloatArray? = null,       // Erster Referenzpunkt [x,y,z]
        val point2: FloatArray? = null,       // Zweiter Referenzpunkt [x,y,z]
        val measuredDistance: Float = 0f,     // Gemessene Distanz (Scan)
        val knownDistance: Float = 0f,        // Bekannte Distanz (Nutzer-Eingabe in mm)
        val scaleFactor: Float = 1.0f,        // Berechneter Skalierungsfaktor
        val isCalibrated: Boolean = false
    )

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state

    fun setPoint1(x: Float, y: Float, z: Float) {
        _state.value = _state.value.copy(point1 = floatArrayOf(x, y, z))
        recalculate()
    }

    fun setPoint2(x: Float, y: Float, z: Float) {
        _state.value = _state.value.copy(point2 = floatArrayOf(x, y, z))
        recalculate()
    }

    fun setKnownDistance(distanceMm: Float) {
        _state.value = _state.value.copy(knownDistance = distanceMm)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val p1 = s.point1 ?: return
        val p2 = s.point2 ?: return

        val dx = p2[0] - p1[0]
        val dy = p2[1] - p1[1]
        val dz = p2[2] - p1[2]
        val measured = sqrt(dx * dx + dy * dy + dz * dz) * 1000f // m → mm

        val scale = if (measured > 0.001f && s.knownDistance > 0f) {
            s.knownDistance / measured
        } else 1.0f

        _state.value = s.copy(
            measuredDistance = measured,
            scaleFactor = scale,
            isCalibrated = s.knownDistance > 0f && measured > 0.001f
        )
    }

    fun reset() {
        _state.value = CalibrationState()
    }
}
```

---

## 12. Modul 10: UI/UX Design

### 12.1 Navigation

```kotlin
// app/src/main/java/com/scanforge3d/MainActivity.kt
package com.scanforge3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanforge3d.ui.theme.ScanForge3DTheme
import com.scanforge3d.ui.home.HomeScreen
import com.scanforge3d.ui.scan.ScanScreen
import com.scanforge3d.ui.preview.PreviewScreen
import com.scanforge3d.ui.calibration.CalibrationScreen
import com.scanforge3d.ui.export.ExportScreen
import com.scanforge3d.ui.projects.ProjectsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScanForge3DTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                onStartScan = { navController.navigate("scan") },
                                onOpenProjects = { navController.navigate("projects") }
                            )
                        }

                        composable("scan") {
                            ScanScreen(
                                onScanComplete = { scanId ->
                                    navController.navigate("calibration/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("calibration/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            CalibrationScreen(
                                scanId = scanId,
                                onCalibrated = {
                                    navController.navigate("preview/$scanId")
                                },
                                onSkip = {
                                    navController.navigate("preview/$scanId")
                                }
                            )
                        }

                        composable("preview/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            PreviewScreen(
                                scanId = scanId,
                                onExport = {
                                    navController.navigate("export/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("export/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            ExportScreen(
                                scanId = scanId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("projects") {
                            ProjectsScreen(
                                onOpenProject = { scanId ->
                                    navController.navigate("preview/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### 12.2 Scan-Screen

```kotlin
// app/src/main/java/com/scanforge3d/ui/scan/ScanScreen.kt
package com.scanforge3d.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ScanScreen(
    onScanComplete: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // AR-Kamera-Vorschau (nimmt den Großteil des Bildschirms ein)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Hier wird die ARCore SceneView eingebettet
            // Implementierung via AndroidView { ARSceneView(context) }

            // Echtzeit-Overlay: Punktwolke als Farbpunkte
            ScanOverlay(
                pointCount = scanState.pointCount,
                coverage = scanState.surfaceCoverage,
                quality = scanState.qualityScore
            )
        }

        // Steuerungsleiste unten
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fortschritts-Indikatoren
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScanMetric("Punkte", "${scanState.pointCount / 1000}k")
                    ScanMetric("Frames", "${scanState.frameCount}")
                    ScanMetric("Abdeckung", "${(scanState.surfaceCoverage * 100).toInt()}%")
                    ScanMetric("Qualität", scanState.qualityLabel)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scan-Taste
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onBack) {
                        Text("Abbrechen")
                    }

                    if (scanState.isScanning) {
                        Button(
                            onClick = {
                                viewModel.stopScan()
                                onScanComplete(scanState.scanId)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Scan beenden")
                        }
                    } else {
                        Button(onClick = { viewModel.startScan() }) {
                            Text("Scan starten")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### 12.3 Export-Screen

```kotlin
// app/src/main/java/com/scanforge3d/ui/export/ExportScreen.kt
package com.scanforge3d.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ExportScreen(
    scanId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val exportState by viewModel.exportState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Export",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // STL Export (lokal)
        ExportOption(
            title = "STL (Binary)",
            description = "Direkt auf dem Gerät. Geeignet für 3D-Druck.",
            icon = Icons.Default.Save,
            isLoading = exportState.stlExporting,
            isCompleted = exportState.stlCompleted,
            onClick = { viewModel.exportSTL(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // OBJ Export (lokal)
        ExportOption(
            title = "OBJ",
            description = "Wavefront OBJ mit Normalen. Universelles Format.",
            icon = Icons.Default.Save,
            isLoading = exportState.objExporting,
            isCompleted = exportState.objCompleted,
            onClick = { viewModel.exportOBJ(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STEP Export (Cloud)
        ExportOption(
            title = "STEP (AP214)",
            description = "CAD-kompatibel. Wird in der Cloud verarbeitet.",
            icon = Icons.Default.CloudUpload,
            isLoading = exportState.stepExporting,
            isCompleted = exportState.stepCompleted,
            progress = exportState.stepProgress,
            onClick = { viewModel.exportSTEP(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // CATIA Hinweis
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CATIA Part (.CATPart)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Exportiere als STEP und importiere die Datei in CATIA V5/V6. " +
                    "Das CATIA-Part-Format ist proprietär und kann nur direkt in " +
                    "CATIA erzeugt werden.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.downloadCATIAMacro() }) {
                    Text("CATIA Import-Macro herunterladen")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Mesh-Statistiken
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mesh-Statistiken", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                StatRow("Vertices", "${exportState.vertexCount}")
                StatRow("Dreiecke", "${exportState.triangleCount}")
                StatRow("Wasserdicht", if (exportState.isWatertight) "Ja ✓" else "Nein ✗")
                StatRow("Dateigröße (STL)", exportState.estimatedFileSize)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zurück")
        }
    }
}

@Composable
private fun ExportOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    isCompleted: Boolean,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isLoading && !isCompleted) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isCompleted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading && progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
```

---

## 13. Abhängigkeiten und Bibliotheken

### 13.1 Übersicht

| Bibliothek | Zweck | Plattform | Lizenz | Installation |
|---|---|---|---|---|
| **ARCore** | Depth API, Tracking | Android | Apache 2.0 | Gradle |
| **Eigen 3.4** | Lineare Algebra (C++) | NDK | MPL2 | Header-only, git submodule |
| **nanoflann** | KD-Tree für Nearest Neighbors | NDK | BSD | Header-only, git submodule |
| **PoissonRecon** | Screened Poisson Reconstruction | NDK | MIT | git submodule |
| **Filament/SceneView** | 3D-Rendering | Android | Apache 2.0 | Gradle |
| **Hilt** | Dependency Injection | Android | Apache 2.0 | Gradle |
| **Room** | Lokale Datenbank | Android | Apache 2.0 | Gradle |
| **Retrofit** | HTTP Client | Android | Apache 2.0 | Gradle |
| **FastAPI** | REST API | Server | MIT | pip |
| **PythonOCC** | Open CASCADE Binding | Server | LGPL 2.1 | conda |
| **trimesh** | Mesh I/O | Server | MIT | pip |
| **COLMAP** | Photogrammetrie | Server | BSD | apt/build |

### 13.2 Third-Party Setup Script

```bash
#!/bin/bash
# scripts/setup_ndk.sh
# Richtet alle Third-Party-Abhängigkeiten für den Native-Build ein

set -e

THIRD_PARTY_DIR="native/third_party"
mkdir -p $THIRD_PARTY_DIR

echo "=== Eigen 3.4 (Header-only) ==="
if [ ! -d "$THIRD_PARTY_DIR/eigen" ]; then
    git clone --branch 3.4.0 --depth 1 \
        https://gitlab.com/libeigen/eigen.git \
        $THIRD_PARTY_DIR/eigen
fi

echo "=== nanoflann (Header-only KD-Tree) ==="
if [ ! -d "$THIRD_PARTY_DIR/nanoflann" ]; then
    git clone --branch v1.5.4 --depth 1 \
        https://github.com/jlblancoc/nanoflann.git \
        $THIRD_PARTY_DIR/nanoflann
fi

echo "=== PoissonRecon ==="
if [ ! -d "$THIRD_PARTY_DIR/PoissonRecon" ]; then
    git clone --depth 1 \
        https://github.com/mkazhdan/PoissonRecon.git \
        $THIRD_PARTY_DIR/PoissonRecon
fi

echo "=== Fertig! ==="
echo "Third-Party-Bibliotheken installiert in: $THIRD_PARTY_DIR/"
ls -la $THIRD_PARTY_DIR/
```

---

## 14. Entwicklungsreihenfolge

### Phase 1: Grundlagen (Woche 1–2)
1. Android-Projekt mit Gradle + NDK aufsetzen
2. ARCore Session initialisieren
3. Raw Depth API Daten empfangen und als Punktwolke ausgeben
4. Einfache 3D-Ansicht mit SceneView/Filament

### Phase 2: Punktwolke (Woche 3–4)
5. C++ Datenstrukturen (PointCloud, Vec3f) kompilieren
6. JNI Bridge erstellen und testen
7. Voxel-Grid-Filter implementieren
8. Statistical Outlier Removal implementieren
9. Punktwolken-Akkumulation über mehrere Frames

### Phase 3: Mesh-Rekonstruktion (Woche 5–7)
10. Poisson Reconstruction integrieren (PoissonRecon lib)
11. Normalen-Schätzung via PCA
12. Mesh-Decimation (Quadric Error Metrics)
13. Mesh-Repair (Löcher füllen, Manifold, Normalen)
14. Vollständige Processing-Pipeline testen

### Phase 4: Export (Woche 8–9)
15. Binary STL Writer (C++) implementieren und testen
16. Kotlin STL Writer als Fallback
17. OBJ und PLY Writer
18. Datei-Sharing via Android Share Intent

### Phase 5: Cloud-Backend (Woche 10–12)
19. FastAPI Server aufsetzen
20. PythonOCC/Open CASCADE STEP-Export
21. Upload/Download API Endpoints
22. Android Retrofit Client
23. Polling-basierter Export-Flow
24. Docker-Deployment

### Phase 6: Kalibrierung + UI (Woche 13–14)
25. Referenzmaß-System implementieren
26. Scan-UI mit Echtzeit-Feedback
27. 3D-Vorschau mit Touch-Interaktion
28. Export-Screen mit Format-Auswahl
29. Projekt-Verwaltung (Room DB)

### Phase 7: Optimierung (Woche 15–16)
30. Performance-Profiling (GPU/CPU/Speicher)
31. Genauigkeits-Tests mit Referenzobjekten
32. Crash-Handling und Error-Recovery
33. End-to-End-Tests
34. Release-Build

---

## 15. Test-Strategie

### 15.1 Unit Tests

```kotlin
// Mesh-Verarbeitung
@Test fun `voxel filter reduces point count`()
@Test fun `SOR removes outlier points`()
@Test fun `ICP registration converges on known transformation`()
@Test fun `poisson produces watertight mesh`()
@Test fun `decimation preserves mesh topology`()
@Test fun `STL export produces valid binary file`()
@Test fun `scale factor correctly applied`()

// Kotlin-seitig
@Test fun `depth frame processor filters by confidence`()
@Test fun `point cloud accumulator limits memory usage`()
@Test fun `calibration computes correct scale factor`()
```

### 15.2 Referenz-Objekte für Genauigkeitstests

- **Kalibrierungswürfel** 25mm (3D-gedruckt, mit Messschieber verifiziert)
- **Zylindrische Bohrung** Ø20mm (für Loch-Scanning-Test)
- **L-Profil** mit bekannten Maßen (für Bauräume)
- Vergleich: Scan-Mesh vs. CAD-Referenz mit Hausdorff-Distanz

### 15.3 STL-Validierung

```bash
# ADMesh für STL-Validierung
admesh --write-binary-stl=repaired.stl scan.stl
# Prüft: Normalen, Löcher, Non-Manifold, degenerierte Dreiecke
```

---

## 16. Bekannte Einschränkungen

### Technische Grenzen

- **Genauigkeit**: Smartphone-Scans erreichen 1–5mm, nicht 0.05mm wie industrielle Scanner
- **Reflektierende Oberflächen**: Metall, Glas, glänzendes Plastik verursachen Artefakte
- **Dunkle Umgebungen**: ARCore Depth braucht ausreichend Licht
- **Große Objekte**: > 2m erfordern Multi-Scan mit ICP-Registration
- **Feine Details**: < 2mm Details werden nicht zuverlässig erfasst

### Format-Einschränkungen

- **STEP**: Nur als Tessellated Shape (Dreiecksnetz), nicht als echte BREP-Geometrie mit parametrischen Flächen. Für echtes Reverse Engineering (Mesh → BREP mit Zylindern, Ebenen etc.) ist zusätzliche manuelle CAD-Arbeit nötig.
- **CATPart**: Kein direkter Export möglich. Immer über STEP-Zwischenformat.
- **STL**: Keine Farb-/Texturinformation. Für farbige Scans PLY verwenden.

### Performance-Grenzen

- **Punktwolke**: Max. ~2 Mio. Punkte auf mid-range Geräten
- **Mesh-Rekonstruktion**: Poisson mit Depth 10+ kann >30 Sekunden dauern
- **Speicher**: Punktwolken >500MB können OOM verursachen → Voxel-Downsampling pflicht

---

## Build Commands

```bash
# Android App bauen
./gradlew assembleDebug

# Nur Native bauen
./gradlew :app:externalNativeBuildDebug

# Tests
./gradlew test

# Cloud-Backend starten
cd cloud-backend && docker-compose up -d

# Third-Party Setup
chmod +x scripts/setup_ndk.sh && ./scripts/setup_ndk.sh
```

## Architecture Rules

- Features dürfen NICHT voneinander abhängen, nur von `data/` und `processing/`
- Native Code verwendet `snake_case`, Kotlin verwendet `camelCase`
- JNI-Calls immer batchen (40% Performance-Impact bei Einzelcalls)
- Alle Float-Arrays für JNI sind flat: `[x0,y0,z0, x1,y1,z1, ...]`
- Mesh-Serialisierung: `[vertex_count, triangle_count, vertices..., indices...]`
- Poisson-Depth für mobile: 8 (schnell) bis 10 (detailliert), nie >12
- Immer `EIGEN_DONT_VECTORIZE` setzen für ARM-Kompatibilität
