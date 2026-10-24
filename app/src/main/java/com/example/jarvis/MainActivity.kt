package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    private val audioRequest = 101
    private val overlayRequest = 202
    private val contactRequest = 303

    private val manager by lazy {
        JarvisCommandManager(this)
    }

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
            text = "JARVIS ULTIMATE 3.6"
            textSize = 27f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            text = "Jarvis Ready 😎"
            textSize = 18f
            setPadding(0, 25, 0, 25)
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
            layout.addView(Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        // FLOATING EMOJI JARVIS

        addButton("😎 SHOW FLOATING JARVIS") {
            startFloatingJarvis()
        }

        addButton("❌ HIDE FLOATING JARVIS") {

            stopService(
                Intent(
                    this,
                    JarvisFloatingService::class.java
                )
            )

            status.text = "Floating Jarvis बंद कर दिया गया"
        }

        // VOICE CONTROL

        addButton("RUN COMMAND") {
            runCommand(command.text.toString())
        }

        addButton("SPEAK HINDI") {
            listen()
        }

        addButton("START HEY JARVIS") {
            startJarvisVoice()
        }

        addButton("STOP LISTENING") {

            val intent = Intent(
                this,
                JarvisVoiceService::class.java
            ).apply {
                action = JarvisVoiceService.SLEEP
            }

            try {
                startService(intent)
            } catch (_: Exception) {
                stopService(
                    Intent(
                        this,
                        JarvisVoiceService::class.java
                    )
                )
            }

            say("Jarvis Listening बंद कर दी गई है")
        }

        // CONTACT PERMISSIONS

        addButton("📞 ENABLE CONTACT CALLING") {
            checkContactPermissions()
        }

        // ACCESSIBILITY CONTROL

        addButton("ENABLE MOBILE CONTROL") {
            openSettings(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
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
            global(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
        }

        addButton("QUICK SETTINGS") {
            global(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            )
        }

        // SYSTEM SETTINGS

        addButton("WIFI SETTINGS") {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }

        addButton("BLUETOOTH SETTINGS") {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

        addButton("PHONE SETTINGS") {
            openSettings(Settings.ACTION_SETTINGS)
        }

        addButton("DISPLAY SETTINGS") {
            openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }

        addButton("SOUND SETTINGS") {
            openSettings(Settings.ACTION_SOUND_SETTINGS)
        }

        addButton("BATTERY SETTINGS") {
            openSettings(
                Settings.ACTION_BATTERY_SAVER_SETTINGS
            )
        }

        setContentView(scrollView)

        checkAudioPermission()
    }

    // CONTACT AND PHONE PERMISSIONS

    private fun checkContactPermissions(): Boolean {

        val missing = mutableListOf<String>()

        if (
            checkSelfPermission(
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.READ_CONTACTS)
        }

        if (
            checkSelfPermission(
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.CALL_PHONE)
        }

        if (missing.isEmpty()) {

            say("Contacts और Phone Call की अनुमति पहले से चालू है")
            return true
        }

        requestPermissions(
            missing.toTypedArray(),
            contactRequest
        )

        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {

            contactRequest -> {

                val contactsAllowed =
                    checkSelfPermission(
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                val phoneAllowed =
                    checkSelfPermission(
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED

                when {

                    contactsAllowed && phoneAllowed -> {
                        say("Jarvis Contact Calling तैयार है")
                    }

                    contactsAllowed -> {
                        say("Contacts की अनुमति मिल गई है। कॉल लगाने के लिए Phone Permission भी दें")
                    }

                    else -> {
                        say("Contact खोजने के लिए Contacts Permission जरूरी है")
                    }
                }
            }

            audioRequest -> {

                val allowed =
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED

                if (allowed) {
                    status.text = "माइक्रोफोन की अनुमति मिल गई"
                } else {
                    status.text = "माइक्रोफोन की अनुमति नहीं मिली"
                }
            }
        }
    }

    // FLOATING BUBBLE PERMISSION

    private fun startFloatingJarvis() {

        if (!Settings.canDrawOverlays(this)) {

            status.text =
                "पहले Display over other apps अनुमति चालू करें"

            try {

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )

                startActivityForResult(
                    intent,
                    overlayRequest
                )

            } catch (_: Exception) {

                say("Floating Window की सेटिंग नहीं खुल सकी")
            }

            return
        }

        try {

            startService(
                Intent(
                    this,
                    JarvisFloatingService::class.java
                )
            )

            status.text = "Floating Jarvis शुरू हो गया 😎"

        } catch (_: Exception) {

            say("Floating Jarvis शुरू नहीं हो सका")
        }
    }

    // AUDIO PERMISSION

    private fun checkAudioPermission(): Boolean {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            audioRequest
        )

        return false
    }

    // START HEY JARVIS

    private fun startJarvisVoice() {

        if (!checkAudioPermission()) {

            say("पहले माइक्रोफोन की अनुमति दें")
            return
        }

        try {

            val intent = Intent(
                this,
                JarvisVoiceService::class.java
            )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

            status.text =
                "Jarvis Voice Service शुरू हो रही है"

        } catch (_: Exception) {

            say("Voice Service शुरू नहीं हो सकी")
        }
    }

    // TEXT TO SPEECH

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

    // SPEECH RECOGNITION

    private fun listen() {

        if (!checkAudioPermission()) return

        try {

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
                )
            }

            startActivityForResult(
                intent,
                speechRequest
            )

        } catch (_: Exception) {

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

        if (requestCode == overlayRequest) {

            if (Settings.canDrawOverlays(this)) {

                startFloatingJarvis()

            } else {

                status.text =
                    "Floating Jarvis के लिए अनुमति जरूरी है"
            }

            return
        }

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

    // MAIN COMMAND MANAGER

    private fun runCommand(raw: String) {

        val text = raw.trim()

        if (text.isBlank()) {

            say("कृपया कमांड दें")
            return
        }

        try {

            val result = manager.execute(text)

            say(result)

        } catch (_: Exception) {

            say("कमांड चलाने में समस्या आई")
        }
    }

    // GLOBAL MOBILE CONTROL

    private fun global(action: Int) {

        val service = JarvisAccessibility.active

        if (service == null) {

            say("पहले Jarvis Accessibility चालू करें")
            return
        }

        if (service.performGlobalAction(action)) {

            say("जी सर, कमांड पूरी हुई"

            )

        } else {

            say("यह एक्शन अभी उपलब्ध नहीं है")
        }
    }

    // SETTINGS

    private fun openSettings(action: String) {

        try {

            startActivity(Intent(action))

            say("सेटिंग खोल रहा हूँ")

        } catch (_: Exception) {

            say("सेटिंग नहीं खुल सकी")
        }
    }

    override fun onDestroy() {

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}
