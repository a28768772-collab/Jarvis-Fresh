package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.util.Locale

class JarvisCommandManager(
    private val context: Context
) {

    fun execute(raw: String): String {

        val text = raw.trim().lowercase(Locale.ROOT)

        if (text.isBlank()) {
            return "कृपया कमांड बोलिए"
        }

        val service = JarvisAccessibility.active

        // MOBILE GLOBAL ACTIONS

        fun global(action: Int): String {

            if (service == null) {
                return "पहले Jarvis Accessibility चालू करें"
            }

            return if (service.performGlobalAction(action)) {
                "जी सर, कमांड पूरी हुई"
            } else {
                "यह एक्शन अभी उपलब्ध नहीं है"
            }
        }

        // OPEN ANDROID INTENT

        fun launch(
            intent: Intent,
            response: String
        ): String {

            return try {

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                response

            } catch (e: Exception) {
                "यह काम अभी नहीं हो सका"
            }
        }

        // OPEN WEBSITE

        fun openUrl(url: String): String {

            return launch(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                ),
                "जी सर, खोल रहा हूँ"
            )
        }

        // OPEN APP

        fun openApp(
            packageName: String,
            fallback: String
        ): String {

            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)

            return if (intent != null) {

                launch(
                    intent,
                    "जी सर, ऐप खोल रहा हूँ"
                )

            } else {

                openUrl(fallback)
            }
        }

        // GOOGLE SEARCH

        fun search(query: String): String {

            if (query.isBlank()) {
                return "क्या सर्च करना है?"
            }

            return openUrl(
                "https://www.google.com/search?q=" +
                    Uri.encode(query)
            )
        }

        // COMMAND PROCESSING

        return when {

            // SCROLL DOWN

            text.contains("नीचे स्क्रॉल") ||
            text.contains("scroll down") -> {

                when {
                    service == null ->
                        "पहले Jarvis Accessibility चालू करें"

                    service.scroll(false) ->
                        "नीचे स्क्रॉल किया"

                    else ->
                        "स्क्रॉल नहीं हो सका"
                }
            }

            // SCROLL UP

            text.contains("ऊपर स्क्रॉल") ||
            text.contains("scroll up") -> {

                when {
                    service == null ->
                        "पहले Jarvis Accessibility चालू करें"

                    service.scroll(true) ->
                        "ऊपर स्क्रॉल किया"

                    else ->
                        "स्क्रॉल नहीं हो सका"
                }
            }

            // CLICK VISIBLE BUTTON

            text.startsWith("क्लिक ") ||
            text.startsWith("टच ") ||
            text.startsWith("click ") -> {

                val label = text.substringAfter(' ').trim()

                when {
                    service == null ->
                        "पहले Jarvis Accessibility चालू करें"

                    label.isBlank() ->
                        "किस बटन पर क्लिक करना है?"

                    service.tapLabel(label) ->
                        "क्लिक किया"

                    else ->
                        "यह बटन नहीं मिला"
                }
            }

            // HOME

            text.contains("होम") ||
            text.contains("home") ||
            text.contains("मुख्य स्क्रीन") ||
            text.contains("घर जाओ") ->

                global(
                    AccessibilityService.GLOBAL_ACTION_HOME
                )

            // BACK

            text.contains("बैक") ||
            text.contains("पीछे") ||
            text == "back" ->

                global(
                    AccessibilityService.GLOBAL_ACTION_BACK
                )

            // RECENT APPS

            text.contains("रीसेंट") ||
            text.contains("recent") ->

                global(
                    AccessibilityService.GLOBAL_ACTION_RECENTS
                )

            // NOTIFICATIONS

            text.contains("नोटिफिकेशन") ||
            text.contains("notification") ->

                global(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )

            // QUICK SETTINGS

            text.contains("क्विक सेटिंग") ||
            text.contains("quick settings") ->

                global(
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                )

            // YOUTUBE

            text.contains("यूट्यूब") ||
            text.contains("youtube") ->

                openApp(
                    "com.google.android.youtube",
                    "https://youtube.com"
                )

            // WHATSAPP

            text.contains("व्हाट्सएप") ||
            text.contains("व्हाट्सऐप") ||
            text.contains("whatsapp") ->

                openApp(
                    "com.whatsapp",
                    "https://whatsapp.com"
                )

            // INSTAGRAM

            text.contains("इंस्टाग्राम") ||
            text.contains("instagram") ->

                openApp(
                    "com.instagram.android",
                    "https://instagram.com"
                )

            // FACEBOOK

            text.contains("फेसबुक") ||
            text.contains("facebook") ->

                openApp(
                    "com.facebook.katana",
                    "https://facebook.com"
                )

            // CHROME

            text.contains("क्रोम") ||
            text.contains("chrome") ->

                openApp(
                    "com.android.chrome",
                    "https://google.com"
                )

            // GOOGLE MAPS

            text.contains("मैप") ||
            text.contains("maps") -> {

                val place = text
                    .replace("गूगल मैप", "")
                    .replace("मैप खोलो", "")
                    .replace("मैप्स खोलो", "")
                    .replace("google maps", "")
                    .replace("maps", "")
                    .replace("खोलो", "")
                    .trim()

                if (place.isBlank()) {

                    openUrl("https://maps.google.com")

                } else {

                    openUrl(
                        "https://www.google.com/maps/search/?api=1&query=" +
                            Uri.encode(place)
                    )
                }
            }

            // CAMERA

            text.contains("कैमरा") ||
            text.contains("camera") ->

                launch(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    "कैमरा खोल रहा हूँ"
                )

            // GALLERY

            text.contains("गैलरी") ||
            text.contains("gallery") ||
            text.contains("फोटो दिखाओ") ->

                launch(
                    Intent(
                        Intent.ACTION_VIEW,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    ).apply {
                        type = "image/*"
                    },
                    "गैलरी खोल रहा हूँ"
                )

            // MUSIC

            text.contains("म्यूजिक") ||
            text.contains("music") ||
            text.contains("गाने खोलो") ->

                launch(
                    Intent(
                        Intent.ACTION_VIEW
                    ).apply {
                        type = "audio/*"
                    },
                    "म्यूजिक ऐप खोल रहा हूँ"
                )

            // CALCULATOR

            text.contains("कैलकुलेटर") ||
            text.contains("calculator") -> {

                val calculator = Intent(
                    Intent.ACTION_MAIN
                ).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                }

                launch(
                    calculator,
                    "कैलकुलेटर खोल रहा हूँ"
                )
            }

            // PHONE DIALER

            text.contains("डायलर") ||
            text.contains("dialer") ||
            text.contains("फोन खोलो") ->

                launch(
                    Intent(Intent.ACTION_DIAL),
                    "फोन डायलर खोल रहा हूँ"
                )

            // WIFI SETTINGS

            text.contains("वाईफाई") ||
            text.contains("वाई-फाई") ||
            text.contains("wifi") ||
            text.contains("wi-fi") ->

                launch(
                    Intent(Settings.ACTION_WIFI_SETTINGS),
                    "वाई-फाई सेटिंग खोल रहा हूँ"
                )

            // BLUETOOTH SETTINGS

            text.contains("ब्लूटूथ") ||
            text.contains("bluetooth") ->

                launch(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                    "ब्लूटूथ सेटिंग खोल रहा हूँ"
                )

            // BATTERY SETTINGS

            text.contains("बैटरी") ||
            text.contains("battery") ->

                launch(
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                    "बैटरी सेटिंग खोल रहा हूँ"
                )

            // DISPLAY SETTINGS

            text.contains("डिस्प्ले") ||
            text.contains("display") ||
            text.contains("ब्राइटनेस") ||
            text.contains("brightness") ->

                launch(
                    Intent(Settings.ACTION_DISPLAY_SETTINGS),
                    "डिस्प्ले सेटिंग खोल रहा हूँ"
                )

            // SOUND SETTINGS

            text.contains("साउंड सेटिंग") ||
            text.contains("आवाज सेटिंग") ||
            text.contains("sound settings") ->

                launch(
                    Intent(Settings.ACTION_SOUND_SETTINGS),
                    "साउंड सेटिंग खोल रहा हूँ"
                )

            // PHONE SETTINGS

            text.contains("सेटिंग") ||
            text.contains("settings") ->

                launch(
                    Intent(Settings.ACTION_SETTINGS),
                    "फोन सेटिंग खोल रहा हूँ"
                )

            // GOOGLE SEARCH

            text.startsWith("सर्च ") ||
            text.startsWith("search ") ->

                search(
                    text.substringAfter(' ').trim()
                )

            // WEBSITE CONTROL

            text.startsWith("वेबसाइट ") ||
            text.startsWith("website ") -> {

                val site = text
                    .substringAfter(' ')
                    .trim()
                    .removeSuffix(" खोलो")
                    .removeSuffix(" open")
                    .trim()

                if (
                    site.matches(
                        Regex(
                            "[a-z0-9.-]+\\.[a-z]{2,}(/[^ ]*)?"
                        )
                    )
                ) {

                    openUrl("https://$site")

                } else {

                    "सही वेबसाइट का नाम बताइए"
                }
            }

            // UNKNOWN COMMAND

            else -> {
                "यह कमांड अभी उपलब्ध नहीं है"
            }
        }
    }
}
