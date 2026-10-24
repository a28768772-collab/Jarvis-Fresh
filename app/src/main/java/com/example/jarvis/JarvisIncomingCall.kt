package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager

object JarvisIncomingCall {

    fun answer(context: Context): String {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "इस Android Version में कॉल उठाने की सुविधा उपलब्ध नहीं है"
        }

        if (
            context.checkSelfPermission(
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "पहले Jarvis को Answer Phone Calls की अनुमति दें"
        }

        return try {

            val telecom = context.getSystemService(
                Context.TELECOM_SERVICE
            ) as TelecomManager

            @Suppress("DEPRECATION")
            telecom.acceptRingingCall()

            "जी सर, कॉल उठाने का अनुरोध भेज दिया है"

        } catch (_: Exception) {

            "कॉल नहीं उठ सकी। फोन की Call Permission जाँचें"
        }
    }

    fun reject(context: Context): String {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return "इस Android Version में कॉल काटने की सुविधा उपलब्ध नहीं है"
        }

        if (
            context.checkSelfPermission(
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "पहले Jarvis को Answer Phone Calls की अनुमति दें"
        }

        return try {

            val telecom = context.getSystemService(
                Context.TELECOM_SERVICE
            ) as TelecomManager

            @Suppress("DEPRECATION")
            val success = telecom.endCall()

            if (success) {
                "जी सर, कॉल काट दी गई है"
            } else {
                "अभी कोई कॉल नहीं काटी जा सकी"
            }

        } catch (_: Exception) {

            "कॉल नहीं काटी जा सकी"
        }
    }
}
