package com.example.jarvis

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.accessibilityservice.AccessibilityService
import java.util.Locale

class JarvisVoiceService : Service(),
    RecognitionListener,
    TextToSpeech.OnInitListener {

    companion object {
        const val STOP = "com.example.jarvis.STOP_VOICE"
        private const val CHANNEL = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var running = false
    private var waitingForCommand = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)

        val channel = NotificationChannel(
            CHANNEL,
            "Jarvis Voice",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (running) return START_NOT_STICKY

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        val openIntent = Intent(this, MainActivity::class.java)

        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceService::class.java)
            .setAction(STOP)

        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("JARVIS 3.1")
            .setContentText("Hey Jarvis listening active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .addAction(
                Notification.Action.Builder(
                    null, "STOP", stopPending
                ).build()
            )
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo
                .FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        running = true
        waitingForCommand = false

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(this)
            listenAgain()
        } else {
            speak("वॉइस रिकग्निशन उपलब्ध नहीं है")
        }

        return START_NOT_STICKY
    }

    private fun listenAgain(delay: Long = 500) {
        if (!running) return

        handler.postDelayed({
            if (!running) return@postDelayed

            try {
                recognizer?.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            "hi-IN"
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                            false
                        )
                    }
                )
            } catch (_: Exception) {
                listenAgain(2000)
            }
        }, delay)
    }

    override fun onResults(results: Bundle?) {
        val heard = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()

        if (waitingForCommand) {
            waitingForCommand = false
            executeCommand(heard)
            return
        }

        val wakeWords = listOf(
            "hey jarvis",
            "हे जार्विस",
            "हे जारविस",
            "हाय जार्विस",
            "जार्विस"
        )

        val wake = wakeWords.firstOrNull {
            heard.contains(it)
        }

        if (wake != null) {
            val remaining = heard.substringAfter(wake).trim()

            if (remaining.isNotEmpty()) {
                executeCommand(remaining)
            } else {
                waitingForCommand = true
                speak("जी सर, बताइए")
            }
        } else {
            listenAgain()
        }
    }

    private fun executeCommand(command: String) {
        val service = JarvisAccessibility.active

        val action = when {
            command.contains("होम") || command == "home" ->
                AccessibilityService.GLOBAL_ACTION_HOME

            command.contains("बैक") ||
            command.contains("पीछे") ->
                AccessibilityService.GLOBAL_ACTION_BACK

            command.contains("रीसेंट") ||
            command.contains("recent") ->
                AccessibilityService.GLOBAL_ACTION_RECENTS

            command.contains("नोटिफिकेशन") ->
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS

            command.contains("क्विक सेटिंग") ->
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS

            else -> null
        }

        if (action != null) {
            if (service?.performGlobalAction(action) == true) {
                speak("जी सर")
            } else {
                speak("पहले मोबाइल कंट्रोल चालू करें")
            }
            return
        }

        if (command.contains("नीचे स्क्रॉल")) {
            speak(
                if (service?.scroll(false) == true)
                    "जी सर" else "स्क्रॉल नहीं हो सका"
            )
            return
        }

        if (command.contains("ऊपर स्क्रॉल")) {
            speak(
                if (service?.scroll(true) == true)
                    "जी सर" else "स्क्रॉल नहीं हो सका"
            )
            return
        }

        speak("यह कमांड अभी उपलब्ध नहीं है")
    }

    private fun speak(message: String) {
        try {
            recognizer?.cancel()
        } catch (_: Exception) {}

        if (ready) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_voice"
            )
        } else {
            listenAgain(1500)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            ready = true

            tts?.setOnUtteranceProgressListener(
                object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        handler.post { listenAgain(400) }
                    }

                    override fun onError(id: String?) {
                        handler.post { listenAgain(1000) }
                    }
                }
            )
        }
    }

    override fun onError(error: Int) {
        if (!running) return

        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> 2000L
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stopSelf()
                return
            }
            else -> 1000L
        }

        listenAgain(delay)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
