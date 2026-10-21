#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeExecuteLlamaWithGrammar(
        JNIEnv *env, jobject, jstring, jstring, jstring, jint, jint, jfloat, jfloat, jint, jstring) {
    return env->NewStringUTF("{\"error\":\"Native llama.cpp inference is disabled on this 32-bit ABI\"}");
}
