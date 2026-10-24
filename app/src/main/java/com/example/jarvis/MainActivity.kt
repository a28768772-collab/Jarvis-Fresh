package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity(),
    TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var command: EditText

    private var tts: TextToSpeech? = null

    private val speechRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 55, 30, 25)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        layout.addView(TextView(this).apply {
            text = "JARVIS ULTIMATE"
            textSize = 28f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            text = "Jarvis Ready"
            textSize = 18f
            setPadding(0, 30, 0, 30)
        }

        layout.addView(status)

        command = EditText(this).apply {
            hint = "Jarvis को कमांड दें"
            setSingleLine(true)
        }

        layout.addView(command)

        fun addButton(
            title: String,
            action: () -> Unit
        ) {
            val button = Button(this)
            button.text = title
            button.setOnClickListener { action() }
            layout.addView(button)
        }

        addButton("Run Command") {
            runCommand(command.text.toString())
        }

        addButton("Speak Hindi") {
            listen()
        }

        addButton("Enable Mobile Control") {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        setContentView(layout)

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun say(message: String) {
        status.text = message

        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis"
        )
    }

    private fun listen() {
        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "hi-IN"
            )

            startActivityForResult(
                intent,
                speechRequest
            )

        } catch (e: Exception) {
            say("Voice Recognition उपलब्ध नहीं है")
        }
    }

    @Deprecated("Legacy speech callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == speechRequest &&
            resultCode == RESULT_OK
        ) {
            val spoken = data
                ?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                ?.firstOrNull() ?: return

            command.setText(spoken)
            runCommand(spoken)
        }
    }

    private fun runCommand(raw: String) {

        val text = raw.trim().lowercase(Locale.ROOT)

        val service = JarvisAccessibility.active

        when {

            text.contains("यूट्यूब") ||
            text.contains("youtube") -> {
                openApp(
                    "com.google.android.youtube",
                    "https://youtube.com"
                )
            }

            text.contains("क्रोम") ||
            text.contains("chrome") -> {
                openApp(
                    "com.android.chrome",
                    "https://google.com"
                )
            }

            text.contains("व्हाट्सएप") ||
            text.contains("whatsapp") -> {
                openApp(
                    "com.whatsapp",
                    "https://whatsapp.com"
                )
            }

            text.contains("सेटिंग") -> {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                )
                say("सेटिंग खोल रहा हूँ")
            }

            text.contains("होम") -> {
                if (service != null) {
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService.GLOBAL_ACTION_HOME
                    )
                    say("होम खोल दिया")
                } else permission()
            }

            text.contains("बैक") ||
            text.contains("पीछे") -> {
                if (service != null) {
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService.GLOBAL_ACTION_BACK
                    )
                    say("वापस जा रहा हूँ")
                } else permission()
            }

            text.contains("नीचे स्क्रॉल") -> {
                if (service != null) {
                    service.scroll(false)
                    say("नीचे स्क्रॉल किया")
                } else permission()
            }

            text.contains("ऊपर स्क्रॉल") -> {
                if (service != null) {
                    service.scroll(true)
                    say("ऊपर स्क्रॉल किया")
                } else permission()
            }

            text.startsWith("क्लिक ") ||
            text.startsWith("टच ") -> {

                if (service == null) {
                    permission()
                } else {
                    val label = text.substringAfter(' ').trim()

                    val result = service.tapLabel(label)

                    if (result) {
                        say("क्लिक करने की कोशिश की")
                    } else {
                        say("बटन नहीं मिला")
                    }
                }
            }

            else -> {
                say("यह कमांड अभी उपलब्ध नहीं है")
            }
        }
    }

    private fun permission() {
        say("पहले Jarvis Accessibility चालू करें")
    }

    private fun openApp(
        packageName: String,
        url: String
    ) {
        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        startActivity(
            intent ?: Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )

        say("जी सर, खोल रहा हूँ")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
