
package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.accessibilityservice.AccessibilityService
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
            setPadding(30, 40, 30, 30)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scrollView = ScrollView(this)
        scrollView.addView(layout)

        layout.addView(TextView(this).apply {
            text = "JARVIS ULTIMATE 3.0"
            textSize = 27f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            text = "Jarvis Ready"
            textSize = 18f
            setPadding(0, 25, 0, 25)
        }
        layout.addView(status)

        command = EditText(this).apply {
            hint = "Jarvis को कमांड दें"
            setSingleLine(true)
        }
        layout.addView(command)

        fun addButton(title: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = title
                setOnClickListener { action() }
            })
        }

        addButton("RUN COMMAND") {
            runCommand(command.text.toString())
        }

        addButton("SPEAK HINDI") {
            listen()
        }

        addButton("ENABLE MOBILE CONTROL") {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        addButton("HOME") {
            global(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        addButton("BACK") {
            global(AccessibilityService.GLOBAL_ACTION_BACK)
        }

        addButton("RECENT APPS") {
            global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }

        addButton("NOTIFICATIONS") {
            global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }

        addButton("QUICK SETTINGS") {
            global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }

        addButton("WIFI SETTINGS") {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }

        addButton("BLUETOOTH SETTINGS") {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

        setContentView(scrollView)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 1
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 1
            )
            return
        }

        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }

            startActivityForResult(intent, speechRequest)
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
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val spoken = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return

            command.setText(spoken)
            runCommand(spoken)
        }
    }

    private fun global(action: Int) {
        val service = JarvisAccessibility.active

        if (service == null) {
            say("पहले Jarvis Accessibility चालू करें")
            return
        }

        if (service.performGlobalAction(action)) {
            say("जी सर, कमांड पूरी हुई")
        } else {
            say("यह एक्शन अभी उपलब्ध नहीं है")
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
            say("सेटिंग खोल रहा हूँ")
        } catch (e: Exception) {
            say("सेटिंग नहीं खुल सकी")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )
            say("जी सर, खोल रहा हूँ")
        } catch (e: Exception) {
            say("लिंक नहीं खुल सका")
        }
    }

    private fun openApp(pkg: String, fallback: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)

        try {
            if (intent != null) {
                startActivity(intent)
                say("जी सर, खोल रहा हूँ")
            } else {
                openUrl(fallback)
            }
        } catch (e: Exception) {
            openUrl(fallback)
        }
    }

    private fun runCommand(raw: String) {
        val text = raw.trim().lowercase(Locale.ROOT)

        if (text.isBlank()) {
            say("कृपया कमांड दें")
            return
        }

        val service = JarvisAccessibility.active

        when {
            text.contains("यूट्यूब") ||
            text.contains("youtube") ->
                openApp(
                    "com.google.android.youtube",
                    "https://youtube.com"
                )

            text.contains("व्हाट्सएप") ||
            text.contains("whatsapp") ->
                openApp(
                    "com.whatsapp",
                    "https://whatsapp.com"
                )

            text.contains("क्रोम") ||
            text.contains("chrome") ->
                openApp(
                    "com.android.chrome",
                    "https://google.com"
                )

            text.contains("वाईफाई") ||
            text.contains("wifi") ||
            text.contains("wi-fi") ->
                openSettings(Settings.ACTION_WIFI_SETTINGS)

            text.contains("ब्लूटूथ") ||
            text.contains("bluetooth") ->
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)

            text.contains("सेटिंग") ||
            text.contains("settings") ->
                openSettings(Settings.ACTION_SETTINGS)

            text.contains("नोटिफिकेशन") ||
            text.contains("notification") ->
                global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

            text.contains("क्विक सेटिंग") ||
            text.contains("quick settings") ->
                global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

            text.contains("रीसेंट") ||
            text.contains("recent") ->
                global(AccessibilityService.GLOBAL_ACTION_RECENTS)

            text.contains("होम") ||
            text == "home" ->
                global(AccessibilityService.GLOBAL_ACTION_HOME)

            text.contains("बैक") ||
            text.contains("पीछे") ||
            text == "back" ->
                global(AccessibilityService.GLOBAL_ACTION_BACK)

            text.contains("नीचे स्क्रॉल") ||
            text.contains("scroll down") -> {
                if (service == null) {
                    permission()
                } else {
                    service.scroll(false)
                    say("नीचे स्क्रॉल किया")
                }
            }

            text.contains("ऊपर स्क्रॉल") ||
            text.contains("scroll up") -> {
                if (service == null) {
                    permission()
                } else {
                    service.scroll(true)
                    say("ऊपर स्क्रॉल किया")
                }
            }

            text.startsWith("क्लिक ") ||
            text.startsWith("टच ") ||
            text.startsWith("click ") -> {
                if (service == null) {
                    permission()
                } else {
                    val label = text.substringAfter(' ').trim()
                    if (service.tapLabel(label)) {
                        say("क्लिक करने की कोशिश की")
                    } else {
                        say("बटन नहीं मिला")
                    }
                }
            }

            text.startsWith("सर्च ") ||
            text.startsWith("search ") -> {
                val query = text.substringAfter(' ').trim()
                if (query.isNotBlank()) {
                    openUrl(
                        "https://www.google.com/search?q=" +
                        Uri.encode(query)
                    )
                }
            }

            text.startsWith("वेबसाइट ") ||
            text.startsWith("website ") -> {
                val site = text.substringAfter(' ').trim()
                if (site.matches(
                    Regex("[a-z0-9.-]+\\.[a-z]{2,}(/[^ ]*)?")
                )) {
                    openUrl("https://$site")
                } else {
                    say("सही वेबसाइट का नाम बताइए")
                }
            }

            else -> say("यह कमांड अभी उपलब्ध नहीं है")
        }
    }

    private fun permission() {
        say("पहले Jarvis Accessibility चालू करें")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
