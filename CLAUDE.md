# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Android app (requires Gradle wrapper - generate with: gradle wrapper --gradle-version 8.5)
cd ScanForge3D
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew :app:externalNativeBuildDebug   # Native C++ only
./gradlew test                   # Unit tests

# Native third-party setup (required before first build)
bash scripts/setup_ndk.sh        # Clones Eigen 3.4, nanoflann 1.5.4, PoissonRecon

# Cloud backend
cd ScanForge3D/cloud-backend
docker-compose up -d             # Start API + Redis + Celery worker
docker-compose logs -f api       # Follow API logs
bash scripts/deploy_cloud.sh status  # Check service health
```

## Project Structure

This is a **three-tier** Android 3D scanning app:

```
ScanForge3D/
├── app/                    # Android app (Kotlin, Jetpack Compose, Hilt)
├── native/                 # C++ processing library (NDK, CMake)
│   ├── src/               # point_cloud/, mesh/, export/, util/
│   └── third_party/       # eigen, nanoflann, PoissonRecon (gitignored, setup via script)
├── cloud-backend/          # Python FastAPI + Celery + Redis
│   └── services/          # STEP export (PythonOCC), photogrammetry (COLMAP)
└── scripts/               # setup_ndk.sh, build_native.sh, deploy_cloud.sh
```

## Architecture

### Two Scan Modes
1. **Depth Scan** – ARCore Raw Depth API, on-device mesh reconstruction. Only on devices with depth sensor.
2. **Photo Mode** – CameraX photo capture → cloud upload → COLMAP photogrammetry. Works on all Android 9+ devices.

ARCore is **optional** (`AndroidManifest.xml`: `required="false"`, `value="optional"`). HomeViewModel checks depth support at startup and shows/hides the depth scan button accordingly.

### Data Flow
```
Camera Frames → DepthFrameProcessor (Kotlin) → PointCloudAccumulator
    → NativeMeshProcessor (JNI) → C++ pipeline:
        VoxelGridFilter → StatisticalOutlierRemoval → NormalEstimation
        → PoissonReconstruction → MeshDecimation → MeshRepair
    → STL/OBJ/PLY export (on-device) or upload to cloud for STEP export
```

### JNI Serialization Convention
All data crosses the JNI boundary as flat `FloatArray`:
- **Point clouds:** `[x0,y0,z0, x1,y1,z1, ...]`
- **Points with normals:** `[x0,y0,z0,nx0,ny0,nz0, ...]`
- **Meshes:** `[vertex_count, triangle_count, v0x,v0y,v0z, v1x,..., t0a,t0b,t0c, ...]`
- **Transforms:** 16 floats, column-major 4x4 matrix

### Navigation (Jetpack Compose)
```
home → scan → calibration/{scanId} → preview/{scanId} → export/{scanId}
home → photo_capture → preview/{scanId} → export/{scanId}
home → projects → preview/{scanId}
```
Photo mode skips calibration (COLMAP provides absolute scale).

### DI (Hilt)
`AppModule.kt` provides: Room database, ProjectDao, ScanDao, OkHttpClient (30s connect / 120s read+write), CloudApiService (Retrofit).

### Cloud Backend
- **API:** FastAPI on port 8000
- **Queue:** Celery + Redis on port 6379
- **Endpoints:** `POST /api/v1/upload-mesh`, `POST /api/v1/upload-images`, `GET /api/v1/job/{id}`, `GET /api/v1/download/{id}`
- **STEP export:** PythonOCC (Open CASCADE) via `StlAPI_Reader` → `STEPControl_Writer`
- **Photogrammetry:** COLMAP pipeline (feature extraction → matching → sparse → dense → Poisson mesh)

## Key Constraints

- **Language:** German UI strings, German code comments. Code identifiers in English.
- **Features must NOT depend on each other**, only on `data/` and `processing/`.
- **Native code:** `snake_case`. **Kotlin:** `camelCase`.
- **Always set** `EIGEN_DONT_VECTORIZE` and `EIGEN_DISABLE_UNALIGNED_ARRAY_ASSERT` for ARM.
- **Poisson depth:** 8 (fast) to 10 (detailed) on mobile, never >12.
- **Max point cloud:** ~2M points on mid-range devices. Voxel downsampling is mandatory.
- **CATIA Part (.CATPart):** Cannot be exported directly. Always via STEP intermediate format.
- **STL:** No color/texture. Use PLY for colored scans.
- Batch JNI calls where possible (40% performance impact from individual calls).
- `ProjectRepository.saveProject()` for insert (not `insertProject`).

## Tech Stack

| Layer | Stack |
|---|---|
| Android UI | Kotlin, Jetpack Compose, Material 3, Hilt, Room, CameraX, Coil |
| 3D Rendering | Filament via SceneView (`arsceneview:2.1.0`) |
| AR | ARCore Raw Depth API (optional) |
| Native | C++17, Eigen, nanoflann, PoissonRecon, NEON SIMD |
| Network | Retrofit + OkHttp, polling-based async |
| Cloud | FastAPI, Celery, Redis, PythonOCC 7.7.2, COLMAP, Docker |
