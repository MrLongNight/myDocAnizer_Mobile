#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "llama-android"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr uint32_t kContextSize = 2048;

struct Engine {
    std::string model_path;
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    int threads = 4;
};

std::mutex g_mutex;
std::atomic<int> g_inference_progress{-1};
std::atomic<int> g_inference_stage{0};
std::atomic<bool> g_cancel_requested{false};

enum NativeStage : int {
    STAGE_IDLE = 0, STAGE_MODEL_LOAD = 1, STAGE_CONTEXT_INIT = 2,
    STAGE_PROMPT_PREPARE = 3, STAGE_PROMPT_EVAL = 4, STAGE_GENERATION = 5,
    STAGE_COMPLETE = 6, STAGE_CANCELLED = 7, STAGE_ERROR = 8
};
Engine * g_engine = nullptr;
std::atomic<bool> g_engine_loaded{false};
bool g_backend_ready = false;

// llama.cpp calls this during prompt evaluation and token generation. Unlike the
// Kotlin timeout watcher, this can interrupt a long synchronous llama_decode().
bool should_abort_inference(void *) {
    return g_cancel_requested.load(std::memory_order_relaxed);
}

bool continue_model_loading(float, void *) {
    return !g_cancel_requested.load(std::memory_order_relaxed);
}

std::string from_jstring(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string json_escape(const std::string & value) {
    std::string out;
    out.reserve(value.size() + 16);
    char escaped[7];
    for (unsigned char c : value) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    std::snprintf(escaped, sizeof(escaped), "\\u%04x", c);
                    out += escaped;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

std::string error_json(const std::string & message) {
    return "{\"error\":\"" + json_escape(message) + "\"}";
}

std::string inference_result_json(const std::string & output, int generated, const char * finish_reason,
                                  int64_t prompt_ms, int64_t generation_ms, float tokens_per_second, const char * phase) {
    return "{\"text\":\"" + json_escape(output) +
        "\",\"tokensGenerated\":" + std::to_string(generated) +
        ",\"finish_reason\":\"" + finish_reason +
        "\",\"phase\":\"" + phase +
        "\",\"promptEvalTimeMs\":" + std::to_string(prompt_ms) +
        ",\"generateTimeMs\":" + std::to_string(generation_ms) +
        ",\"tokensPerSecond\":" + std::to_string(tokens_per_second) + "}";
}

void release_engine() {
    g_engine_loaded.store(false, std::memory_order_relaxed);
    if (!g_engine) return;
    if (g_engine->context) llama_free(g_engine->context);
    if (g_engine->model) llama_model_free(g_engine->model);
    delete g_engine;
    g_engine = nullptr;
}

bool ensure_engine(const std::string & model_path, int threads, std::string & error) {
    if (g_engine && g_engine->model && g_engine->context &&
        g_engine->model_path == model_path && g_engine->threads == threads) {
        return true;
    }

    release_engine();
    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    auto * engine = new Engine();
    engine->model_path = model_path;
    engine->threads = threads;

    g_inference_stage.store(STAGE_MODEL_LOAD, std::memory_order_relaxed);
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // portable CPU-only build
    model_params.progress_callback = continue_model_loading;
    model_params.progress_callback_user_data = nullptr;
    engine->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!engine->model) {
        const bool cancelled = g_cancel_requested.load(std::memory_order_relaxed);
        error = cancelled ? "cancelled:model_load" : "llama.cpp could not load the selected GGUF model";
        g_inference_stage.store(cancelled ? STAGE_CANCELLED : STAGE_ERROR, std::memory_order_relaxed);
        delete engine;
        return false;
    }
    if (g_cancel_requested.load(std::memory_order_relaxed)) {
        llama_model_free(engine->model);
        delete engine;
        error = "cancelled:model_load";
        g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
        return false;
    }
    g_inference_stage.store(STAGE_CONTEXT_INIT, std::memory_order_relaxed);
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = kContextSize;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.op_offload = false;
    context_params.abort_callback = should_abort_inference;
    context_params.abort_callback_data = nullptr;
    engine->context = llama_init_from_model(engine->model, context_params);
    if (!engine->context) {
        const bool cancelled = g_cancel_requested.load(std::memory_order_relaxed);
        error = cancelled ? "cancelled:context_init" : "llama.cpp could not create an inference context";
        llama_model_free(engine->model);
        delete engine;
        g_inference_stage.store(cancelled ? STAGE_CANCELLED : STAGE_ERROR, std::memory_order_relaxed);
        return false;
    }
    if (g_cancel_requested.load(std::memory_order_relaxed)) {
        llama_free(engine->context);
        llama_model_free(engine->model);
        delete engine;
        error = "cancelled:context_init";
        g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
        return false;
    }
    g_engine = engine;
    g_engine_loaded.store(true, std::memory_order_relaxed);
    return true;
}

std::string build_prompt(
        const llama_model * model,
        const std::string & system_prompt,
        const std::string & user_prompt) {
    const char * chat_template = llama_model_chat_template(model, nullptr);
    if (!chat_template) {
        return system_prompt.empty() ? user_prompt : system_prompt + "\n\n" + user_prompt;
    }

    std::vector<llama_chat_message> messages;
    if (!system_prompt.empty()) messages.push_back({"system", system_prompt.c_str()});
    messages.push_back({"user", user_prompt.c_str()});

    std::vector<char> buffer(system_prompt.size() + user_prompt.size() + 4096);
    int32_t length = llama_chat_apply_template(
        chat_template, messages.data(), messages.size(), true,
        buffer.data(), static_cast<int32_t>(buffer.size()));
    if (length < 0) {
        return system_prompt.empty() ? user_prompt : system_prompt + "\n\n" + user_prompt;
    }
    if (length > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(length) + 1);
        length = llama_chat_apply_template(
            chat_template, messages.data(), messages.size(), true,
            buffer.data(), static_cast<int32_t>(buffer.size()));
        if (length < 0) return system_prompt.empty() ? user_prompt : system_prompt + "\n\n" + user_prompt;
    }
    return std::string(buffer.data(), static_cast<size_t>(length));
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    const int32_t required = -llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        nullptr, 0, true, true);
    if (required <= 0) return {};
    std::vector<llama_token> tokens(static_cast<size_t>(required));
    const int32_t actual = llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (actual < 0) return {};
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

bool fit_prompt_to_context(
        const llama_vocab * vocab,
        std::string & prompt,
        size_t max_prompt_tokens,
        std::vector<llama_token> & tokens) {
    tokens = tokenize(vocab, prompt);
    constexpr const char * marker = "\n[OCR-Text in der Mitte gekuerzt]\n";
    while (tokens.size() > max_prompt_tokens && prompt.size() > 512) {
        const double ratio = static_cast<double>(max_prompt_tokens) /
                             static_cast<double>(tokens.size());
        const size_t keep = std::max<size_t>(512, static_cast<size_t>(prompt.size() * ratio * 0.92));
        const size_t marker_size = std::string(marker).size();
        const size_t content_size = keep > marker_size ? keep - marker_size : 256;
        const size_t head = content_size * 2 / 3;
        const size_t tail = content_size - head;
        prompt = prompt.substr(0, head) + marker + prompt.substr(prompt.size() - tail);
        tokens = tokenize(vocab, prompt);
    }
    return !tokens.empty() && tokens.size() <= max_prompt_tokens;
}

llama_sampler * make_sampler(
        const llama_vocab * vocab,
        float temperature,
        float top_p,
        const std::string & grammar) {
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    }
    if (!grammar.empty()) {
        llama_sampler * grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (!grammar_sampler) {
            llama_sampler_free(sampler);
            return nullptr;
        }
        llama_sampler_chain_add(sampler, grammar_sampler);
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }
    return sampler;
}

std::string run_inference(
        const std::string & model_path,
        const std::string & user_prompt,
        const std::string & system_prompt,
        int threads,
        float temperature,
        float top_p,
        int max_tokens,
        const std::string & grammar) {
    std::lock_guard<std::mutex> lock(g_mutex);
    // Cancellation is reset before Kotlin starts the watcher; never clear it at JNI entry.
    g_inference_progress.store(5, std::memory_order_relaxed);
    g_inference_stage.store(STAGE_MODEL_LOAD, std::memory_order_relaxed);
    std::string error;
    threads = std::max(1, std::min(threads, 8));
    if (!ensure_engine(model_path, threads, error)) {
        g_inference_progress.store(-1, std::memory_order_relaxed);
        if (g_cancel_requested.load(std::memory_order_relaxed) || error.rfind("cancelled:", 0) == 0) {
            const std::string phase = error.rfind("cancelled:", 0) == 0 ? error.substr(10) : "model_load";
            g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
            return inference_result_json("", 0, "cancelled", 0, 0, 0.0f, phase.c_str());
        }
        g_inference_stage.store(STAGE_ERROR, std::memory_order_relaxed);
        return error_json(error);
    }
    if (g_cancel_requested.load(std::memory_order_relaxed)) {
        g_inference_progress.store(-1, std::memory_order_relaxed);
        g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
        return inference_result_json("", 0, "cancelled", 0, 0, 0.0f, "context_init");
    }
    g_inference_progress.store(15, std::memory_order_relaxed);
    g_inference_stage.store(STAGE_PROMPT_PREPARE, std::memory_order_relaxed);
    const llama_vocab * vocab = llama_model_get_vocab(g_engine->model);
    std::string prompt = build_prompt(g_engine->model, system_prompt, user_prompt);
    if (g_cancel_requested.load(std::memory_order_relaxed)) {
        g_inference_progress.store(-1, std::memory_order_relaxed);
        g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
        return inference_result_json("", 0, "cancelled", 0, 0, 0.0f, "prompt_prepare");
    }
    g_inference_progress.store(20, std::memory_order_relaxed);
    const int generation_limit = std::max(1, std::min(max_tokens, 1024));
    const size_t prompt_limit = kContextSize > static_cast<uint32_t>(generation_limit + 16)
        ? static_cast<size_t>(kContextSize - generation_limit - 16) : 128;
    std::vector<llama_token> tokens;
    if (!fit_prompt_to_context(vocab, prompt, prompt_limit, tokens)) {
        return error_json("OCR prompt could not be fitted into the configured context window");
    }

    llama_memory_clear(llama_get_memory(g_engine->context), true);
    llama_sampler * sampler = make_sampler(vocab, temperature, top_p, grammar);
    if (!sampler) {
        g_inference_stage.store(STAGE_ERROR, std::memory_order_relaxed);
        return error_json("The supplied GBNF grammar is invalid");
    }
    const int64_t start_us = llama_time_us();
    g_inference_stage.store(STAGE_PROMPT_EVAL, std::memory_order_relaxed);
    constexpr size_t kPromptDecodeChunkTokens = 32;
    size_t prompt_offset = 0;
    while (prompt_offset < tokens.size()) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            const int64_t elapsed_ms = (llama_time_us() - start_us) / 1000;
            llama_sampler_free(sampler);
            g_inference_progress.store(-1, std::memory_order_relaxed);
            g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
            return inference_result_json("", 0, "cancelled", elapsed_ms, 0, 0.0f, "prompt_eval");
        }
        const size_t chunk_size = std::min(kPromptDecodeChunkTokens, tokens.size() - prompt_offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + prompt_offset, static_cast<int32_t>(chunk_size));
        if (llama_decode(g_engine->context, batch) != 0) {
            const int64_t elapsed_ms = (llama_time_us() - start_us) / 1000;
            llama_sampler_free(sampler);
            g_inference_progress.store(-1, std::memory_order_relaxed);
            if (g_cancel_requested.load(std::memory_order_relaxed)) {
                g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
                return inference_result_json("", 0, "cancelled", elapsed_ms, 0, 0.0f, "prompt_eval");
            }
            g_inference_stage.store(STAGE_ERROR, std::memory_order_relaxed);
            return error_json("llama.cpp failed while evaluating the OCR prompt");
        }
        prompt_offset += chunk_size;
        const int prompt_progress = 25 + static_cast<int>((prompt_offset * 19) / tokens.size());
        g_inference_progress.store(std::min(44, prompt_progress), std::memory_order_relaxed);
    }
    const int64_t prompt_end_us = llama_time_us();
    g_inference_progress.store(45, std::memory_order_relaxed);
    g_inference_stage.store(STAGE_GENERATION, std::memory_order_relaxed);

    std::string output;
    int generated = 0;
    bool reached_eog = false;
    bool decode_failed = false;
    bool cancelled = false;
    for (; generated < generation_limit; ++generated) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) { cancelled = true; break; }
        const int progress = 45 + static_cast<int>((static_cast<int64_t>(generated) * 54) / generation_limit);
        g_inference_progress.store(progress, std::memory_order_relaxed);
        llama_token token = llama_sampler_sample(sampler, g_engine->context, -1);
        if (llama_vocab_is_eog(vocab, token)) { reached_eog = true; break; }
        llama_sampler_accept(sampler, token);
        char piece[512];
        const int32_t piece_length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (piece_length > 0) output.append(piece, static_cast<size_t>(piece_length));
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(g_engine->context, next) != 0) {
            // llama.cpp's abort callback reports cancellation as a failed decode.
            // Preserve the distinction so the diagnostic abort test does not label
            // a deliberate stop as a native inference error.
            if (g_cancel_requested.load(std::memory_order_relaxed)) {
                cancelled = true;
            } else {
                decode_failed = true;
            }
            break;
        }
    }
    const int64_t end_us = llama_time_us();
    llama_sampler_free(sampler);
    g_inference_progress.store(100, std::memory_order_relaxed);

    const int64_t prompt_ms = (prompt_end_us - start_us) / 1000;
    const int64_t generation_ms = (end_us - prompt_end_us) / 1000;
    const float tokens_per_second = generation_ms > 0
        ? static_cast<float>(generated * 1000.0 / generation_ms) : 0.0f;

    const char * finish_reason = decode_failed ? "error" : (cancelled ? "cancelled" : (reached_eog ? "stop" : "length"));
    const char * phase = decode_failed ? "generation_error" : (cancelled ? "generation" : "complete");
    if (decode_failed) {
        g_inference_progress.store(-1, std::memory_order_relaxed);
        g_inference_stage.store(STAGE_ERROR, std::memory_order_relaxed);
    } else if (cancelled) {
        g_inference_progress.store(-1, std::memory_order_relaxed);
        g_inference_stage.store(STAGE_CANCELLED, std::memory_order_relaxed);
    } else {
        g_inference_progress.store(100, std::memory_order_relaxed);
        g_inference_stage.store(STAGE_COMPLETE, std::memory_order_relaxed);
    }
    return inference_result_json(output, generated, finish_reason, prompt_ms, generation_ms, tokens_per_second, phase);
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeGetInferenceProgress(JNIEnv *, jobject) {
    return static_cast<jint>(g_inference_progress.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeGetInferenceStage(JNIEnv *, jobject) {
    return static_cast<jint>(g_inference_stage.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeResetCancellation(JNIEnv *, jobject) {
    g_cancel_requested.store(false, std::memory_order_relaxed);
    g_inference_stage.store(STAGE_IDLE, std::memory_order_relaxed);
    g_inference_progress.store(-1, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeExecuteLlamaWithGrammar(
        JNIEnv * env, jobject,
        jstring model_path, jstring prompt, jstring system_prompt,
        jint threads, jint, jfloat temperature, jfloat top_p,
        jint max_tokens, jstring grammar) {
    const std::string result = run_inference(
        from_jstring(env, model_path),
        from_jstring(env, prompt),
        from_jstring(env, system_prompt),
        threads, temperature, top_p, max_tokens,
        from_jstring(env, grammar));
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeUnloadEngine(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
    g_inference_progress.store(-1, std::memory_order_relaxed);
    g_inference_stage.store(STAGE_IDLE, std::memory_order_relaxed);
    g_cancel_requested.store(false, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeIsEngineLoaded(JNIEnv *, jobject) {
    // Never block the UI thread behind the long-running inference mutex.
    return g_engine_loaded.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeGetLoadedModelPath(JNIEnv * env, jobject) {
    // Diagnostics can run on the main thread; return immediately while inference owns the mutex.
    std::unique_lock<std::mutex> lock(g_mutex, std::try_to_lock);
    if (!lock.owns_lock()) return env->NewStringUTF("__INFERENCE_BUSY__");
    const std::string path = (g_engine && g_engine->model) ? g_engine->model_path : std::string();
    return env->NewStringUTF(path.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeGetContextSize(JNIEnv *, jobject) {
    return static_cast<jint>(kContextSize);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_service_LlamaCppInferenceEngine_nativeCancelInference(JNIEnv *, jobject) {
    g_cancel_requested.store(true, std::memory_order_relaxed);
}
