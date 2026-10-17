package com.mochi.ascend.ai

import com.mochi.ascend.data.AiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to any OpenAI-compatible `/chat/completions` endpoint.
 * Endpoint, key and model come from Settings, so this works with
 * OpenRouter, Groq, DeepSeek, OpenAI, or a local llama.cpp server.
 */
object AiClient {

    private val net = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun complete(cfg: AiConfig, system: String, user: String): String {
        val first = request(cfg, system, user, jsonMode = true)
        // Plenty of OpenAI-compatible servers don't implement response_format.
        // Rather than fail, retry once without it.
        return first.getOrElse { error ->
            val msg = error.message.orEmpty()
            if (msg.contains("400") || msg.contains("response_format", ignoreCase = true)) {
                request(cfg, system, user, jsonMode = false).getOrThrow()
            } else {
                throw error
            }
        }
    }

    private suspend fun request(
        cfg: AiConfig,
        system: String,
        user: String,
        jsonMode: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (cfg.apiKey.isBlank()) throw IllegalStateException("No API key set in Settings.")
            if (cfg.model.isBlank()) throw IllegalStateException("No model set in Settings.")

            val base = cfg.baseUrl.trim().trimEnd('/')
            if (base.isBlank()) throw IllegalStateException("No base URL set in Settings.")
            val url = URL("$base/chat/completions")

            val payload = buildJsonObject {
                put("model", cfg.model)
                put("temperature", 0.4)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", user) })
                }
                if (jsonMode) {
                    put("response_format", buildJsonObject { put("type", "json_object") })
                }
            }.toString()

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            }

            try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code - ${body.take(500).ifBlank { "no response body" }}")
                }
                extractContent(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Pulls `choices[0].message.content` out of the response envelope. */
    private fun extractContent(body: String): String {
        val root = net.parseToJsonElement(body).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: throw IllegalStateException("Response had no 'choices' field.")
        val first = choices.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("Model returned no choices.")
        val content = first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
        if (content.isNullOrBlank()) throw IllegalStateException("Model returned an empty message.")
        return content
    }
}
