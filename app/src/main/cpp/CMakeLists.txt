cmake_minimum_required(VERSION 3.22)
project(projectm_jni)

# JNI bridge library
add_library(projectm_jni SHARED projectm_jni.cpp)

# Link against pre-built libprojectM-4
add_library(projectM-4 SHARED IMPORTED)
set_target_properties(projectM-4 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libprojectM-4.so
)

# projectM headers (copied into project)
target_include_directories(projectm_jni PRIVATE
    ${CMAKE_SOURCE_DIR}
)

target_link_libraries(projectm_jni
    projectM-4
    android
    log
    GLESv2
    EGL
)
