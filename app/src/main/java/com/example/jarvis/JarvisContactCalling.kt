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

    private val yesWords = setOf(
        "हाँ", "हां", "हा",
        "yes", "yes call",
        "हाँ कॉल करो", "हां कॉल करो",
        "हां कॉल कर दो", "हाँ कॉल कर दो",
        "कॉल करो", "कॉल कर दो",
        "फोन लगाओ", "जी हाँ",
        "जी हां", "कर दो"
    )

    private val noWords = setOf(
        "नहीं", "नही", "ना",
        "no", "cancel",
        "कैंसिल", "रद्द करो",
        "मत करो", "कॉल मत करो"
    )

    private fun clean(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?।]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(value: String): String {
        return clean(value)
            .replace(Regex("[\\s._-]+"), "")
    }

    private fun digits(value: String): String {
        return value.filter { it.isDigit() }
    }

    private fun requestedName(value: String): String {
        var name = clean(value)

        val prefixes = listOf(
            "कृपया ", "please ",
            "jarvis ", "hey jarvis ",
            "जार्विस ", "जारविस ",
            "जार्वेज ", "जारवेज "
        )

        for (prefix in prefixes) {
            if (name.startsWith(prefix)) {
                name = name.removePrefix(prefix).trim()
            }
        }

        val suffixes = listOf(
            " को कॉल लगाओ",
            " को कॉल करो",
            " को फोन लगाओ",
            " को फोन करो",
            " को call करो",
            " को call लगाओ",
            " को कॉल",
            " को call"
        )

        for (suffix in suffixes) {
            if (name.endsWith(suffix)) {
                name = name.removeSuffix(suffix).trim()
                break
            }
        }

        return name
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

        // पूरा नाम बिल्कुल समान।
        if (a == b) return 100

        val savedWords = saved.split(" ")
            .filter { it.isNotBlank() }

        val spokenWords = spoken.split(" ")
            .filter { it.isNotBlank() }

        // सेव नाम का कोई पूरा शब्द समान।
        if (savedWords.any { normalize(it) == b }) {
            return 85
        }

        // बोले गए सभी शब्द सेव नाम में मौजूद।
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

        // नाम शुरुआत में मौजूद।
        if (a.startsWith(b)) return 70

        // नाम बीच में मौजूद।
        if (b.length >= 3 && a.contains(b)) return 60

        return 0
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
            return "Jarvis को Contacts की अनुमति नहीं मिली है। ऐप की Permissions में Contacts Allow करें"
        }

        val wanted = requestedName(spokenName)

        if (wanted.isBlank()) {
            return "किस व्यक्ति को कॉल करना है?"
        }

        val matches = mutableListOf<Contact>()

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

                    val score = scoreName(name, wanted)

                    if (score > 0) {

                        matches.add(
                            Contact(
                                name = name,
                                number = number,
                                score = score
                            )
                        )
                    }
                }
            }

        } catch (_: Exception) {

            return "Contacts पढ़ने में समस्या आई। Contacts Permission जाँचें"
        }

        if (matches.isEmpty()) {

            return "$wanted नाम का Contact नहीं मिला। फोन में सेव नाम के अनुसार दोबारा बोलिए"
        }

        val unique = matches
            .sortedByDescending { it.score }
            .distinctBy { digits(it.number) }

        val bestScore = unique.first().score

        // पहले सबसे अच्छे मिलान वाले नाम लें।
        val selected = unique.filter {
            it.score == bestScore
        }

        if (selected.size == 1) {

            pending = selected.first()

            return "${pending!!.name} का नंबर मिल गया है। क्या कॉल करूँ? हाँ कॉल करो या नहीं बोलिए"
        }

        // एक जैसे नाम मिलने पर विकल्प चुनने दें।
        choices = selected.take(5)

        val names = choices.mapIndexed { index, contact ->
            "${index + 1}: ${contact.name}"
        }.joinToString(", ")

        return "इस नाम से कई नंबर मिले हैं। $names। किसे कॉल करना है? पहला, दूसरा या नंबर बोलिए"
    }

    private fun choiceIndex(text: String): Int? {

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

    fun confirm(
        context: Context,
        raw: String
    ): String {

        val text = clean(raw)

        if (text in noWords) {

            cancel()

            return "ठीक है, कॉल रद्द कर दी गई है"
        }

        // यदि कई Contacts मिले हैं तो पहले व्यक्ति चुनें।
        if (choices.isNotEmpty()) {

            val index = choiceIndex(text)

            if (index == null || index !in choices.indices) {

                return "कृपया पहला, दूसरा या सही नंबर बोलिए"
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

        val number = contact.number

        return try {

            val uri = Uri.fromParts(
                "tel",
                number,
                null
            )

            // Permission होने पर पुष्टि के बाद कॉल।
            // Permission न होने पर Dialer खुलेगा।
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

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
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
