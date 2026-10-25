package com.example.jarvis

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class OnlineAiSettingsActivity : Activity() {

    private val cyan = Color.rgb(44, 204, 255)
    private val blue = Color.rgb(15, 95, 180)
    private val background = Color.rgb(4, 10, 24)
    private val panel = Color.rgb(10, 23, 43)
    private val white = Color.rgb(230, 245, 255)
    private val muted = Color.rgb(139, 174, 202)

    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var enabled: CheckBox
    private lateinit var status: TextView

    private val brain by lazy { OnlineAiBrain(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply {
            setBackgroundColor(this@OnlineAiSettingsActivity.background)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }

        page.addView(
            text("J.A.R.V.I.S ONLINE AI", 24f, cyan, true)
        )
        page.addView(
            text(
                "Render/server की जरूरत नहीं। यह ऐप सीधे HTTPS AI API से connect होगा.",
                13f,
                muted,
                false
            )
        )
        page.addView(space(18))

        page.addView(text("API ENDPOINT", 13f, cyan, true))
        endpoint = input(
            brain.getEndpoint(),
            "https://api.openai.com/v1/chat/completions"
        )
        page.addView(endpoint, lp())

        page.addView(space(10))
        page.addView(text("MODEL", 13f, cyan, true))
        model = input(brain.getModel(), "जैसे gpt-4o-mini")
        page.addView(model, lp())

        page.addView(space(10))
        page.addView(text("API KEY", 13f, cyan, true))
        apiKey = input("", "API key यहाँ डालें").apply {
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        page.addView(apiKey, lp())

        enabled = CheckBox(this).apply {
            text = "ONLINE AI ENABLE"
            textSize = 15f
            setTextColor(white)
            isChecked = brain.isEnabled()
            buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        page.addView(enabled)

        page.addView(space(10))

        val save = button("💾 SAVE SETTINGS")
        save.setOnClickListener {
            val key = apiKey.text.toString().trim()
            if (key.isBlank() && !brain.isEnabled()) {
                status.text = "API key डालें"
                return@setOnClickListener
            }

            brain.setConfig(
                endpoint.text.toString(),
                model.text.toString(),
                key.ifBlank { brain.getApiKey().orEmpty() },
                enabled.isChecked
            )

            status.text = "Online AI settings saved"
        }
        page.addView(save, lp())

        val test = button("🌐 TEST ONLINE AI")
        test.setOnClickListener {
            status.text = "Testing..."
            Thread {
                val result = brain.testConnection()
                runOnUiThread {
                    status.text = result
                }
            }.start()
        }
        page.addView(test, lp())

        val clear = button("🗑 CLEAR API KEY")
        clear.setOnClickListener {
            brain.clearConfig()
            apiKey.setText("")
            enabled.isChecked = false
            status.text = "Online AI settings cleared"
        }
        page.addView(clear, lp())

        page.addView(space(14))

        status = text(
            "API key इस ऐप के Android Keystore से encrypted storage में रखी जाएगी.",
            13f,
            white,
            false
        )
        page.addView(status)

        root.addView(page)
        setContentView(root)
    }

    private fun input(value: String, hint: String): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
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
    }

    private fun button(title: String): Button {
        return Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(white)
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 50, 90))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), cyan)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(54)
    )

    private fun space(v: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(v))
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()
}
