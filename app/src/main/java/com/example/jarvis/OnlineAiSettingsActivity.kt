package com.example.jarvis

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*

class OnlineAiSettingsActivity : Activity() {
    private val cyan = Color.rgb(44, 204, 255)
    private val blue = Color.rgb(15, 95, 180)
    private val pageBackground = Color.rgb(4, 10, 24)
    private val panelColor = Color.rgb(10, 23, 43)
    private val white = Color.rgb(230, 245, 255)
    private val muted = Color.rgb(139, 174, 202)

    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var enabled: CheckBox
    private lateinit var status: TextView
    private val brain by lazy { OnlineAiBrain(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground

        val root = ScrollView(this).apply { setBackgroundColor(pageBackground) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(30))
        }
        page.addView(text("J.A.R.V.I.S • GEMINI AI", 23f, cyan, true))
        page.addView(space(8))
        page.addView(text("अपनी Gemini API Key पेस्ट करें, Save दबाएँ और फिर Test Connection करें।", 14f, white, false))
        page.addView(space(6))
        page.addView(text("यह ऐप सीधे Gemini API से जुड़ता है; अलग Render server की जरूरत नहीं।", 12f, muted, false))
        page.addView(space(20))

        page.addView(text("GEMINI API KEY", 13f, cyan, true))
        apiKey = input("", "यहाँ अपनी Gemini API Key पेस्ट करें").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        page.addView(apiKey, lp(58))
        page.addView(space(14))

        page.addView(text("MODEL (बदलना जरूरी नहीं)", 13f, cyan, true))
        model = input(brain.getModel(), "gemini-3.8-flash")
        page.addView(model, lp(54))
        page.addView(space(8))

        enabled = CheckBox(this).apply {
            text = "GEMINI ONLINE AI चालू रखें"
            textSize = 15f
            setTextColor(white)
            isChecked = brain.isEnabled()
            buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        page.addView(enabled)
        page.addView(space(14))

        status = text(
            if (brain.isEnabled()) "Saved API key मिली है। Test Connection दबाकर जाँचें।"
            else "अभी Gemini कनेक्शन सेट नहीं है।",
            13f, white, false
        )
        status.setPadding(dp(12), dp(12), dp(12), dp(12))
        status.background = panelBackground()
        page.addView(status, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(space(12))

        page.addView(button("💾 SAVE AND ENABLE GEMINI").apply {
            setOnClickListener { saveSettings(showSuccess = true) }
        }, lp(54))
        page.addView(space(8))
        page.addView(button("🌐 TEST CONNECTION").apply {
            setOnClickListener {
                if (!saveSettings(showSuccess = false)) return@setOnClickListener
                status.text = "Gemini से कनेक्ट हो रहा है…"
                Thread {
                    val result = brain.testConnection()
                    runOnUiThread { status.text = result }
                }.start()
            }
        }, lp(54))
        page.addView(space(8))
        page.addView(button("🗑 CLEAR SAVED API KEY").apply {
            setOnClickListener {
                brain.clearConfig()
                apiKey.setText("")
                enabled.isChecked = false
                status.text = "Saved Gemini settings साफ़ कर दी गई हैं।"
            }
        }, lp(54))
        page.addView(space(16))
        page.addView(text(
            "सुरक्षा: API Key Android Keystore से encrypted storage में रखी जाती है। फिर भी APK में इस्तेमाल की गई key को पूरी तरह छिपाने की गारंटी नहीं होती। इसे GitHub या किसी के साथ साझा न करें।",
            12f, muted, false
        ))

        root.addView(page)
        setContentView(root)
    }

    private fun saveSettings(showSuccess: Boolean): Boolean {
        val key = apiKey.text.toString().trim()
        if (key.isBlank() && brain.getApiKey().isNullOrBlank()) {
            status.text = "पहले Gemini API Key पेस्ट करें।"
            apiKey.requestFocus()
            return false
        }
        val saved = brain.setConfig(
            brain.getEndpoint(),
            model.text.toString(),
            key,
            enabled = true
        )
        if (!saved) {
            status.text = "Key सेव नहीं हो सकी। दोबारा कोशिश करें।"
            return false
        }
        enabled.isChecked = true
        apiKey.setText("") // don't leave the secret visible on screen
        if (showSuccess) status.text = "Gemini API Key सेव हो गई। अब TEST CONNECTION दबाएँ।"
        return true
    }

    private fun input(value: String, hintText: String): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        setHintTextColor(muted)
        setTextColor(white)
        textSize = 14f
        setSingleLine(true)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 17, 34))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), blue)
        }
    }

    private fun button(title: String): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        setTextColor(white)
        background = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), cyan)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun panelBackground() = GradientDrawable().apply {
        setColor(panelColor)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), blue)
    }

    private fun lp(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        if (height == ViewGroup.LayoutParams.WRAP_CONTENT) height else dp(height)
    )

    private fun space(value: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(value))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
