# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Android app (Windows)
cd ScanForge3D
.\gradlew assembleDebug          # Debug APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew assembleRelease        # Release APK
.\gradlew :app:externalNativeBuildDebug   # Native C++ only
.\gradlew test                   # Unit tests

# Android app (Linux/Mac)
cd ScanForge3D
./gradlew assembleDebug

# Native third-party setup (required before first build)
bash scripts/setup_ndk.sh        # Clones Eigen 3.4, nanoflann 1.5.4, PoissonRecon

# Cloud backend (on server)
cd ScanForge3D/cloud-backend
docker compose up -d             # Start API + Redis + Celery worker
docker compose logs -f api       # Follow API logs
docker compose down              # Stop services
docker compose build --no-cache  # Rebuild after code changes
```

## Project Structure

This is a **three-tier** Android 3D scanning app:

```
ScanForge3D/
├── app/                    # Android app (Kotlin, Jetpack Compose, Hilt)
│   └── src/main/java/com/scanforge3d/
│       ├── ui/photocapture/   # Photo-based scanning mode
│       ├── ui/scan/           # ARCore depth scanning mode
│       ├── ui/preview/        # 3D mesh viewer
│       ├── di/AppModule.kt    # Hilt DI (OkHttp, Retrofit, Room)
│       └── data/remote/       # CloudApiService (backend API)
├── native/                 # C++ processing library (NDK, CMake)
│   ├── src/               # point_cloud/, mesh/, export/, util/
│   └── third_party/       # eigen, nanoflann, PoissonRecon (gitignored)
├── cloud-backend/          # Python FastAPI server
│   ├── services/
│   │   ├── photogrammetry_service.py  # COLMAP + trimesh/scipy meshing
│   │   └── step_export_service.py     # PythonOCC STEP export
│   ├── routers/           # API endpoints
│   ├── Dockerfile         # Ubuntu 22.04 + COLMAP (CPU-only)
│   └── requirements.txt   # fastapi, trimesh, scipy, numpy
└── scripts/               # setup_ndk.sh, build_native.sh
```

## Architecture

### Two Scan Modes
1. **Depth Scan** – ARCore Raw Depth API, on-device mesh reconstruction. Only on devices with depth sensor.
2. **Photo Mode** – CameraX photo capture → cloud upload → COLMAP photogrammetry. Works on all Android 9+ devices.

ARCore is **optional** (`AndroidManifest.xml`: `required="false"`, `value="optional"`). HomeViewModel checks depth support at startup.

### Photo Mode Pipeline (CPU-only server)
```
Phone: CameraX captures JPEGs → stored in filesDir (NOT cacheDir!)
       → bytes read into memory → OkHttp multipart upload

Server: COLMAP feature_extractor (SIFT, CPU)
      → exhaustive_matcher (CPU)
      → mapper (sparse reconstruction)
      → model_converter → PLY point cloud
      → trimesh + scipy Delaunay → triangle mesh
      → PLY download to app
```

### Data Flow (Depth Mode)
```
ARCore Frames → DepthFrameProcessor (Kotlin) → PointCloudAccumulator
    → NativeMeshProcessor (JNI) → C++ pipeline:
        VoxelGridFilter → StatisticalOutlierRemoval → NormalEstimation
        → PoissonReconstruction → MeshDecimation → MeshRepair
    → STL/OBJ/PLY export (on-device)
```

### JNI Serialization Convention
All data crosses the JNI boundary as flat `FloatArray`:
- **Point clouds:** `[x0,y0,z0, x1,y1,z1, ...]`
- **Points with normals:** `[x0,y0,z0,nx0,ny0,nz0, ...]`
- **Meshes:** `[vertex_count, triangle_count, v0x,v0y,v0z, v1x,..., t0a,t0b,t0c, ...]`

### Navigation (Jetpack Compose)
```
home → scan → calibration/{scanId} → preview/{scanId} → export/{scanId}
home → photo_capture → preview/{scanId} → export/{scanId}
home → projects → preview/{scanId}
```
Photo mode skips calibration (needs manual reference measurement for accurate scale).

### DI (Hilt) - AppModule.kt
- OkHttpClient: 60s connect, **10 min** read/write timeout (for large uploads)
- Logging: BASIC level (not BODY - causes memory issues with large uploads)
- CloudApiService: Retrofit with BASE_URL from BuildConfig

### Cloud Backend API
- `POST /api/v1/upload-images` – Upload photos, returns job_id
- `GET /api/v1/job/{id}` – Poll status (pending/processing/completed/failed)
- `GET /api/v1/download/{id}` – Download result PLY mesh
- `GET /health` – Health check

## Key Constraints

- **Photo storage:** Use `context.filesDir`, NOT `cacheDir` (Android aggressively cleans cache)
- **Upload:** Read file bytes into memory BEFORE creating RequestBody (avoids ENOENT race condition)
- **Server:** CPU-only, no GPU. Uses scipy instead of open3d (smaller footprint)
- **Language:** German UI strings, German code comments. Code identifiers in English.
- **Features must NOT depend on each other**, only on `data/` and `processing/`.
- **Native code:** `snake_case`. **Kotlin:** `camelCase`.
- **Always set** `EIGEN_DONT_VECTORIZE` for ARM compatibility.
- **Poisson depth:** 8-10 on mobile, never >12.
- **Max point cloud:** ~2M points. Voxel downsampling mandatory.
- **Scale/Calibration:** Photogrammetry has no absolute scale. User must measure reference distance.
- Batch JNI calls where possible (40% performance impact from individual calls).

## Server Deployment

```bash
# First time setup on Debian/Ubuntu server
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker $USER  # then re-login

# Clone and start
git clone https://github.com/USERNAME/3D-Scanning-App.git
cd 3D-Scanning-App/ScanForge3D/cloud-backend
docker compose up -d

# Update server after code changes
cd ~/path/to/3D-Scanning-App
git checkout -- .  # discard local changes
git pull
cd ScanForge3D/cloud-backend
docker compose down
docker compose build --no-cache
docker compose up -d

# Check logs
docker compose logs --tail=100 api
```

## Tech Stack

| Layer | Stack |
|---|---|
| Android UI | Kotlin, Jetpack Compose, Material 3, Hilt, Room, CameraX, Coil |
| 3D Rendering | Filament via SceneView (`arsceneview:2.1.0`) |
| AR | ARCore Raw Depth API (optional) |
| Native | C++17, Eigen, nanoflann, PoissonRecon, NEON SIMD |
| Network | Retrofit + OkHttp (10 min timeout), polling-based async |
| Cloud | FastAPI, COLMAP (CPU), trimesh, scipy, Docker on Ubuntu 22.04 |
