cmake_minimum_required(VERSION 3.22.1)
project(llama_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

# --- Performance (kritisch) --------------------------------------------------
# Der Android-Gradle-Plugin ruft CMake fuer Debug-Varianten mit
# CMAKE_BUILD_TYPE=Debug auf (unoptimiert, Asserts an). llama.cpp/ggml laeuft
# dann etwa 50-100x langsamer. Die CI baut und verteilt ausschliesslich
# assembleDebug (.github/workflows/build-apk.yml), die LLM-Diagnose existiert nur
# im Debug-Build. Diagnose-Messung auf dem Geraet: 4 von 161 Prompt-Tokens in 20 s
# (Qwen2.5-1.5B Q4_K_M).
#
# Warum NORMALE Variablen: Die NDK-Toolchain setzt CMAKE_<LANG>_FLAGS_<CFG> als
# normale Variable. Ein set(... CACHE ... FORCE) wird seit CMP0126 (CMake >= 3.21)
# davon verdeckt und wirkt nicht. Ausserdem gewinnt bei mehreren -O-Flags das
# letzte; add_compile_options() steht in der Kommandozeile NACH den Config-Flags.
# "cppFlags += -O3" in build.gradle.kts reicht nicht (nur C++, ggml-Kern ist C).
foreach(_lang C CXX)
    foreach(_cfg DEBUG RELWITHDEBINFO RELEASE)
        set(CMAKE_${_lang}_FLAGS_${_cfg} "-O3 -DNDEBUG")
    endforeach()
endforeach()
set(CMAKE_BUILD_TYPE Release)
add_compile_options(-O3)
add_compile_definitions(NDEBUG)
message(STATUS "llama_android: CMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE} "
               "C_FLAGS_DEBUG=${CMAKE_C_FLAGS_DEBUG} C_FLAGS_RELEASE=${CMAKE_C_FLAGS_RELEASE}")

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

    add_library(llama_android SHARED llama_android.cpp build_info.cpp)
    find_library(log-lib log)
    target_include_directories(llama_android PRIVATE
        "${LLAMA_DIR}/include"
        "${LLAMA_DIR}/ggml/include")
    target_link_libraries(llama_android PRIVATE llama ${log-lib})
else()
    add_library(llama_android SHARED llama_android_stub.cpp)
endif()
