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
