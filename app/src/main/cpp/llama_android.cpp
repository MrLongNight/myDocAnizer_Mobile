#include <jni.h>
#include <android/log.h>
#include <algorithm>
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
Engine * g_engine = nullptr;
bool g_backend_ready = false;

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

void release_engine() {
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

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // portable CPU-only build
    engine->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!engine->model) {
        error = "llama.cpp could not load the selected GGUF model";
        delete engine;
        return false;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = kContextSize;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.op_offload = false;
    engine->context = llama_init_from_model(engine->model, context_params);
    if (!engine->context) {
        error = "llama.cpp could not create an inference context";
        llama_model_free(engine->model);
        delete engine;
        return false;
    }

    g_engine = engine;
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
    std::string error;
    threads = std::max(1, std::min(threads, 8));
    if (!ensure_engine(model_path, threads, error)) return error_json(error);

    const llama_vocab * vocab = llama_model_get_vocab(g_engine->model);
    std::string prompt = build_prompt(g_engine->model, system_prompt, user_prompt);
    const int generation_limit = std::max(1, std::min(max_tokens, 1024));
    const size_t prompt_limit = kContextSize > static_cast<uint32_t>(generation_limit + 16)
        ? static_cast<size_t>(kContextSize - generation_limit - 16) : 128;
    std::vector<llama_token> tokens;
    if (!fit_prompt_to_context(vocab, prompt, prompt_limit, tokens)) {
        return error_json("OCR prompt could not be fitted into the configured context window");
    }

    llama_memory_clear(llama_get_memory(g_engine->context), true);
    llama_sampler * sampler = make_sampler(vocab, temperature, top_p, grammar);
    if (!sampler) return error_json("The supplied GBNF grammar is invalid");

    const int64_t start_us = llama_time_us();
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(g_engine->context, batch) != 0) {
        llama_sampler_free(sampler);
        return error_json("llama.cpp failed while evaluating the OCR prompt");
    }
    const int64_t prompt_end_us = llama_time_us();

    std::string output;
    int generated = 0;
    for (; generated < generation_limit; ++generated) {
        llama_token token = llama_sampler_sample(sampler, g_engine->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(sampler, token);
        char piece[512];
        const int32_t piece_length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (piece_length > 0) output.append(piece, static_cast<size_t>(piece_length));
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(g_engine->context, next) != 0) break;
    }
    const int64_t end_us = llama_time_us();
    llama_sampler_free(sampler);

    const int64_t prompt_ms = (prompt_end_us - start_us) / 1000;
    const int64_t generation_ms = (end_us - prompt_end_us) / 1000;
    const float tokens_per_second = generation_ms > 0
        ? static_cast<float>(generated * 1000.0 / generation_ms) : 0.0f;

    return "{\"text\":\"" + json_escape(output) +
        "\",\"tokensGenerated\":" + std::to_string(generated) +
        ",\"promptEvalTimeMs\":" + std::to_string(prompt_ms) +
        ",\"generateTimeMs\":" + std::to_string(generation_ms) +
        ",\"tokensPerSecond\":" + std::to_string(tokens_per_second) + "}";
}

} // namespace

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
