package com.example.jarvis

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private var listening = false
    private var speaking = false
    private var waitingForCommand = false

    private var nextListen: Runnable? = null

    private val manager by lazy {
        JarvisCommandManager(this)
    }

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this, this)

        val channel = NotificationChannel(
            CHANNEL,
            "Jarvis Voice Control",
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

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val openIntent = Intent(
            this,
            MainActivity::class.java
        )

        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(
            this,
            JarvisVoiceService::class.java
        ).setAction(STOP)

        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("JARVIS ULTIMATE 3.2")
            .setContentText("Hey Jarvis listening active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "STOP",
                    stopPending
                ).build()
            )
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        running = true
        waitingForCommand = false

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(this)
            listenAgain(700)
        } else {
            speak("वॉइस रिकग्निशन उपलब्ध नहीं है")
        }

        return START_NOT_STICKY
    }

    private fun listenAgain(delay: Long = 500) {

        if (!running || speaking || listening) return

        nextListen?.let { handler.removeCallbacks(it) }

        val task = Runnable {
            if (!running || speaking || listening) return@Runnable

            try {
                listening = true

                recognizer?.startListening(
                    Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {
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

            } catch (e: Exception) {
                listening = false
                listenAgain(2000)
            }
        }

        nextListen = task
        handler.postDelayed(task, delay)
    }

    override fun onResults(results: Bundle?) {

        listening = false

        val heard = results
            ?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )
            ?.firstOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()

        if (heard.isBlank()) {
            listenAgain()
            return
        }

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
            "जार्विस",
            "jarvis"
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

    // ALL COMMANDS THROUGH COMMAND MANAGER

    private fun executeCommand(command: String) {

        if (command.isBlank()) {
            speak("कृपया कमांड बताइए")
            return
        }

        try {
            val result = manager.execute(command)
            speak(result)
        } catch (e: Exception) {
            speak("कमांड चलाने में समस्या आई")
        }
    }

    private fun speak(message: String) {

        speaking = true

        nextListen?.let { handler.removeCallbacks(it) }
        nextListen = null

        if (listening) {
            try {
                recognizer?.cancel()
            } catch (_: Exception) {}
            listening = false
        }

        if (ready) {

            val result = tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_voice"
            )

            if (result != TextToSpeech.SUCCESS) {
                speaking = false
                listenAgain(1200)
            }

        } else {
            speaking = false
            listenAgain(1500)
        }
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts?.language = Locale("hi", "IN")
            ready = true

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        handler.post {
                            speaking = false
                            listenAgain(500)
                        }
                    }

                    override fun onError(id: String?) {
                        handler.post {
                            speaking = false
                            listenAgain(1200)
                        }
                    }
                }
            )
        }
    }

    override fun onError(error: Int) {

        listening = false

        if (!running || speaking) return

        val delay = when (error) {

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> 2500L

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stopSelf()
                return
            }

            else -> 1200L
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
        listening = false
        speaking = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}
