package com.example.jarvis

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Direct online AI client.
 *
 * Uses an OpenAI-compatible Chat Completions endpoint.
 * The API key is encrypted with Android Keystore before being stored.
 */
class OnlineAiBrain(private val context: Context) {

    companion object {
        private const val PREFS = "jarvis_online_ai"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_MODEL = "model"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API = "api_blob"
        private const val KEY_IV = "api_iv"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_online_ai_key"
        private const val DEFAULT_ENDPOINT =
            "https://api.openai.com/v1/chat/completions"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) && !getApiKey().isNullOrBlank()

    fun getEndpoint(): String =
        prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_ENDPOINT }

    fun getModel(): String =
        prefs.getString(KEY_MODEL, DEFAULT_MODEL)
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_MODEL }

    fun setConfig(endpoint: String, model: String, apiKey: String, enabled: Boolean) {
        prefs.edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_MODEL, model.trim())
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (apiKey.isNotBlank()) {
            encryptAndStore(apiKey.trim())
        } else {
            prefs.edit().remove(KEY_API).remove(KEY_IV).apply()
        }
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    fun getApiKey(): String? {
        val blob = prefs.getString(KEY_API, null) ?: return null
        val ivText = prefs.getString(KEY_IV, null) ?: return null

        return try {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Synchronous helper used by the existing command manager.
     * Network work itself runs on a worker thread.
     */
    fun askBlocking(userText: String): String? {
        if (!isEnabled() || userText.isBlank()) return null

        val task = Callable { request(userText) }
        val executor = Executors.newSingleThreadExecutor()

        return try {
            executor.submit(task).get(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        } finally {
            executor.shutdownNow()
        }
    }

    fun testConnection(): String {
        if (!isEnabled()) {
            return "पहले API Key डालकर Online AI चालू करें"
        }

        val result = askBlocking("Reply with exactly: JARVIS ONLINE")
        return if (result.isNullOrBlank()) {
            "Online AI test failed. Endpoint, model, API key और internet check करें."
        } else {
            "Online AI connected: $result"
        }
    }

    private fun request(userText: String): String? {
        val apiKey = getApiKey() ?: return null
        val endpoint = getEndpoint()
        val model = getModel()

        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "role",
                                "system"
                            )
                            put(
                                "content",
                                "You are JARVIS, a concise Hindi-speaking phone assistant. " +
                                    "Answer naturally in Hindi unless the user asks for another language. " +
                                    "Do not claim to have performed phone actions unless the app actually performed them."
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userText)
                        }
                    )
                }
            )
            put("temperature", 0.5)
        }.toString()

        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (code !in 200..299) return null

            parseAnswer(responseText)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAnswer(jsonText: String): String? {
        return try {
            val root = JSONObject(jsonText)

            // OpenAI-compatible Chat Completions response.
            root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: root.optString("output_text")
                    .trim()
                    .takeIf { it.isNotBlank() }
                ?: root.optString("text")
                    .trim()
                    .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptAndStore(apiKey: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                getOrCreateSecretKey()
            )

            val encrypted = cipher.doFinal(
                apiKey.toByteArray(StandardCharsets.UTF_8)
            )

            prefs.edit()
                .putString(
                    KEY_API,
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
                )
                .putString(
                    KEY_IV,
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                )
                .apply()
        } catch (_: Exception) {
            // Do not write plaintext API keys.
            prefs.edit().remove(KEY_API).remove(KEY_IV).apply()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            "AES",
            KEYSTORE
        )

        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    android.security.keystore.KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()
        )

        return generator.generateKey()
    }
}
