cmake_minimum_required(VERSION 3.22)
project(lossless)

include(ExternalProject)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os -ffunction-sections -fdata-sections -fno-exceptions -fno-rtti")
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -s")

set(JPEGTURBO_INSTALL_DIR ${CMAKE_BINARY_DIR}/jpeg-turbo-install)

ExternalProject_Add(
    jpeg_turbo_build
    SOURCE_DIR ${CMAKE_SOURCE_DIR}/third_party/libjpeg-turbo
    CMAKE_ARGS
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DANDROID_NDK=${ANDROID_NDK}
        -DCMAKE_INSTALL_PREFIX=${JPEGTURBO_INSTALL_DIR}
        -DCMAKE_BUILD_TYPE=Release
        -DENABLE_SHARED=FALSE
        -DENABLE_STATIC=TRUE
        -DWITH_TURBOJPEG=TRUE
        -DWITH_JPEG8=TRUE
        -DWITH_JAVA=FALSE
        -DWITH_SIMD=FALSE
    BUILD_BYPRODUCTS ${JPEGTURBO_INSTALL_DIR}/lib/libturbojpeg.a
    INSTALL_COMMAND ${CMAKE_COMMAND} --build . --target install
)

add_library(lossless SHARED jpeg_transform.cpp)
add_dependencies(lossless jpeg_turbo_build)

target_include_directories(lossless PRIVATE ${JPEGTURBO_INSTALL_DIR}/include)
target_link_libraries(lossless
    ${JPEGTURBO_INSTALL_DIR}/lib/libturbojpeg.a
    log android)
