cmake_minimum_required(VERSION 3.22.1)
project(scanforge_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable Eigen vectorization for ARM compatibility
add_definitions(
    -DEIGEN_DISABLE_UNALIGNED_ARRAY_ASSERT
    -DEIGEN_DONT_VECTORIZE
    -DEIGEN_MAX_ALIGN_BYTES=0
)

# Third-party header-only libraries
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/eigen
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/nanoflann
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/PoissonRecon/Src
)

# Collect source files
file(GLOB_RECURSE NATIVE_SOURCES
    "src/*.cpp"
    "src/*.h"
)

# Poisson Reconstruction sources (optional - only if third_party is present)
file(GLOB POISSON_SOURCES
    "third_party/PoissonRecon/Src/PoissonRecon.cpp"
    "third_party/PoissonRecon/Src/SurfaceTrimmer.cpp"
)

# Create shared library
add_library(scanforge_native SHARED
    ${NATIVE_SOURCES}
    ${POISSON_SOURCES}
)

# Link Android libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(scanforge_native
    ${log-lib}
    ${android-lib}
)

# NEON optimizations for ARM
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(scanforge_native PRIVATE -march=armv8-a+simd)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(scanforge_native PRIVATE -mfpu=neon -mfloat-abi=softfp)
endif()
