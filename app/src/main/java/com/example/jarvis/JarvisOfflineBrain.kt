package com.example.jarvis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone

class JarvisOfflineBrain {

    fun answer(raw: String): String? {
        val original = raw.trim()
        if (original.isBlank()) return "कृपया अपना सवाल बताइए।"

        val text = original
            .lowercase(Locale.ROOT)
            .replace(Regex("[,.!?।]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val now = Date()
        val locale = Locale("hi", "IN")

        return when {
            // पहचान और बातचीत
            containsAny(text, "तुम्हारा नाम", "आपका नाम", "your name") ->
                "मेरा नाम जार्विस है। मैं आपका ऑफलाइन पर्सनल असिस्टेंट हूँ।"

            containsAny(text, "तुम कौन हो", "आप कौन हैं", "who are you") ->
                "मैं जार्विस हूँ। मैं ऑफलाइन कमांड और सामान्य सवालों में मदद करता हूँ।"

            containsAny(text, "कैसे हो", "कैसा है", "how are you") ->
                "मैं तैयार हूँ। बताइए, आपकी क्या मदद करूँ?"

            containsAny(text, "क्या कर सकते हो", "क्या क्या कर सकते", "what can you do") ->
                "मैं सामान्य सवालों के जवाब दे सकता हूँ, आसान गणना कर सकता हूँ और आपके ऐप के कमांड सिस्टम के साथ काम कर सकता हूँ।"

            containsAny(text, "नमस्ते", "नमस्कार", "राम राम", "hello", "hi jarvis") ->
                "नमस्ते! जार्विस तैयार है। बताइए क्या करना है?"

            containsAny(text, "धन्यवाद", "शुक्रिया", "thank you", "thanks") ->
                "आपका स्वागत है।"

            containsAny(text, "गुड मॉर्निंग", "good morning") ->
                "सुप्रभात! आपका दिन अच्छा रहे।"

            containsAny(text, "गुड नाइट", "good night") ->
                "शुभ रात्रि। आराम से सोइए।"

            containsAny(text, "तुम्हें किसने बनाया", "who created you") ->
                "मुझे आपके JARVIS Android प्रोजेक्ट के लिए बनाया जा रहा है।"

            // तारीख और समय
            containsAny(text, "अभी कितना समय", "समय क्या है", "टाइम बताओ", "what time is it") ->
                "अभी समय है " + SimpleDateFormat("hh:mm a", locale).format(now)

            containsAny(text, "आज कौन सा दिन", "आज का दिन", "what day is it") ->
                "आज " + SimpleDateFormat("EEEE", locale).format(now) + " है।"

            containsAny(text, "आज की तारीख", "तारीख बताओ", "what is the date") ->
                "आज की तारीख " + SimpleDateFormat("dd MMMM yyyy", locale).format(now) + " है।"

            containsAny(text, "इस साल कौन सा साल", "कौन सा साल चल रहा", "what year is it") ->
                "अभी साल " + SimpleDateFormat("yyyy", locale).format(now) + " चल रहा है।"

            // सामान्य ज्ञान
            containsAny(text, "भारत की राजधानी", "capital of india") ->
                "भारत की राजधानी नई दिल्ली है।"

            containsAny(text, "हरियाणा की राजधानी", "capital of haryana") ->
                "हरियाणा और पंजाब की साझा राजधानी चंडीगढ़ है।"

            containsAny(text, "पंजाब की राजधानी", "capital of punjab") ->
                "पंजाब की राजधानी चंडीगढ़ है।"

            containsAny(text, "एक साल में कितने महीने", "months in a year") ->
                "एक साल में बारह महीने होते हैं।"

            containsAny(text, "एक हफ्ते में कितने दिन", "days in a week") ->
                "एक हफ्ते में सात दिन होते हैं।"

            containsAny(text, "एक दिन में कितने घंटे", "hours in a day") ->
                "एक दिन में चौबीस घंटे होते हैं।"

            containsAny(text, "एक घंटे में कितने मिनट", "minutes in an hour") ->
                "एक घंटे में साठ मिनट होते हैं।"

            containsAny(text, "एक मिनट में कितने सेकंड", "seconds in a minute") ->
                "एक मिनट में साठ सेकंड होते हैं।"

            containsAny(text, "सूरज कहाँ से निकलता", "sun rise direction") ->
                "सूरज पूर्व दिशा में उगता हुआ दिखाई देता है।"

            containsAny(text, "पृथ्वी का उपग्रह", "natural satellite of earth") ->
                "चंद्रमा पृथ्वी का प्राकृतिक उपग्रह है।"

            containsAny(text, "पानी का रासायनिक सूत्र", "chemical formula of water") ->
                "पानी का रासायनिक सूत्र H₂O है।"

            containsAny(text, "भारत का राष्ट्रीय पशु", "national animal of india") ->
                "भारत का राष्ट्रीय पशु बाघ है।"

            containsAny(text, "भारत का राष्ट्रीय पक्षी", "national bird of india") ->
                "भारत का राष्ट्रीय पक्षी मोर है।"

            containsAny(text, "भारत का राष्ट्रीय फूल", "national flower of india") ->
                "भारत का राष्ट्रीय फूल कमल है।"

            containsAny(text, "भारत का स्वतंत्रता दिवस", "independence day of india") ->
                "भारत का स्वतंत्रता दिवस 15 अगस्त को मनाया जाता है।"

            containsAny(text, "गणतंत्र दिवस कब", "republic day of india") ->
                "भारत का गणतंत्र दिवस 26 जनवरी को मनाया जाता है।"

            // गणना
            isMathQuestion(text) -> calculate(text)

            // छोटी बातचीत
            containsAny(text, "मेरा नाम क्या", "what is my name") ->
                "आपने अभी तक मुझे अपना नाम नहीं बताया है।"

            containsAny(text, "मदद करो", "help me", "मदद चाहिए") ->
                "अपना सवाल पूछिए या कोई कमांड दीजिए। मैं उपलब्ध ऑफलाइन जानकारी से मदद करूँगा।"

            containsAny(text, "तुम ऑनलाइन हो", "are you online") ->
                "मेरा यह जवाब ऑफलाइन नियमों से बन रहा है। इंटरनेट उपलब्ध होना ज़रूरी नहीं है।"

            else -> null
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean {
        return phrases.any { text.contains(it.lowercase(Locale.ROOT)) }
    }

    private fun isMathQuestion(text: String): Boolean {
        return Regex("""-?\d+(?:\.\d+)?\s*[\+\-*/×÷]\s*-?\d+(?:\.\d+)?""")
            .containsMatchIn(text)
    }

    private fun calculate(text: String): String {
        val match = Regex(
            """(-?\d+(?:\.\d+)?)\s*([\+\-*/×÷])\s*(-?\d+(?:\.\d+)?)"""
        ).find(text) ?: return "कृपया गणित इस तरह बोलें: 25 + 5"

        val a = match.groupValues[1].toDoubleOrNull()
            ?: return "पहली संख्या समझ नहीं आई।"
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull()
            ?: return "दूसरी संख्या समझ नहीं आई।"

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "×" -> a * b
            "/", "÷" -> {
                if (b == 0.0) return "शून्य से भाग नहीं दिया जा सकता।"
                a / b
            }
            else -> return "यह गणितीय ऑपरेशन उपलब्ध नहीं है।"
        }

        val formatted = if (result.isFinite() &&
            result == result.toLong().toDouble()
        ) {
            result.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", result)
                .trimEnd('0')
                .trimEnd('.')
        }

        return "जवाब है $formatted"
    }
}
