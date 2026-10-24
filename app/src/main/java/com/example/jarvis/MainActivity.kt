package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var command: EditText
    private lateinit var clock: TextView
    private lateinit var micButton: Button

    private var tts: TextToSpeech? = null

    private val speechRequest = 42
    private val audioRequest = 101
    private val overlayRequest = 202
    private val contactRequest = 303

    private val cyan = Color.rgb(44, 204, 255)
    private val blue = Color.rgb(15, 95, 180)
    private val backgroundColor = Color.rgb(4, 10, 24)
    private val panelColor = Color.rgb(10, 23, 43)
    private val white = Color.rgb(230, 245, 255)
    private val muted = Color.rgb(139, 174, 202)

    private val manager by lazy {
        JarvisCommandManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        tts = TextToSpeech(this, this)

        buildDashboard()
        checkAudioPermission()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }

        scroll.addView(page)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelBackground()
        }

        val logo = TextView(this).apply {
            text = "◈"
            textSize = 34f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }

        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleBox.addView(label("J.A.R.V.I.S", 22f, cyan, true))
        titleBox.addView(label("PERSONAL AI SYSTEM", 10f, muted, false))

        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("ONLINE • 3.6", 11f, cyan, true))
        page.addView(header)

        page.addView(space(16))

        // CLOCK AND STATUS
        val topCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val clockCard = card()
        clock = label("--:--", 30f, cyan, true)
        clockCard.addView(clock)
        clockCard.addView(label("LOCAL DEVICE TIME", 10f, muted, false))
        clockCard.addView(space(8))
        clockCard.addView(label("◉  JARVIS READY", 12f, white, true))
        topCards.addView(
            clockCard,
            LinearLayout.LayoutParams(0, dp(130), 1f).apply {
                marginEnd = dp(8)
            }
        )

        val statusCard = card()
        statusCard.addView(label("SYSTEM STATUS", 14f, cyan, true))
        statusCard.addView(space(8))
        statusCard.addView(label("VOICE  •  Hindi", 12f, white, false))
        statusCard.addView(label("CONTROL  •  Android", 12f, white, false))
        statusCard.addView(label("MODE  •  Assistant", 12f, white, false))
        topCards.addView(
            statusCard,
            LinearLayout.LayoutParams(0, dp(130), 1f)
        )

        page.addView(topCards)
        page.addView(space(16))

        // CENTRAL JARVIS CORE
        val core = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(10, 38, 72),
                    Color.rgb(5, 13, 29)
                )
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), cyan)
            }
        }

        val coreSymbol = TextView(this).apply {
            text = "◉"
            textSize = 76f
            gravity = Gravity.CENTER
            setTextColor(cyan)
        }

        core.addView(coreSymbol)
        core.addView(label("JARVIS CORE", 23f, white, true))
        core.addView(space(4))
        core.addView(
            label(
                "YOUR PERSONAL VOICE ASSISTANT",
                10f,
                muted,
                false
            )
        )
        core.addView(space(14))

        micButton = Button(this).apply {
            text = "🎙  START HEY JARVIS"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = buttonBackground()
            setOnClickListener { startJarvisVoice() }
        }

        core.addView(
            micButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            )
        )

        page.addView(core)
        page.addView(space(16))

        // STATUS MESSAGE
        val statusCard = card()
        status = label(
            "JARVIS तैयार है। कमांड बोलें या नीचे लिखें।",
            14f,
            white,
            false
        )
        statusCard.addView(status)
        page.addView(statusCard)

        page.addView(space(16))
        page.addView(label("QUICK APPS", 17f, cyan, true))
        page.addView(space(8))

        val apps = listOf(
            "▶  YouTube" to "youtube",
            "◎  Chrome" to "chrome",
            "●  WhatsApp" to "whatsapp",
            "◎  Instagram" to "instagram",
            "⚙  Settings" to "settings",
            "▣  Camera" to "camera",
            "▧  Gallery" to "gallery"
        )

        val appGrid = GridLayout(this).apply {
            columnCount = 2
        }

        apps.forEach { item ->
            val b = Button(this).apply {
                text = item.first
                isAllCaps = false
                textSize = 13f
                setTextColor(white)
                background = panelBackground()
                setOnClickListener {
                    runCommand(
                        when (item.second) {
                            "youtube" -> "YouTube खोलो"
                            "chrome" -> "Chrome खोलो"
                            "whatsapp" -> "WhatsApp खोलो"
                            "instagram" -> "Instagram खोलो"
                            "settings" -> "Settings खोलो"
                            "camera" -> "Camera खोलो"
                            else -> "Gallery खोलो"
                        }
                    )
                }
            }

            appGrid.addView(
                b,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(54)
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                    )
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            )
        }

        page.addView(appGrid)
        page.addView(space(16))

        // COMMAND INPUT
        page.addView(label("VOICE COMMAND CONSOLE", 17f, cyan, true))
        page.addView(space(8))

        command = EditText(this).apply {
            hint = "उदाहरण: YouTube खोलो"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            singleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
        }

        page.addView(
            command,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
            )
        )

        page.addView(space(8))

        addActionButton(page, "▶  RUN COMMAND") {
            runCommand(command.text.toString())
        }

        addActionButton(page, "🎤  SPEAK A COMMAND") {
            listen()
        }

        page.addView(space(12))
        page.addView(label("DEVICE CONTROL", 17f, cyan, true))
        page.addView(space(8))

        addActionButton(page, "📞 ENABLE CONTACT CALLING") {
            checkContactPermissions()
        }

        addActionButton(page, "📲 ANSWER INCOMING CALL") {
            val result = try {
                JarvisIncomingCall.answer(this)
            } catch (_: Exception) {
                "कॉल उठाने की सुविधा उपलब्ध नहीं हुई। Android अनुमति और फोन की संगतता जाँचें।"
            }
            say(result)
        }

        addActionButton(page, "🔓 ENABLE ACCESSIBILITY CONTROL") {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        addActionButton(page, "😎 SHOW FLOATING JARVIS") {
            startFloatingJarvis()
        }

        addActionButton(page, "🙈 HIDE FLOATING JARVIS") {
            stopService(
                Intent(this, JarvisFloatingService::class.java)
            )
            say("Floating Jarvis बंद कर दिया गया")
        }

        addActionButton(page, "⏹ STOP LISTENING") {
            try {
                startService(
                    Intent(this, JarvisVoiceService::class.java).apply {
                        action = JarvisVoiceService.SLEEP
                    }
                )
                say("Jarvis सुनना बंद कर रहा है")
            } catch (_: Exception) {
                say("Voice Service बंद नहीं हो सकी")
            }
        }

        page.addView(space(12f))
        page.addView(
            label(
                "J.A.R.V.I.S  •  VOICE  •  APP CONTROL  •  AUTOMATION",
                10f,
                muted,
                false
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        setContentView(root)
        updateClock()
    }

    private fun updateClock() {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        clock.text = format.format(Date())
        clock.postDelayed({ updateClock() }, 30_000)
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelBackground()
        }
    }

    private fun space(value: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(value))
        }
    }

    private fun addActionButton(
        parent: LinearLayout,
        title: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(white)
            background = buttonBackground()
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply {
                bottomMargin = dp(7)
            }
        )
    }

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), blue)
        }
    }

    private fun buttonBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(10, 73, 124),
                Color.rgb(7, 36, 72)
            )
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cyan)
        }
    }

    private fun inputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.rgb(7, 17, 34))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cyan)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // CONTACT PERMISSIONS

    private fun checkContactPermissions(): Boolean {
        val missing = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.READ_CONTACTS)
        }

        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CALL_PHONE)
        }

        if (missing.isEmpty()) {
            say("Contacts और Phone की अनुमति पहले से चालू है")
            return true
        }

        requestPermissions(missing.toTypedArray(), contactRequest)
        return false
    }

    // AUDIO PERMISSION

    private fun checkAudioPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            audioRequest
        )
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        when (requestCode) {
            contactRequest -> {
                val contacts = checkSelfPermission(
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED

                val phone = checkSelfPermission(
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

                say(
                    when {
                        contacts && phone ->
                            "Jarvis Contact Calling तैयार है"
                        contacts ->
                            "Contacts अनुमति मिल गई। Phone अनुमति भी दें।"
                        else ->
                            "Contact खोजने के लिए Contacts अनुमति जरूरी है"
                    }
                )
            }

            audioRequest -> {
                val allowed = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

                status.text = if (allowed) {
                    "माइक्रोफोन की अनुमति मिल गई"
                } else {
                    "माइक्रोफोन की अनुमति नहीं मिली"
                }
            }
        }
    }

    // FLOATING BUBBLE

    private fun startFloatingJarvis() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "पहले Display over other apps अनुमति चालू करें"

            try {
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ),
                    overlayRequest
                )
            } catch (_: Exception) {
                say("Floating Window की सेटिंग नहीं खुल सकी")
            }
            return
        }

        try {
            startService(
                Intent(this, JarvisFloatingService::class.java)
            )
            say("Floating Jarvis शुरू हो गया")
        } catch (_: Exception) {
            say("Floating Jarvis शुरू नहीं हो सका")
        }
    }

    // VOICE SERVICE

    private fun startJarvisVoice() {
        if (!checkAudioPermission()) {
            say("पहले माइक्रोफोन की अनुमति दें")
            return
        }

        try {
            val intent = Intent(this, JarvisVoiceService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            status.text = "Jarvis Voice Service शुरू हो रही है"
        } catch (_: Exception) {
            say("Voice Service शुरू नहीं हो सकी")
        }
    }

    // TEXT TO SPEECH

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status.text = "Hindi voice उपलब्ध नहीं है; TTS भाषा सेट करें"
            }
        }
    }

    private fun say(message: String) {
        status.text = message
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_dashboard"
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            }

            startActivityForResult(intent, speechRequest)
        } catch (_: Exception) {
            say("Voice Recognition उपलब्ध नहीं है")
        }
    }

    @Deprecated("Legacy activity callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == overlayRequest) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingJarvis()
            } else {
                say("Floating Jarvis के लिए अनुमति जरूरी है")
            }
            return
        }

        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull() ?: return

            command.setText(spoken)
            runCommand(spoken)
        }
    }

    // COMMAND MANAGER

    private fun runCommand(raw: String) {
        if (raw.isBlank()) {
            say("कृपया कमांड दें")
            return
        }

        try {
            say(manager.execute(raw))
        } catch (_: Exception) {
            say("कमांड चलाने में समस्या आई")
        }
    }

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
