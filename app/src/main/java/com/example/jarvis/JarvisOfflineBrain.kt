package com.example.jarvis

import java.util.Locale

class JarvisOfflineBrain {

    fun answer(raw: String): String? {

        val text = raw.trim().lowercase(Locale.ROOT)

        return when {

            text.contains("तुम्हारा नाम") ||
            text.contains("your name") ->
                "मेरा नाम जार्विस है। मैं आपका पर्सनल असिस्टेंट हूँ।"

            text.contains("तुम कौन हो") ||
            text.contains("who are you") ->
                "मैं जार्विस हूँ। मैं मोबाइल कंट्रोल और सामान्य सवालों में मदद करता हूँ।"

            text.contains("कैसे हो") ||
            text.contains("how are you") ->
                "मैं तैयार हूँ सर। बताइए क्या करना है?"

            text.contains("क्या कर सकते हो") ||
            text.contains("what can you do") ->
                "मैं ऐप खोल सकता हूँ, फोन की कुछ सेटिंग खोल सकता हूँ, स्क्रीन स्क्रॉल कर सकता हूँ और सामान्य सवालों के जवाब दे सकता हूँ।"

            text.contains("धन्यवाद") ||
            text.contains("thank you") ->
                "आपका स्वागत है।"

            text.contains("नमस्ते") ||
            text.contains("hello") ->
                "नमस्ते सर। मैं जार्विस, आपकी सेवा में तैयार हूँ।"

            text.contains("भारत की राजधानी") ->
                "भारत की राजधानी नई दिल्ली है।"

            text.contains("एक साल में कितने महीने") ->
                "एक साल में बारह महीने होते हैं।"

            text.contains("एक हफ्ते में कितने दिन") ->
                "एक हफ्ते में सात दिन होते हैं।"

            text.contains("सूरज कहाँ से निकलता") ->
                "सूरज पूर्व दिशा से निकलता है।"

            else -> null
        }
    }
}
