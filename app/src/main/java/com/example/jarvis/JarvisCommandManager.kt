package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

/** Executes supported commands only after the user has enabled relevant permissions. */
class JarvisCommandManager(private val context: Context) {
    fun execute(raw: String): String {
        val text = raw.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return "कृपया कमांड बोलिए"
        val service = JarvisAccessibility.active
        fun global(action: Int): String = when {
            service == null -> "पहले Jarvis Accessibility चालू करें"
            service.performGlobalAction(action) -> "जी सर, कमांड पूरी हुई"
            else -> "यह एक्शन उपलब्ध नहीं है"
        }
        fun launch(intent: Intent, response: String): String = try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            response
        } catch (_: Exception) { "यह काम अभी नहीं हो सका" }
        fun url(value: String): String = launch(Intent(Intent.ACTION_VIEW, Uri.parse(value)), "जी सर, खोल रहा हूँ")
        fun app(pkg: String, fallback: String): String {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            return if (intent != null) launch(intent, "जी सर, खोल रहा हूँ") else url(fallback)
        }
        return when {
            text.contains("नीचे स्क्रॉल") || text.contains("scroll down") -> when {
                service == null -> "पहले Jarvis Accessibility चालू करें"
                service.scroll(false) -> "नीचे स्क्रॉल किया"
                else -> "स्क्रॉल नहीं हो सका"
            }
            text.contains("ऊपर स्क्रॉल") || text.contains("scroll up") -> when {
                service == null -> "पहले Jarvis Accessibility चालू करें"
                service.scroll(true) -> "ऊपर स्क्रॉल किया"
                else -> "स्क्रॉल नहीं हो सका"
            }
            text.startsWith("क्लिक ") || text.startsWith("टच ") || text.startsWith("click ") -> when {
                service == null -> "पहले Jarvis Accessibility चालू करें"
                service.tapLabel(text.substringAfter(' ').trim()) -> "क्लिक किया"
                else -> "यह बटन नहीं मिला"
            }
            text.contains("रीसेंट") || text.contains("recent") -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            text.contains("नोटिफिकेशन") || text.contains("notification") -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            text.contains("क्विक सेटिंग") || text.contains("quick settings") -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            text.contains("होम") || text == "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            text.contains("बैक") || text.contains("पीछे") || text == "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            text.contains("यूट्यूब") || text.contains("youtube") -> app("com.google.android.youtube", "https://youtube.com")
            text.contains("व्हाट्सएप") || text.contains("whatsapp") -> app("com.whatsapp", "https://whatsapp.com")
            text.contains("क्रोम") || text.contains("chrome") -> app("com.android.chrome", "https://google.com")
            text.contains("कैमरा") || text.contains("camera") -> launch(Intent("android.media.action.IMAGE_CAPTURE"), "कैमरा खोल रहा हूँ")
            text.contains("डायलर") || text.contains("dialer") || text.contains("फोन खोलो") -> launch(Intent(Intent.ACTION_DIAL), "डायलर खोल रहा हूँ")
            text.contains("वाईफाई") || text.contains("wifi") || text.contains("wi-fi") -> launch(Intent(Settings.ACTION_WIFI_SETTINGS), "वाई-फाई सेटिंग खोल रहा हूँ")
            text.contains("ब्लूटूथ") || text.contains("bluetooth") -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "ब्लूटूथ सेटिंग खोल रहा हूँ")
            text.contains("बैटरी") || text.contains("battery") -> launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), "बैटरी सेटिंग खोल रहा हूँ")
            text.contains("डिस्प्ले") || text.contains("display") || text.contains("ब्राइटनेस") -> launch(Intent(Settings.ACTION_DISPLAY_SETTINGS), "डिस्प्ले सेटिंग खोल रहा हूँ")
            text.contains("आवाज सेटिंग") || text.contains("sound settings") -> launch(Intent(Settings.ACTION_SOUND_SETTINGS), "साउंड सेटिंग खोल रहा हूँ")
            text.contains("सेटिंग") || text.contains("settings") -> launch(Intent(Settings.ACTION_SETTINGS), "सेटिंग खोल रहा हूँ")
            text.startsWith("सर्च ") || text.startsWith("search ") -> url("https://www.google.com/search?q=" + Uri.encode(text.substringAfter(' ').trim()))
            text.startsWith("वेबसाइट ") || text.startsWith("website ") -> {
                val site = text.substringAfter(' ').trim()
                if (site.matches(Regex("[a-z0-9.-]+\\.[a-z]{2,}(/[^ ]*)?"))) url("https://$site") else "सही वेबसाइट का नाम बताइए"
            }
            else -> "यह कमांड अभी उपलब्ध नहीं है"
        }
    }
}
