package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import java.util.Locale

object JarvisContactCalling {

    private data class Contact(
        val name: String,
        val number: String,
        val score: Int
    )

    private var pending: Contact? = null
    private var choices: List<Contact> = emptyList()

    // Hindi voice name -> saved English contact name.
    // Only names are mapped, never phone numbers.

    private val aliases = mapOf(
        "आकाश कुमार" to "Aakash Kumar O",
        "आकाश कुमार ओ" to "Aakash Kumar O",
        "अनुज सिंग" to "Anuj Sing",
        "अनुज सिंह" to "Anuj Singh",
        "अनुज कुमार" to "Anuj Kumar",
        "अमित कुमार" to "Amit Kumar Abhaypur",
        "अमित कुमार अभयपुर" to "Amit Kumar Abhaypur",
        "अमृता भाभी" to "Amrita Bhabhiji",
        "अमृता की सिस्टर" to "Amrita Ki Sister",
        "अंकेत पिकअप" to "Aneket Pikup",
        "अंजलि भाभी" to "Anjali Bhabhiji",
        "एम्बुलेंस" to "Ambulance",

        "धीरज" to "Dheeraj",
        "धीरज बाबू" to "Dhiraj Babu",
        "धीरज बबुआ" to "Dhiraj Babuaa",
        "दीदी" to "Didi",
        "दीपाली भाभी" to "Dipali Bhabhiji",

        "फ्रेंड" to "Friend",
        "जीबी सेट्रिंग" to "Gb Settring Sanoli",
        "गोलू पारस" to "Golu Paras",
        "गुड्डू गुप्ता" to "Guddu Gupta",
        "गुड़िया चौहान" to "Gudhiya Chauhan",
        "गुड़िया दीदी" to "Gudiya Didi Guraini",
        "गुड़िया फ्रेंड" to "Gudiya Friend",
        "गुरिंदर सिंह" to "Gurinder Singh",
        "हरि शरणम" to "Hari Sharnam Chilling Fluids",
        "इंदल" to "Indal",

        "जया दीदी" to "Jyaaa Didi",
        "काजल के ससुर जी" to "Kajal Ke Sasur Ji",
        "काजल सिस्टर" to "Kajal Sister",
        "कालू" to "Kalu",
        "कानक्सू" to "Kanxu You",
        "कपूरथला" to "Kapurthala",
        "करण भाई" to "Karan Bhai",
        "किरण" to "Kiran",
        "किरण आंटी" to "Kiran aanti",
        "किसान" to "Kisan",
        "किस्त" to "Kist",
        "किस्त वाला" to "Kist Wala",
        "किट्टू भाई" to "Kittu Bhai",
        "कृषमा सिस्टर" to "Krishama Sister",
        "लालू" to "Lalu",

        "मयूर" to "Mayur KTH",
        "मम्मी" to "Mmmiy",
        "मोएन" to "Moen",
        "मोनीलाल" to "Monilal",
        "मुल्ला" to "Mulla",
        "पापा" to "पापा",
        "प्रदीप" to "Pradeep",
        "प्रतिमा दी" to "Pratima Di",
        "प्रतिमा दीदी" to "Pratima Didi",
        "प्रिंस" to "Prince",
        "प्रिंश" to "Prinsh",
        "प्रिया" to "Priya",
        "पुष्पा फ्रेंड" to "Pushpa Friend Veeraipur",
        "सोनू" to "Sonu",

        "माय डैड" to "My Dad",
        "माय लव" to "My Love",
        "माय लाइफ" to "My Life",
        "माय डियर हसबैंड" to "My Dear Husband Ji",
        "माय वाइफ" to "My Wief",
        "माय एयरटेल" to "my airtel",
        "मेडिकल हेल्प" to "Medical Help",
        "म्यूजिक एंड मोर" to "Music & More",
        "इंस्टेंट हेल्प" to "Instant help 24x7"
    )

    private val yesWords = setOf(
        "हाँ", "हां", "हा", "yes",
        "yes call", "जी हाँ", "जी हां",
        "हाँ कॉल करो", "हां कॉल करो",
        "हाँ कॉल कर दो", "हां कॉल कर दो",
        "कॉल करो", "कॉल कर दो",
        "फोन लगाओ", "कर दो"
    )

    private val noWords = setOf(
        "नहीं", "नही", "ना", "no",
        "cancel", "कैंसिल", "रद्द करो",
        "मत करो", "कॉल मत करो"
    )

    private fun clean(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(value: String): String {
        return clean(value).replace(" ", "")
    }

    private fun digits(value: String): String {
        return value.filter { it.isDigit() }
    }

    private fun requestedName(value: String): String {
        var name = clean(value)

        val prefixes = listOf(
            "hey jarvis ",
            "jarvis ",
            "हे जार्विस ",
            "जार्विस ",
            "जारविस ",
            "जार्वेज ",
            "जारवेज ",
            "कृपया ",
            "please ",
            "call ",
            "कॉल करो ",
            "फोन लगाओ "
        )

        var changed: Boolean

        do {
            changed = false

            for (prefix in prefixes) {
                if (name.startsWith(prefix)) {
                    name = name.removePrefix(prefix).trim()
                    changed = true
                    break
                }
            }
        } while (changed)

        val suffixes = listOf(
            " को कॉल लगाओ",
            " को कॉल करो",
            " को फोन लगाओ",
            " को फोन करो",
            " को call करो",
            " को call लगाओ",
            " को कॉल",
            " को call",
            " को फोन",
            " call",
            " को डायल करो"
        )

        for (suffix in suffixes) {
            if (name.endsWith(suffix)) {
                name = name.removeSuffix(suffix).trim()
                break
            }
        }

        return name
    }

    private fun resolveAlias(value: String): String {
        val key = normalize(value)

        return aliases.entries.firstOrNull {
            normalize(it.key) == key
        }?.value ?: value
    }

    private fun scoreName(
        savedName: String,
        spokenName: String
    ): Int {

        val saved = clean(savedName)
        val spoken = clean(spokenName)

        val a = normalize(saved)
        val b = normalize(spoken)

        if (a.isBlank() || b.isBlank()) return 0

        // Exact match, including emoji-free names.
        if (a == b) return 100

        val savedWords = saved.split(" ")
            .filter { it.isNotBlank() }

        val spokenWords = spoken.split(" ")
            .filter { it.isNotBlank() }

        if (savedWords.any { normalize(it) == b }) {
            return 85
        }

        if (
            spokenWords.isNotEmpty() &&
            spokenWords.all { word ->
                savedWords.any {
                    normalize(it) == normalize(word)
                }
            }
        ) {
            return 80
        }

        if (a.startsWith(b) && b.length >= 3) {
            return 70
        }

        if (b.length >= 4 && a.contains(b)) {
            return 60
        }

        return 0
    }

    private fun choiceIndex(raw: String): Int? {

        val text = clean(raw)

        return when (text) {

            "1", "एक", "पहला", "पहले",
            "नंबर एक", "नंबर 1" -> 0

            "2", "दो", "दूसरा", "दूसरे",
            "नंबर दो", "नंबर 2" -> 1

            "3", "तीन", "तीसरा",
            "नंबर तीन", "नंबर 3" -> 2

            "4", "चार", "चौथा",
            "नंबर चार", "नंबर 4" -> 3

            "5", "पांच", "पाँच", "पांचवां",
            "नंबर पांच", "नंबर 5" -> 4

            else -> null
        }
    }

    fun isConfirmation(raw: String): Boolean {

        if (pending == null && choices.isEmpty()) {
            return false
        }

        val text = clean(raw)

        if (text in yesWords || text in noWords) {
            return true
        }

        if (choices.isNotEmpty()) {
            return choiceIndex(text) != null
        }

        return false
    }

    fun cancel() {
        pending = null
        choices = emptyList()
    }

    fun findContact(
        context: Context,
        spokenName: String
    ): String {

        cancel()

        if (
            context.checkSelfPermission(
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "पहले Jarvis को Contacts की अनुमति दें"
        }

        val spoken = requestedName(spokenName)
        val wanted = resolveAlias(spoken)

        if (wanted.isBlank()) {
            return "किस व्यक्ति को कॉल करना है?"
        }

        val matches = mutableListOf<Contact>()
        var totalContacts = 0

        try {

            val uri =
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                while (cursor.moveToNext()) {

                    val name = cursor.getString(nameIndex)
                        ?: continue

                    val number = cursor.getString(numberIndex)
                        ?: continue

                    if (number.isBlank()) continue

                    totalContacts++

                    val score = scoreName(name, wanted)

                    if (score > 0) {
                        matches.add(
                            Contact(name, number, score)
                        )
                    }
                }
            }

        } catch (_: Exception) {

            return "Contacts पढ़ने में समस्या आई। Contacts Permission जाँचें"
        }

        if (totalContacts == 0) {

            return "Jarvis को कोई फोन नंबर वाला Contact दिखाई नहीं दे रहा। Contacts Permission और SIM Contacts की उपलब्धता जाँचें"
        }

        if (matches.isEmpty()) {

            return "$spoken नाम का Contact नहीं मिला। फोन में सेव नाम के अनुसार दोबारा बोलिए"
        }

        val unique = matches
            .sortedWith(
                compareByDescending<Contact> { it.score }
                    .thenBy { it.name }
            )
            .distinctBy { digits(it.number) }

        val bestScore = unique.first().score

        val selected = unique.filter {
            it.score == bestScore
        }

        if (selected.size == 1) {

            pending = selected.first()

            return "${pending!!.name} का नंबर मिल गया है। क्या कॉल करूँ? हाँ कॉल करो या नहीं बोलिए"
        }

        choices = selected.take(5)

        val names = choices.mapIndexed { index, contact ->
            "${index + 1}: ${contact.name}"
        }.joinToString(", ")

        return "इस नाम से एक से अधिक नंबर मिले हैं। $names। पहला, दूसरा या सही विकल्प बोलिए"
    }

    fun confirm(
        context: Context,
        raw: String
    ): String {

        val text = clean(raw)

        if (text in noWords) {

            cancel()

            return "ठीक है, कॉल रद्द कर दी गई है"
        }

        if (choices.isNotEmpty()) {

            val index = choiceIndex(text)

            if (index == null || index !in choices.indices) {

                return "कृपया पहला, दूसरा या सही विकल्प बोलिए"
            }

            pending = choices[index]
            choices = emptyList()

            return "${pending!!.name} को कॉल करना है? हाँ कॉल करो या नहीं बोलिए"
        }

        val contact = pending
            ?: return "अभी कोई कॉल बाकी नहीं है"

        if (text !in yesWords) {

            return "कृपया हाँ कॉल करो या नहीं बोलिए"
        }

        pending = null

        return try {

            val uri = Uri.fromParts(
                "tel",
                contact.number,
                null
            )

            val canCall =
                context.checkSelfPermission(
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

            val intent = Intent(
                if (canCall) {
                    Intent.ACTION_CALL
                } else {
                    Intent.ACTION_DIAL
                },
                uri
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            if (canCall) {

                "${contact.name} को कॉल लगा रहा हूँ"

            } else {

                "${contact.name} का नंबर डायलर में खोल दिया है। कॉल बटन दबाएँ"
            }

        } catch (_: Exception) {

            "कॉल शुरू नहीं हो सकी। फोन अनलॉक करके दोबारा कोशिश करें"
        }
    }
}
