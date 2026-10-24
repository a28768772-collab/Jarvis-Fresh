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
        val number: String
    )

    private var pending: Contact? = null

    fun isConfirmation(text: String): Boolean {
        return pending != null
    }

    fun cancel() {
        pending = null
    }

    fun findContact(context: Context, spokenName: String): String {
        pending = null

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "पहले Jarvis को Contacts की अनुमति दें"
        }

        val wanted = normalize(spokenName)
        if (wanted.isBlank()) {
            return "किस व्यक्ति को कॉल करना है?"
        }

        val matches = mutableListOf<Contact>()

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            context.contentResolver.query(
                uri, projection, null, null, null
            )?.use { cursor ->

                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue

                    if (normalize(name).contains(wanted)) {
                        matches.add(Contact(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            return "Contacts पढ़ने में समस्या आई"
        }

        val exact = matches.filter {
            normalize(it.name) == wanted
        }

        val selected = (
            if (exact.isNotEmpty()) exact else matches
        ).distinctBy {
            it.number.filter { character -> character.isDigit() }
        }

        if (selected.isEmpty()) {
            return "$spokenName नाम का Contact नहीं मिला"
        }

        if (selected.size > 1) {
            return "इस नाम से एक से अधिक नंबर मिले हैं। कृपया पूरा और अलग नाम बोलें।"
        }

        pending = selected.first()

        return "${pending!!.name} का नंबर मिल गया। क्या कॉल करूँ? हाँ कॉल करो या नहीं बोलिए।"
    }

    fun confirm(context: Context, raw: String): String {
        val contact = pending
            ?: return "अभी कोई कॉल बाकी नहीं है"

        val text = raw.lowercase(Locale.ROOT).trim()

        if (
            text.contains("नहीं") ||
            text.contains("मत करो") ||
            text.contains("कैंसिल") ||
            text.contains("cancel") ||
            text == "no"
        ) {
            pending = null
            return "ठीक है, कॉल रद्द कर दी गई है"
        }

        val approved = text in listOf(
            "हाँ", "हां", "yes",
            "हां कॉल करो", "हाँ कॉल करो",
            "कॉल करो", "कॉल कर दो", "yes call"
        )

        if (!approved) {
            return "कृपया हाँ कॉल करो या नहीं बोलिए"
        }

        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pending = null
            return "पहले Jarvis को Phone Call की अनुमति दें"
        }

        pending = null

        return try {
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.fromParts("tel", contact.number, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            "${contact.name} को कॉल लगा रहा हूँ"

        } catch (e: Exception) {
            "कॉल शुरू नहीं हो सकी। फोन अनलॉक करके दोबारा कोशिश करें"
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(Regex("[\\s._-]+"), "")
            .trim()
    }
}
