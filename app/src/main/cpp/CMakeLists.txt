cmake_minimum_required(VERSION 3.22.1)
project("cult_ffmpeg")

set(CMAKE_CXX_STANDARD 17)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Add prebuilt FFmpeg libraries if present in jniLibs
set(FFMPEG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(cult_ffmpeg SHARED
    ffmpeg_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(mediandk-lib mediandk)
find_library(glesv2-lib GLESv2)

target_link_libraries(cult_ffmpeg
    ${log-lib}
    ${android-lib}
    ${mediandk-lib}
    ${glesv2-lib}
)
