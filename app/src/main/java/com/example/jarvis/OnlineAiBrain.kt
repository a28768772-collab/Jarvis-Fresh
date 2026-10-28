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

class OnlineAiBrain(private val context: Context) {

    companion object {
        private const val PREFS = "jarvis_online_ai"
        private const val KEY_MODEL = "model"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API = "api_blob"
        private const val KEY_IV = "api_iv"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_online_ai_key"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        // Current stable Gemini Flash model. Change this in settings if needed.
        private const val DEFAULT_MODEL = "gemini-3.8-flash"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var lastError = ""

    fun isEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) && !getApiKey().isNullOrBlank()

    // Kept for compatibility with the existing settings screen/code.
    fun getEndpoint(): String = "$GEMINI_BASE_URL/models"

    fun getModel(): String {
        val saved = prefs.getString(KEY_MODEL, null)?.trim().orEmpty()
        return saved.removePrefix("models/").ifBlank { DEFAULT_MODEL }
    }

    /** Returns true only when the supplied key was stored successfully. */
    fun setConfig(endpoint: String, model: String, apiKey: String, enabled: Boolean): Boolean {
        val finalModel = model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        val existingKey = getApiKey()
        val keyToStore = apiKey.trim().ifBlank { existingKey.orEmpty() }
        if (keyToStore.isBlank()) {
            prefs.edit().putString(KEY_MODEL, finalModel).putBoolean(KEY_ENABLED, false).apply()
            return false
        }
        if (apiKey.isNotBlank() && !encryptAndStore(keyToStore)) return false
        prefs.edit()
            .putString(KEY_MODEL, finalModel)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        return true
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
        lastError = ""
    }

    fun getApiKey(): String? {
        val blob = prefs.getString(KEY_API, null) ?: return null
        val ivText = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Synchronous request used by the existing command manager. Call it off the UI thread. */
    fun askBlocking(userText: String): String? {
        if (!isEnabled() || userText.isBlank()) {
            lastError = if (getApiKey().isNullOrBlank()) "Gemini API Key नहीं मिली। Settings में key डालकर Save करें।" else "Online AI बंद है। Settings में इसे चालू करें।"
            return null
        }
        lastError = ""
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { request(userText) }).get(35, TimeUnit.SECONDS)
        } catch (e: Exception) {
            lastError = "Connection error: ${e.cause?.message ?: e.message ?: "timeout"}"
            null
        } finally {
            executor.shutdownNow()
        }
    }

    fun testConnection(): String {
        if (!isEnabled()) return "पहले Gemini API Key डालें, Save करें और Online AI चालू रखें।"
        val result = askBlocking("Reply with exactly these words: JARVIS ONLINE")
        return if (!result.isNullOrBlank()) {
            "Gemini connected successfully: $result"
        } else {
            "Gemini connection failed:\n${lastError.ifBlank { "Unknown error" }}"
        }
    }

    private fun request(userText: String): String? {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            lastError = "API Key खाली है। Settings में key पेस्ट करें।"
            return null
        }
        val model = getModel()
        val endpoint = "$GEMINI_BASE_URL/models/$model:generateContent"
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text",
                    "You are JARVIS, a concise Hindi-speaking Android phone assistant. " +
                    "Answer naturally in Hindi unless another language is requested. " +
                    "Be helpful and polite. Never claim that you performed a phone action unless the Android app actually did it.")
            )))
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText)))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.5).put("maxOutputTokens", 1024))
        }.toString()

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                lastError = formatApiError(code, responseText)
                return null
            }
            val answer = parseAnswer(responseText)
            if (answer.isNullOrBlank()) {
                lastError = "Gemini ने खाली जवाब दिया। Model का नाम और API key की access जाँचें।"
                null
            } else answer
        } catch (e: Exception) {
            lastError = "Network error: ${e.message ?: "Internet connection जाँचें"}"
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAnswer(jsonText: String): String? = try {
        val root = JSONObject(jsonText)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val result = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    if (result.isNotEmpty()) result.append('\n')
                    result.append(text.trim())
                }
            }
        }
        result.toString().trim().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        lastError = "Response parse error: ${e.message ?: "invalid response"}"
        null
    }

    private fun formatApiError(code: Int, responseText: String): String {
        val message = try {
            JSONObject(responseText).optJSONObject("error")?.optString("message")
                ?.trim()?.takeIf { it.isNotBlank() } ?: responseText
        } catch (_: Exception) { responseText }
        val clean = message.replace(Regex("\\s+"), " ").trim().take(700)
        val hint = when (code) {
            400 -> " Request/model की जाँच करें."
            401, 403 -> " API key या उसके permissions की जाँच करें."
            404 -> " Model का नाम उपलब्ध नहीं है; Settings में gemini-3.8-flash रखें."
            429 -> " API quota/rate limit पूरी हो सकती है."
            else -> ""
        }
        return "HTTP $code: $clean$hint"
    }

    private fun encryptAndStore(apiKey: String): Boolean = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString(KEY_API, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    } catch (_: Exception) {
        false
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
