#!/bin/bash
# ============================================================
# ScanForge3D – Projekt-Bootstrap-Skript für Claude Code
# ============================================================
#
# Dieses Skript wird von Claude Code als ERSTES ausgeführt.
# Es erstellt die komplette Projektstruktur und initialisiert
# alle Build-Dateien.
#
# Verwendung:
#   claude "Lies CLAUDE.md und führe dann bootstrap.sh aus"
#
# ============================================================

set -e
echo "🔧 ScanForge3D – Projekt wird initialisiert..."

PROJECT_DIR="ScanForge3D"

# ============================================================
# 1. Android-Projekt Grundstruktur
# ============================================================
echo "📁 Erstelle Projektstruktur..."

mkdir -p $PROJECT_DIR/{app/src/main/{java/com/scanforge3d/{di,ui/{theme,home,scan,preview,calibration,export,projects},data/{model,repository,local,remote},scanning,processing,export},res/{layout,values,drawable,mipmap}},native/{src/{point_cloud,mesh,export,util},third_party},cloud-backend/{routers,services,models,workers},scripts}

# ============================================================
# 2. Gradle Wrapper + Settings
# ============================================================
echo "⚙️  Erstelle Gradle-Konfiguration..."

cat > $PROJECT_DIR/settings.gradle.kts << 'SETTINGS_EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ScanForge3D"
include(":app")
SETTINGS_EOF

cat > $PROJECT_DIR/build.gradle.kts << 'BUILD_ROOT_EOF'
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
BUILD_ROOT_EOF

cat > $PROJECT_DIR/gradle.properties << 'GRADLE_PROPS_EOF'
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
GRADLE_PROPS_EOF

# ============================================================
# 3. Datenmodelle
# ============================================================
echo "📦 Erstelle Datenmodelle..."

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/model/PointCloud.kt << 'POINTCLOUD_EOF'
package com.scanforge3d.data.model

import com.google.ar.core.Pose

data class PointCloud(
    val points: Array<FloatArray>,  // Jeder Eintrag: [x, y, z]
    val timestamp: Long,
    val cameraPose: Pose,
    val pointCount: Int
) {
    fun toFlatArray(): FloatArray {
        val flat = FloatArray(points.size * 3)
        points.forEachIndexed { i, p ->
            flat[i * 3] = p[0]
            flat[i * 3 + 1] = p[1]
            flat[i * 3 + 2] = p[2]
        }
        return flat
    }
}
POINTCLOUD_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/model/TriangleMesh.kt << 'TRIMESH_EOF'
package com.scanforge3d.data.model

/**
 * Kotlin-Repräsentation eines Dreiecks-Meshes.
 * 
 * Serialisiertes Format (FloatArray):
 * [vertex_count, triangle_count, v0x, v0y, v0z, v1x, ..., t0a, t0b, t0c, t1a, ...]
 */
data class TriangleMesh(
    val vertexCount: Int,
    val triangleCount: Int,
    val serializedData: FloatArray
) {
    companion object {
        fun fromSerializedData(data: FloatArray): TriangleMesh {
            return TriangleMesh(
                vertexCount = data[0].toInt(),
                triangleCount = data[1].toInt(),
                serializedData = data
            )
        }
    }
    
    fun getVertex(index: Int): FloatArray {
        val offset = 2 + index * 3
        return floatArrayOf(
            serializedData[offset],
            serializedData[offset + 1],
            serializedData[offset + 2]
        )
    }
    
    fun estimateFileSizeSTL(): Long {
        // Binary STL: 80 header + 4 count + 50 per triangle
        return 84L + triangleCount.toLong() * 50L
    }
}
TRIMESH_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/model/ScanProject.kt << 'PROJECT_EOF'
package com.scanforge3d.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_projects")
data class ScanProject(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pointCount: Int = 0,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val isCalibrated: Boolean = false,
    val scaleFactor: Float = 1.0f,
    val meshFilePath: String? = null,
    val thumbnailPath: String? = null,
    val notes: String = ""
)
PROJECT_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/model/ExportFormat.kt << 'FORMAT_EOF'
package com.scanforge3d.data.model

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val requiresCloud: Boolean
) {
    STL("stl", "application/sla", "STL (Binary)", false),
    OBJ("obj", "text/plain", "Wavefront OBJ", false),
    PLY("ply", "application/x-ply", "Stanford PLY", false),
    STEP("step", "application/step", "STEP (AP214)", true),
    CATIA("CATPart", "application/octet-stream", "CATIA V5 Part", true)
}
FORMAT_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/model/ScanMetadata.kt << 'META_EOF'
package com.scanforge3d.data.model

data class ScanMetadata(
    val deviceModel: String,
    val androidVersion: String,
    val arcoreVersion: String,
    val hasToFSensor: Boolean,
    val depthResolution: Pair<Int, Int>,  // width x height
    val totalFrames: Int,
    val scanDurationMs: Long,
    val averageConfidence: Float
)
META_EOF

# ============================================================
# 4. Room Database
# ============================================================
echo "🗄️  Erstelle Room Database..."

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/local/ScanDatabase.kt << 'DB_EOF'
package com.scanforge3d.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.scanforge3d.data.model.ScanProject

@Database(
    entities = [ScanProject::class],
    version = 1,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
DB_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/data/local/ProjectDao.kt << 'DAO_EOF'
package com.scanforge3d.data.local

import androidx.room.*
import com.scanforge3d.data.model.ScanProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM scan_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ScanProject>>

    @Query("SELECT * FROM scan_projects WHERE id = :id")
    suspend fun getProject(id: String): ScanProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ScanProject)

    @Update
    suspend fun updateProject(project: ScanProject)

    @Delete
    suspend fun deleteProject(project: ScanProject)

    @Query("DELETE FROM scan_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
DAO_EOF

# ============================================================
# 5. Hilt DI Module
# ============================================================
echo "💉 Erstelle Dependency Injection..."

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/ScanForgeApp.kt << 'APP_EOF'
package com.scanforge3d

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScanForgeApp : Application()
APP_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/di/AppModule.kt << 'DI_EOF'
package com.scanforge3d.di

import android.content.Context
import androidx.room.Room
import com.scanforge3d.data.local.ProjectDao
import com.scanforge3d.data.local.ScanDatabase
import com.scanforge3d.data.remote.CloudApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ScanDatabase = Room.databaseBuilder(
        context,
        ScanDatabase::class.java,
        "scanforge_db"
    ).build()

    @Provides
    fun provideProjectDao(db: ScanDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    @Provides
    @Singleton
    fun provideCloudApiService(client: OkHttpClient): CloudApiService =
        Retrofit.Builder()
            .baseUrl(CloudApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudApiService::class.java)
}
DI_EOF

# ============================================================
# 6. Strings und Theme
# ============================================================
echo "🎨 Erstelle UI-Ressourcen..."

cat > $PROJECT_DIR/app/src/main/res/values/strings.xml << 'STRINGS_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ScanForge3D</string>
    <string name="scan_start">Scan starten</string>
    <string name="scan_stop">Scan beenden</string>
    <string name="export">Export</string>
    <string name="projects">Projekte</string>
    <string name="calibrate">Kalibrieren</string>
    <string name="depth_not_supported">Dieses Gerät unterstützt keine Tiefenmessung.</string>
    <string name="arcore_not_installed">Bitte installiere Google Play Services for AR.</string>
</resources>
STRINGS_EOF

cat > $PROJECT_DIR/app/src/main/res/values/themes.xml << 'THEMES_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ScanForge3D" parent="android:Theme.Material.Light.NoActionBar">
    </style>
</resources>
THEMES_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/ui/theme/Color.kt << 'COLOR_EOF'
package com.scanforge3d.ui.theme

import androidx.compose.ui.graphics.Color

val ScanBlue = Color(0xFF1976D2)
val ScanBlueDark = Color(0xFF0D47A1)
val ScanGreen = Color(0xFF4CAF50)
val ScanOrange = Color(0xFFFF9800)
val ScanRed = Color(0xFFF44336)
val ScanGray = Color(0xFF424242)
val ScanLightGray = Color(0xFFF5F5F5)
COLOR_EOF

cat > $PROJECT_DIR/app/src/main/java/com/scanforge3d/ui/theme/Theme.kt << 'THEME_EOF'
package com.scanforge3d.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ScanBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = ScanGreen,
    error = ScanRed,
    background = ScanLightGray,
    surface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun ScanForge3DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
THEME_EOF

# ============================================================
# 7. Native Stub-Dateien
# ============================================================
echo "🔨 Erstelle C++ Grundstruktur..."

cat > $PROJECT_DIR/native/src/util/math_utils.h << 'MATH_EOF'
#pragma once

#include <cmath>
#include <array>
#include <algorithm>

namespace scanforge {

// 4x4 Matrix (column-major, kompatibel mit OpenGL/ARCore)
using Mat4f = std::array<float, 16>;

inline Mat4f identity4x4() {
    Mat4f m = {};
    m[0] = m[5] = m[10] = m[15] = 1.0f;
    return m;
}

inline float clamp(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

} // namespace scanforge
MATH_EOF

cat > $PROJECT_DIR/native/src/util/timer.h << 'TIMER_EOF'
#pragma once

#include <chrono>
#include <android/log.h>

namespace scanforge {

class ScopedTimer {
public:
    ScopedTimer(const char* name) : name_(name),
        start_(std::chrono::high_resolution_clock::now()) {}
    
    ~ScopedTimer() {
        auto end = std::chrono::high_resolution_clock::now();
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start_).count();
        __android_log_print(ANDROID_LOG_INFO, "ScanForge_Timer",
            "%s: %lld ms", name_, (long long)ms);
    }

private:
    const char* name_;
    std::chrono::high_resolution_clock::time_point start_;
};

} // namespace scanforge
TIMER_EOF

# ============================================================
# 8. Cloud Backend Stubs
# ============================================================
echo "☁️  Erstelle Cloud-Backend..."

cat > $PROJECT_DIR/cloud-backend/requirements.txt << 'REQ_EOF'
fastapi==0.109.0
uvicorn==0.27.0
python-multipart==0.0.6
trimesh==4.0.8
numpy==1.26.3
pythonocc-core==7.7.2
celery==5.3.6
redis==5.0.1
pydantic==2.5.3
REQ_EOF

cat > $PROJECT_DIR/cloud-backend/docker-compose.yml << 'DOCKER_EOF'
version: '3.8'
services:
  api:
    build: .
    ports:
      - "8000:8000"
    volumes:
      - ./:/app
      - scan_data:/tmp/scanforge
    environment:
      - REDIS_URL=redis://redis:6379/0
    depends_on:
      - redis

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  worker:
    build: .
    command: celery -A workers.celery_app worker -l info
    volumes:
      - ./:/app
      - scan_data:/tmp/scanforge
    environment:
      - REDIS_URL=redis://redis:6379/0
    depends_on:
      - redis

volumes:
  scan_data:
DOCKER_EOF

# ============================================================
# 9. Kopiere CLAUDE.md ins Projekt
# ============================================================
echo "📋 Kopiere CLAUDE.md..."
cp CLAUDE.md $PROJECT_DIR/CLAUDE.md 2>/dev/null || true

# ============================================================
# 10. Git initialisieren
# ============================================================
echo "🔀 Initialisiere Git..."
cd $PROJECT_DIR
cat > .gitignore << 'GIT_EOF'
# Android
*.iml
.gradle/
build/
local.properties
.idea/
*.apk
*.aab

# Native
native/third_party/
*.o
*.so

# Cloud
__pycache__/
*.pyc
.env
venv/

# OS
.DS_Store
Thumbs.db
GIT_EOF

echo ""
echo "============================================================"
echo "✅ ScanForge3D Projekt erfolgreich erstellt!"
echo "============================================================"
echo ""
echo "Projektstruktur:"
find . -type f | head -50
echo ""
echo "Nächste Schritte für Claude Code:"
echo "  1. Lies CLAUDE.md für die vollständige Spezifikation"
echo "  2. Führe scripts/setup_ndk.sh aus für Third-Party-Libs"
echo "  3. Beginne mit Phase 1: ARCore Session Setup"
echo ""
echo "Build: ./gradlew assembleDebug"
echo "Cloud: cd cloud-backend && docker-compose up -d"
echo "============================================================"
