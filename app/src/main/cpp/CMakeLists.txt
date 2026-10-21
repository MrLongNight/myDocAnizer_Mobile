cmake_minimum_required(VERSION 3.22.1)
project(llama_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

# llama.cpp is pinned as a Git submodule. Compile the real engine only for
# 64-bit ABIs; 32-bit builds get a tiny JNI stub so the app remains installable
# and the Kotlin layer can deliberately use its rule/OCR fallback.
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")

    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LLAMA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

    add_library(llama_android SHARED llama_android.cpp)
    find_library(log-lib log)
    target_include_directories(llama_android PRIVATE
        "${LLAMA_DIR}/include"
        "${LLAMA_DIR}/ggml/include")
    target_link_libraries(llama_android PRIVATE llama ${log-lib})
else()
    add_library(llama_android SHARED llama_android_stub.cpp)
endif()
