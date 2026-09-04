package com.qingsheng.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RateLimitedException(message: String) : IOException(message)

class ApiLLM(private val config: ApiConfig) {

    var onRetry: ((attempt: Int) -> Unit)? = null

    suspend fun generate(
        system: String,
        user: String,
        temperature: Float = 0.8f,
        maxTokens: Int = 300
    ): String {
        var lastError: IOException? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return requestOnce(system, user, temperature, maxTokens)
            } catch (e: RateLimitedException) {
                lastError = e
                if (attempt < MAX_RETRIES) {
                    onRetry?.invoke(attempt + 1)
                    delay(if (attempt == 0) RETRY_FAST_MS else RETRY_SLOW_MS)
                }
            } catch (e: IOException) {
                throw e
            }
        }
        throw lastError ?: IOException("请求失败")
    }

    private suspend fun requestOnce(
        system: String,
        user: String,
        temperature: Float,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", config.model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            if (config.baseUrl.contains("bigmodel.cn")) {
                put("thinking", JSONObject().put("type", "disabled"))
            }
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }

        val connection = URL("${config.baseUrl}/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            setRequestHeaders(connection)

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code == HTTP_RATE_LIMITED) throw RateLimitedException("请求过于频繁（429）")
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code: ${readStream(connection.errorStream).take(120)}")
            }
            parseResponse(readStream(connection.inputStream))
        } finally {
            connection.disconnect()
        }
    }

    private fun setRequestHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun readStream(stream: java.io.InputStream?): String =
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

    private fun parseResponse(raw: String): String {
        val json = JSONObject(raw)
        val content = json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        if (content.isBlank()) throw IOException("返回内容为空")
        return content
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 60_000
        const val HTTP_RATE_LIMITED = 429
        const val MAX_RETRIES = 2
        const val RETRY_FAST_MS = 2_000L
        const val RETRY_SLOW_MS = 5_000L
    }
}
