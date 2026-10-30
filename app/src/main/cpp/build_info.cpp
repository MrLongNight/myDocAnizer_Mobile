#include <jni.h>
#include <string>

#include "llama.h"

// Nur Diagnose: Zeigt, WIE diese native Bibliothek kompiliert wurde und welche
// CPU-Funktionen llama.cpp/ggml nutzt. Wird von NativeBuildInfo.kt aufgerufen und
// erscheint im Export der LLM-Diagnose (Berichtsversion 4).
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_service_NativeBuildInfo_nativeGetBuildInfo(JNIEnv * env, jobject) {
    std::string info;
#if defined(__OPTIMIZE__)
    info += "Native-Optimierung=AN(__OPTIMIZE__)";
#else
    info += "Native-Optimierung=AUS(unoptimiert, -O0!)";
#endif
#if defined(NDEBUG)
    info += "; NDEBUG=1";
#else
    info += "; NDEBUG=0";
#endif
#if defined(__aarch64__)
    info += "; arch=arm64";
#elif defined(__x86_64__)
    info += "; arch=x86_64";
#else
    info += "; arch=other";
#endif
#if defined(__ARM_NEON)
    info += "; Compile-NEON=1";
#else
    info += "; Compile-NEON=0";
#endif
#if defined(__ARM_FEATURE_DOTPROD)
    info += "; Compile-DOTPROD=1";
#else
    info += "; Compile-DOTPROD=0";
#endif
#if defined(__ARM_FEATURE_MATMUL_INT8)
    info += "; Compile-I8MM=1";
#else
    info += "; Compile-I8MM=0";
#endif
    const char * system_info = llama_print_system_info();
    info += "; llama_system_info=";
    info += system_info ? system_info : "n/a";
    return env->NewStringUTF(info.c_str());
}
