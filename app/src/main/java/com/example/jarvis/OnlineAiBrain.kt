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

        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_MODEL = "model"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API = "api_blob"
        private const val KEY_IV = "api_iv"

        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_online_ai_key"

        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

        private const val GEMINI_MODEL =
            "gemini-3.8-flash"

        private const val OLD_OPENAI_ENDPOINT =
            "https://api.openai.com/v1/chat/completions"
    }

    private val prefs =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false) &&
            !getApiKey().isNullOrBlank()
    }

    fun getEndpoint(): String {
        val saved = prefs.getString(KEY_ENDPOINT, null)
            ?.trim()
            .orEmpty()

        /*
         * पुराने OpenAI settings को automatically Gemini पर बदलें।
         */
        if (
            saved.isBlank() ||
            saved == OLD_OPENAI_ENDPOINT ||
            saved == "https://api.openai.com/v1/chat/completions"
        ) {
            return GEMINI_ENDPOINT
        }

        return saved
    }

    fun getModel(): String {
        val saved = prefs.getString(KEY_MODEL, null)
            ?.trim()
            .orEmpty()

        /*
         * पुराने OpenAI model को Gemini model पर बदलें।
         */
        if (
            saved.isBlank() ||
            saved == "gpt-4o-mini"
        ) {
            return GEMINI_MODEL
        }

        return saved
    }

    fun setConfig(
        endpoint: String,
        model: String,
        apiKey: String,
        enabled: Boolean
    ) {
        prefs.edit()
            .putString(
                KEY_ENDPOINT,
                if (
                    endpoint.trim().isBlank() ||
                    endpoint.trim() == OLD_OPENAI_ENDPOINT
                ) {
                    GEMINI_ENDPOINT
                } else {
                    endpoint.trim()
                }
            )
            .putString(
                KEY_MODEL,
                if (
                    model.trim().isBlank() ||
                    model.trim() == "gpt-4o-mini"
                ) {
                    GEMINI_MODEL
                } else {
                    model.trim()
                }
            )
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (apiKey.isNotBlank()) {
            encryptAndStore(apiKey.trim())
        }
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    fun getApiKey(): String? {
        val blob =
            prefs.getString(KEY_API, null)
                ?: return null

        val ivText =
            prefs.getString(KEY_IV, null)
                ?: return null

        return try {
            val iv =
                Base64.decode(
                    ivText,
                    Base64.NO_WRAP
                )

            val encrypted =
                Base64.decode(
                    blob,
                    Base64.NO_WRAP
                )

            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(
                    128,
                    iv
                )
            )

            String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )

        } catch (_: Exception) {
            null
        }
    }

    fun askBlocking(userText: String): String? {

        if (
            !isEnabled() ||
            userText.isBlank()
        ) {
            return null
        }

        val task =
            Callable {
                request(userText)
            }

        val executor =
            Executors.newSingleThreadExecutor()

        return try {

            executor
                .submit(task)
                .get(
                    25,
                    TimeUnit.SECONDS
                )

        } catch (_: Exception) {

            null

        } finally {

            executor.shutdownNow()
        }
    }

    fun testConnection(): String {

        if (!isEnabled()) {
            return "पहले Gemini API Key डालकर Online AI चालू करें"
        }

        val result =
            askBlocking(
                "Reply with exactly: JARVIS ONLINE"
            )

        return if (
            result.isNullOrBlank()
        ) {

            "Gemini Online AI test failed. API key, model और internet check करें."

        } else {

            "Gemini Online AI connected: $result"
        }
    }

    private fun request(
        userText: String
    ): String? {

        val apiKey =
            getApiKey()
                ?: return null

        val endpoint =
            getEndpoint()

        val model =
            getModel()

        val body =
            JSONObject().apply {

                put(
                    "model",
                    model
                )

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
                                        "Be helpful, direct and polite. " +
                                        "Do not claim that you performed a phone action unless the Android app actually performed it."
                                )
                            }
                        )

                        put(
                            JSONObject().apply {

                                put(
                                    "role",
                                    "user"
                                )

                                put(
                                    "content",
                                    userText
                                )
                            }
                        )
                    }
                )

                put(
                    "temperature",
                    0.5
                )
            }
                .toString()

        var connection:
            HttpURLConnection? = null

        return try {

            connection =
                (
                    URL(endpoint)
                        .openConnection()
                        as HttpURLConnection
                    ).apply {

                    requestMethod = "POST"

                    connectTimeout = 10000

                    readTimeout = 20000

                    doOutput = true

                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    setRequestProperty(
                        "Authorization",
                        "Bearer $apiKey"
                    )
                }

            connection
                .outputStream
                .use { output ->

                    output.write(
                        body.toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.use { input ->

                    BufferedReader(
                        InputStreamReader(
                            input,
                            StandardCharsets.UTF_8
                        )
                    ).use { reader ->

                        reader.readText()
                    }

                }.orEmpty()

            if (code !in 200..299) {
                return null
            }

            parseAnswer(
                responseText
            )

        } catch (_: Exception) {

            null

        } finally {

            connection?.disconnect()
        }
    }

    private fun parseAnswer(
        jsonText: String
    ): String? {

        return try {

            val root =
                JSONObject(jsonText)

            root
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun encryptAndStore(
        apiKey: String
    ) {

        try {

            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                )

            cipher.init(
                Cipher.ENCRYPT_MODE,
                getOrCreateSecretKey()
            )

            val encrypted =
                cipher.doFinal(
                    apiKey.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

            prefs.edit()
                .putString(
                    KEY_API,
                    Base64.encodeToString(
                        encrypted,
                        Base64.NO_WRAP
                    )
                )
                .putString(
                    KEY_IV,
                    Base64.encodeToString(
                        cipher.iv,
                        Base64.NO_WRAP
                    )
                )
                .apply()

        } catch (_: Exception) {

            prefs.edit()
                .remove(KEY_API)
                .remove(KEY_IV)
                .apply()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {

        val keyStore =
            KeyStore
                .getInstance(KEYSTORE)
                .apply {
                    load(null)
                }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            )

        if (existing is SecretKey) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
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
