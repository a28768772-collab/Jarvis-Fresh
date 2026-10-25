package com.example.jarvis

import android.Manifest
import android.app.Activity
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
    private val answerRequest = 304

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

        buildDashboard()

        tts = TextToSpeech(this, this)

        checkAudioPermission()
    }

    private fun buildDashboard() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(24)
            )
        }

        scroll.addView(page)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // HEADER

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background = panelBackground()
        }

        val logo = TextView(this).apply {
            text = "◈"
            textSize = 34f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleBox.addView(
            label("J.A.R.V.I.S", 22f, cyan, true)
        )

        titleBox.addView(
            label("PERSONAL AI SYSTEM", 10f, muted, false)
        )

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(
            label("AI • ONLINE", 11f, cyan, true)
        )

        page.addView(header)
        page.addView(space(16))

        // CLOCK AND SYSTEM STATUS

        val topCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val clockCard = card()

        clock = label("--:--", 30f, cyan, true)

        clockCard.addView(clock)

        clockCard.addView(
            label("LOCAL DEVICE TIME", 10f, muted, false)
        )

        clockCard.addView(space(8))

        clockCard.addView(
            label("◉ JARVIS READY", 12f, white, true)
        )

        topCards.addView(
            clockCard,
            LinearLayout.LayoutParams(0, dp(130), 1f).apply {
                marginEnd = dp(8)
            }
        )

        val systemStatusCard = card()

        systemStatusCard.addView(
            label("SYSTEM STATUS", 14f, cyan, true)
        )

        systemStatusCard.addView(space(8))

        systemStatusCard.addView(
            label("VOICE • Hindi", 12f, white, false)
        )

        systemStatusCard.addView(
            label("CONTROL • Android", 12f, white, false)
        )

        systemStatusCard.addView(
            label("MODE • Online + Offline", 12f, white, false)
        )

        topCards.addView(
            systemStatusCard,
            LinearLayout.LayoutParams(0, dp(130), 1f)
        )

        page.addView(topCards)
        page.addView(space(16))

        // ONLINE AI SETTINGS

        page.addView(
            label("ONLINE AI", 17f, cyan, true)
        )

        page.addView(space(8))

        addActionButton(
            page,
            "🌐 OPEN ONLINE AI SETTINGS"
        ) {
            openOnlineAiSettings()
        }

        page.addView(space(16))

        // CENTRAL HOLOGRAM CORE

        val core = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            setPadding(
                dp(12),
                dp(18),
                dp(12),
                dp(18)
            )

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
            text = "◎"
            textSize = 90f
            gravity = Gravity.CENTER
            setTextColor(cyan)
        }

        core.addView(coreSymbol)

        core.addView(
            label("JARVIS CORE", 23f, white, true)
        )

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
            text = "🎙 START HEY JARVIS"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = buttonBackground()

            setOnClickListener {
                startJarvisVoice()
            }
        }

        core.addView(
            micButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        page.addView(core)
        page.addView(space(16))

        // MESSAGE PANEL

        val messageCard = card()

        status = label(
            "JARVIS तैयार है। कमांड बोलें या नीचे लिखें।",
            14f,
            white,
            false
        )

        messageCard.addView(status)
        page.addView(messageCard)

        page.addView(space(16))

        // QUICK APPS

        page.addView(
            label("QUICK APPS", 17f, cyan, true)
        )

        page.addView(space(8))

        val apps = listOf(
            "▶ YouTube" to "YouTube खोलो",
            "◎ Chrome" to "Chrome खोलो",
            "● WhatsApp" to "WhatsApp खोलो",
            "◎ Instagram" to "Instagram खोलो",
            "⚙ Settings" to "Settings खोलो",
            "▣ Camera" to "Camera खोलो",
            "▧ Gallery" to "Gallery खोलो"
        )

        val appGrid = GridLayout(this).apply {
            columnCount = 2
        }

        apps.forEach { item ->

            val appButton = Button(this).apply {
                text = item.first
                isAllCaps = false
                textSize = 13f
                setTextColor(white)
                background = panelBackground()

                setOnClickListener {
                    runCommand(item.second)
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(54)

                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1,
                    1f
                )

                setMargins(
                    dp(3),
                    dp(3),
                    dp(3),
                    dp(3)
                )
            }

            appGrid.addView(appButton, params)
        }

        page.addView(appGrid)
        page.addView(space(16))

        // COMMAND CONSOLE

        page.addView(
            label("VOICE COMMAND CONSOLE", 17f, cyan, true)
        )

        page.addView(space(8))

        command = EditText(this).apply {
            hint = "उदाहरण: YouTube खोलो"

            setHintTextColor(muted)
            setTextColor(white)

            textSize = 15f

            setSingleLine(true)

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background = inputBackground()
        }

        page.addView(
            command,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )

        page.addView(space(8))

        addActionButton(page, "▶ RUN COMMAND") {
            runCommand(command.text.toString())
        }

        addActionButton(page, "🎤 SPEAK A COMMAND") {
            listen()
        }

        page.addView(space(12))

        // DEVICE CONTROL

        page.addView(
            label("DEVICE CONTROL", 17f, cyan, true)
        )

        page.addView(space(8))

        addActionButton(
            page,
            "📞 ENABLE PHONE CONTROL"
        ) {
            checkContactPermissions()
        }

        addActionButton(
            page,
            "📲 ANSWER INCOMING CALL"
        ) {
            if (checkAnswerCallPermission()) {
                answerIncomingCall()
            }
        }

        addActionButton(
            page,
            "🔓 ENABLE ACCESSIBILITY CONTROL"
        ) {
            openSettings(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        }

        addActionButton(
            page,
            "😎 SHOW FLOATING JARVIS"
        ) {
            startFloatingJarvis()
        }

        addActionButton(
            page,
            "🙈 HIDE FLOATING JARVIS"
        ) {
            stopService(
                Intent(
                    this,
                    JarvisFloatingService::class.java
                )
            )

            say("Floating Jarvis बंद कर दिया गया")
        }

        addActionButton(
            page,
            "⏹ STOP LISTENING"
        ) {
            try {
                val intent = Intent(
                    this,
                    JarvisVoiceService::class.java
                ).apply {
                    action = JarvisVoiceService.SLEEP
                }

                startService(intent)

                say("Jarvis सुनना बंद कर रहा है")

            } catch (_: Exception) {
                say("Voice Service बंद नहीं हो सकी")
            }
        }

        page.addView(space(12))

        val footer = label(
            "J.A.R.V.I.S • VOICE • APP CONTROL • AUTOMATION",
            10f,
            muted,
            false
        ).apply {
            gravity = Gravity.CENTER
        }

        page.addView(footer)

        setContentView(root)

        updateClock()
    }

    // ONLINE AI SETTINGS

    private fun openOnlineAiSettings() {
        try {
            startActivity(
                Intent(
                    this,
                    OnlineAiSettingsActivity::class.java
                )
            )
        } catch (_: Exception) {
            say("Online AI Settings अभी उपलब्ध नहीं हैं")
        }
    }

    // CLOCK

    private fun updateClock() {
        if (!::clock.isInitialized) return

        val format = SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        )

        clock.text = format.format(Date())
    }

    override fun onResume() {
        super.onResume()
        updateClock()
    }

    // UI HELPERS

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background = panelBackground()
        }
    }

    private fun space(value: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(value)
            )
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

            setOnClickListener {
                action()
            }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
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

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    // PHONE PERMISSIONS

    private fun checkContactPermissions(): Boolean {

        val missing = mutableListOf<String>()

        if (
            checkSelfPermission(
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(
                Manifest.permission.READ_CONTACTS
            )
        }

        if (
            checkSelfPermission(
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(
                Manifest.permission.CALL_PHONE
            )
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            checkSelfPermission(
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        }

        if (missing.isEmpty()) {
            say("Jarvis Phone Control की अनुमतियाँ चालू हैं")
            return true
        }

        requestPermissions(
            missing.toTypedArray(),
            contactRequest
        )

        return false
    }

    private fun checkAnswerCallPermission(): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            say("इस Android संस्करण पर यह सुविधा उपलब्ध नहीं है")
            return false
        }

        if (
            checkSelfPermission(
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.ANSWER_PHONE_CALLS
            ),
            answerRequest
        )

        return false
    }

    private fun answerIncomingCall() {
        try {
            val result = JarvisIncomingCall.answer(this)
            say(result)
        } catch (_: Exception) {
            say("कॉल उठाने की सुविधा उपलब्ध नहीं हुई")
        }
    }

    private fun checkAudioPermission(): Boolean {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            ),
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

                val answerAllowed =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    checkSelfPermission(
                        Manifest.permission.ANSWER_PHONE_CALLS
                    ) == PackageManager.PERMISSION_GRANTED

                say(
                    when {
                        contactsAllowed &&
                        phoneAllowed &&
                        answerAllowed ->
                            "Jarvis Phone Control तैयार है"

                        !contactsAllowed ->
                            "Contacts की अनुमति दें"

                        !phoneAllowed ->
                            "Phone की अनुमति दें"

                        else ->
                            "Incoming Call की अनुमति दें"
                    }
                )
            }

            answerRequest -> {
                val allowed =
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED

                if (allowed) {
                    say("Incoming Call की अनुमति मिल गई")
                } else {
                    say("Incoming Call की अनुमति नहीं मिली")
                }
            }

            audioRequest -> {

                val allowed =
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED

                status.text = if (allowed) {
                    "माइक्रोफोन की अनुमति मिल गई"
                } else {
                    "माइक्रोफोन की अनुमति नहीं मिली"
                }
            }
        }
    }

    // FLOATING JARVIS

    private fun startFloatingJarvis() {

        if (!Settings.canDrawOverlays(this)) {

            status.text =
                "पहले Display over other apps अनुमति चालू करें"

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
                Intent(
                    this,
                    JarvisFloatingService::class.java
                )
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
            val intent = Intent(
                this,
                JarvisVoiceService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

            val result = tts?.setLanguage(
                Locale("hi", "IN")
           
