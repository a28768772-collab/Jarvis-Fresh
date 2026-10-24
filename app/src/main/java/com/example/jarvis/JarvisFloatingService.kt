package com.example.jarvis

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class JarvisFloatingService : Service() {

    private lateinit var windowManager: WindowManager

    private var bubble: TextView? = null
    private var menu: LinearLayout? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var active = false

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(
            WINDOW_SERVICE
        ) as WindowManager

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

    private fun roundedBackground(
        color: Int,
        stroke: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 45f
            setColor(color)
            setStroke(3, stroke)
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

            background = roundedBackground(
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
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (kotlin.math.abs(dx) > 10 ||
                        kotlin.math.abs(dy) > 10
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
            windowManager.addView(emoji, params)
            bubble = emoji
            bubbleParams = params
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun onBubbleTap() {

        if (!active) {
            active = true
            bubble?.text = "😎"

            // Sleep से Active होने पर मेनू नहीं खुलेगा।
            return
        }

        if (menu != null) {
            hideMenu()
        } else {
            showMenu()
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

            background = roundedBackground(
                Color.rgb(27, 29, 44),
                Color.rgb(92, 181, 255)
            )

            elevation = 15f
        }

        val heading = TextView(this).apply {
            text = "😎  JARVIS"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = "जी सर, आदेश दीजिए!"
            textSize = 15f
            setTextColor(Color.rgb(160, 222, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12.dp(), 0, 12.dp())
        }

        panel.addView(heading)
        panel.addView(message)

        fun addButton(
            title: String,
            action: () -> Unit
        ) {
            val button = Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { action() }
            }

            panel.addView(button)
        }

        addButton("🎙 Open Jarvis") {
            val intent = Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            startActivity(intent)
            hideMenu()
        }

        addButton("😴 Sleep Mode") {
            active = false
            bubble?.text = "😴"
            hideMenu()
        }

        addButton("➖ Minimize") {
            hideMenu()
        }

        addButton("❌ Close Bubble") {
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
            windowManager.addView(panel, params)
            menu = panel
            menuParams = params
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
        menuParams = null
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
        ).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideMenu()

        bubble?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }

        bubble = null
        bubbleParams = null

        super.onDestroy()
    }
}
