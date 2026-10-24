package com.example.jarvis

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class JarvisFloatingService : Service(),
    TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager

    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var menu: LinearLayout? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var active = false
    private var speaking = false
    private var greetingPending = false
    private var animationFrame = 0
    private var receiverRegistered = false

    // Voice Service से Sleep/Awake संदेश प्राप्त करें।
    private val stateReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            when (intent?.getStringExtra("state")) {

                "sleep" -> {
                    showSleepState()
                }

                "awake" -> {
                    active = true

                    if (!speaking) {
                        bubble?.apply {
                            text = "😎"
                            scaleX = 1f
                            scaleY = 1f
                        }
                    }
                }
            }
        }
    }

    private val animation = object : Runnable {

        override fun run() {

            if (!speaking) return

            val faces = arrayOf(
                "😮",
                "😃",
                "😮",
                "😊"
            )

            bubble?.apply {

                text = faces[animationFrame % faces.size]

                val scale = if (animationFrame % 2 == 0) {
                    1.12f
                } else {
                    0.94f
                }

                scaleX = scale
                scaleY = scale
            }

            animationFrame++

            handler.postDelayed(this, 220)
        }
    }

    override fun onCreate() {

        super.onCreate()

        windowManager = getSystemService(
            WINDOW_SERVICE
        ) as WindowManager

        // Voice Service का Broadcast सुनें।
        val filter = IntentFilter(
            JarvisVoiceService.BUBBLE_STATE
        )

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                stateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            @Suppress("DEPRECATION")
            registerReceiver(
                stateReceiver,
                filter
            )
        }

        receiverRegistered = true

        tts = TextToSpeech(this, this)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        showBubble()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!Settings.canDrawOverlays(this)) {

            stopSelf()

            return START_NOT_STICKY
        }

        if (bubble == null) {
            showBubble()
        }

        return START_NOT_STICKY
    }

    private fun overlayType(): Int {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        } else {

            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun Int.dp(): Int {

        return (
            this * resources.displayMetrics.density
        ).toInt()
    }

    private fun background(
        color: Int,
        borderColor: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape = GradientDrawable.RECTANGLE

            cornerRadius = 45f

            setColor(color)

            setStroke(
                3.dp(),
                borderColor
            )
        }
    }

    private fun showBubble() {

        if (bubble != null) return

        if (!Settings.canDrawOverlays(this)) return

        val emoji = TextView(this).apply {

            text = "😴"

            textSize = 35f

            gravity = Gravity.CENTER

            elevation = 12f

            background = background(
                Color.rgb(29, 31, 46),
                Color.rgb(112, 89, 210)
            )
        }

        val params = WindowManager.LayoutParams(
            78.dp(),
            78.dp(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START

            x = 25.dp()

            y = 180.dp()
        }

        var startX = 0
        var startY = 0

        var touchX = 0f
        var touchY = 0f

        var moved = false

        emoji.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    startX = params.x
                    startY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    moved = false

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = (
                        event.rawX - touchX
                    ).toInt()

                    val dy = (
                        event.rawY - touchY
                    ).toInt()

                    if (
                        abs(dx) > 10 ||
                        abs(dy) > 10
                    ) {
                        moved = true
                    }

                    if (moved) {

                        params.x = startX + dx
                        params.y = startY + dy

                        try {

                            windowManager.updateViewLayout(
                                emoji,
                                params
                            )

                        } catch (_: Exception) {}

                        hideMenu()
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!moved) {
                        onBubbleTap()
                    }

                    true
                }

                else -> false
            }
        }

        try {

            windowManager.addView(
                emoji,
                params
            )

            bubble = emoji

        } catch (_: Exception) {

            stopSelf()
        }
    }

    private fun onBubbleTap() {

        // Sleep Emoji पर टैप करने से Jarvis फिर शुरू होगा।
        if (!active) {

            active = true

            bubble?.text = "😊"

            greetUser()

            return
        }

        if (speaking) return

        if (menu == null) {

            showMenu()

        } else {

            hideMenu()
        }
    }

    private fun greetUser() {

        greetingPending = true

        if (!ttsReady) {
            return
        }

        greetingPending = false

        speaking = true

        animationFrame = 0

        handler.removeCallbacks(animation)

        handler.post(animation)

        val result = tts?.speak(
            "Hi sir, मैं आपकी क्या सहायता करूँ?",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_bubble_greeting"
        )

        if (result != TextToSpeech.SUCCESS) {
            finishGreeting()
        }
    }

    private fun finishGreeting() {

        handler.removeCallbacks(animation)

        speaking = false

        bubble?.apply {

            scaleX = 1f
            scaleY = 1f

            text = if (active) {
                "😎"
            } else {
                "😴"
            }
        }

        if (active) {
            startVoiceService()
        }
    }

    private fun startVoiceService() {

        try {

            val intent = Intent(
                this,
                JarvisVoiceService::class.java
            ).apply {

                // Greeting के बाद सीधे कमांड सुनें।
                putExtra(
                    JarvisVoiceService.EXTRA_DIRECT_LISTEN,
                    true
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

        } catch (_: Exception) {

            active = false

            bubble?.text = "😕"

            Toast.makeText(
                this,
                "Jarvis Voice शुरू नहीं हुई। ऐप खोलकर START HEY JARVIS दबाएँ।",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // केवल Emoji और Bubble की स्थिति Sleep करें।
    private fun showSleepState() {

        active = false

        greetingPending = false

        speaking = false

        handler.removeCallbacks(animation)

        try {
            tts?.stop()
        } catch (_: Exception) {}

        bubble?.apply {

            text = "😴"

            scaleX = 1f
            scaleY = 1f
        }

        hideMenu()
    }

    // Menu के Sleep बटन के लिए।
    private fun enterSleepMode() {

        showSleepState()

        try {

            startService(
                Intent(
                    this,
                    JarvisVoiceService::class.java
                ).setAction(
                    JarvisVoiceService.SLEEP
                )
            )

        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts?.language = Locale(
                "hi",
                "IN"
            )

            ttsReady = true

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {

                        if (id == "jarvis_bubble_greeting") {

                            handler.post {

                                if (active && speaking) {
                                    finishGreeting()
                                }
                            }
                        }
                    }

                    override fun onError(id: String?) {

                        if (id == "jarvis_bubble_greeting") {

                            handler.post {

                                if (active && speaking) {
                                    finishGreeting()
                                }
                            }
                        }
                    }
                }
            )

            if (greetingPending && active) {

                handler.post {
                    greetUser()
                }
            }

        } else {

            ttsReady = false

            if (greetingPending && active) {

                greetingPending = false

                handler.post {
                    finishGreeting()
                }
            }
        }
    }

    private fun showMenu() {

        if (menu != null) return

        val panel = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                16.dp(),
                16.dp(),
                16.dp(),
                16.dp()
            )

            background = background(
                Color.rgb(27, 29, 44),
                Color.rgb(92, 181, 255)
            )

            elevation = 15f
        }

        val heading = TextView(this).apply {

            text = "😎  JARVIS"

            textSize = 21f

            setTextColor(Color.WHITE)

            setTypeface(
                null,
                Typeface.BOLD
            )

            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {

            text = "जी सर, आदेश दीजिए!"

            textSize = 15f

            setTextColor(
                Color.rgb(160, 222, 255)
            )

            gravity = Gravity.CENTER

            setPadding(
                0,
                12.dp(),
                0,
                12.dp()
            )
        }

        panel.addView(heading)
        panel.addView(message)

        fun addButton(
            title: String,
            action: () -> Unit
        ) {

            panel.addView(
                Button(this).apply {

                    text = title

                    isAllCaps = false

                    setOnClickListener {
                        action()
                    }
                }
            )
        }

        addButton("🎙 Open Jarvis") {

            val intent = Intent(
                this,
                MainActivity::class.java
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            try {

                startActivity(intent)

            } catch (_: Exception) {}

            hideMenu()
        }

        addButton("😴 Sleep Mode") {

            enterSleepMode()
        }

        addButton("➖ Minimize") {

            hideMenu()
        }

        addButton("❌ Close Bubble") {

            // Close करने पर Voice Service भी बंद होगी।
            try {

                stopService(
                    Intent(
                        this,
                        JarvisVoiceService::class.java
                    )
                )

            } catch (_: Exception) {}

            stopSelf()
        }

        val params = WindowManager.LayoutParams(
            255.dp(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.CENTER
        }

        try {

            windowManager.addView(
                panel,
                params
            )

            menu = panel

        } catch (_: Exception) {

            menu = null
        }
    }

    private fun hideMenu() {

        menu?.let {

            try {

                windowManager.removeView(it)

            } catch (_: Exception) {}
        }

        menu = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {

        active = false
        speaking = false
        greetingPending = false

        handler.removeCallbacksAndMessages(null)

        if (receiverRegistered) {

            try {

                unregisterReceiver(stateReceiver)

            } catch (_: Exception) {}

            receiverRegistered = false
        }

        hideMenu()

        bubble?.let {

            try {

                windowManager.removeView(it)

            } catch (_: Exception) {}
        }

        bubble = null

        try {

            tts?.stop()
            tts?.shutdown()

        } catch (_: Exception) {}

        tts = null

        super.onDestroy()
    }
}
