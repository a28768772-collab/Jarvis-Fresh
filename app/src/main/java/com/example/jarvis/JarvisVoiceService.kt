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
        const val SLEEP = "com.example.jarvis.SLEEP_VOICE"
        const val WAKE = "com.example.jarvis.WAKE_VOICE"

        const val EXTRA_DIRECT_LISTEN = "direct_listen"
        const val BUBBLE_STATE = "com.example.jarvis.BUBBLE_STATE"

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
    private var stopping = false

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

        when (intent?.action) {

            STOP, SLEEP -> {
                enterSleepMode()
                return START_NOT_STICKY
            }
        }

        if (running || stopping) return START_NOT_STICKY

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
            .setContentTitle("JARVIS ULTIMATE 3.6")
            .setContentText("Jarvis microphone active")
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

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

        } catch (_: Exception) {

            stopSelf()
            return START_NOT_STICKY
        }

        running = true
        stopping = false

        waitingForCommand = intent?.getBooleanExtra(
            EXTRA_DIRECT_LISTEN,
            false
        ) == true

        if (waitingForCommand) {
            notifyBubble("awake")
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {

            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(this)

            listenAgain(700)

        } else {

            enterSleepMode()
        }

        return START_NOT_STICKY
    }

    private fun normalize(raw: String): String {

        return raw.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("[,.!?।]+"), "")
            .replace(Regex("\\s+"), " ")
    }

    private fun notifyBubble(state: String) {

        try {

            sendBroadcast(
                Intent(BUBBLE_STATE).apply {

                    setPackage(packageName)

                    putExtra("state", state)
                }
            )

        } catch (_: Exception) {}
    }

    private fun isStopCommand(raw: String): Boolean {

        val text = normalize(raw)

        return text in listOf(

            "jarvis stop",
            "hey jarvis stop",
            "stop jarvis",

            "जार्विस स्टॉप",
            "जारविस स्टॉप",
            "जार्वेज स्टॉप",
            "जारवेज स्टॉप",

            "हे जार्विस स्टॉप",
            "हे जारविस स्टॉप",
            "हे जार्वेज स्टॉप",

            "जार्विस बंद हो जाओ",
            "जारविस बंद हो जाओ",
            "जार्वेज बंद हो जाओ",

            "जार्विस सुनना बंद करो",
            "जारविस सुनना बंद करो",

            "stop listening",
            "jarvis stop listening",

            "जार्विस चुप हो जाओ"
        )
    }

    // FULL SLEEP: Mic और Voice Service बंद।

    private fun enterSleepMode() {

        if (stopping) return

        stopping = true
        running = false
        waitingForCommand = false
        listening = false
        speaking = false

        // Bubble को 😴 दिखाने का संदेश।
        notifyBubble("sleep")

        nextListen?.let {
            handler.removeCallbacks(it)
        }

        nextListen = null
        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}

        recognizer = null

        try {
            tts?.stop()
        } catch (_: Exception) {}

        // Foreground Service और Microphone Session बंद।
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun listenAgain(delay: Long = 500) {

        if (!running || stopping || speaking || listening) return

        nextListen?.let {
            handler.removeCallbacks(it)
        }

        val task = Runnable {

            nextListen = null

            if (!running || stopping || speaking || listening) {
                return@Runnable
            }

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

            } catch (_: Exception) {

                listening = false
                listenAgain(2000)
            }
        }

        nextListen = task
        handler.postDelayed(task, delay)
    }

    override fun onResults(results: Bundle?) {

        listening = false

        if (!running || stopping) return

        val heard = normalize(
            results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )?.firstOrNull().orEmpty()
        )

        if (heard.isBlank()) {
            listenAgain()
            return
        }

        // Stop Command को सबसे पहले पहचानें।
        if (isStopCommand(heard)) {

            enterSleepMode()
            return
        }

        val wakeWords = listOf(

            "hey jarvis",
            "हे जार्विस",
            "हे जारविस",
            "हे जार्वेज",

            "हाय जार्विस",

            "जार्विस",
            "जारविस",
            "जार्वेज",
            "जारवेज",

            "jarvis"
        )

        val wake = wakeWords.firstOrNull {
            heard.contains(it)
        }

        // Bubble Greeting के बाद Direct Command।
        if (waitingForCommand) {

            waitingForCommand = false

            if (wake != null) {

                val remaining = heard.substringAfter(wake).trim()

                if (remaining.isEmpty()) {

                    waitingForCommand = true
                    speak("जी सर, बताइए")

                } else {

                    executeCommand(remaining)
                }

            } else {

                executeCommand(heard)
            }

            return
        }

        // सामान्य Voice Start में Wake Word जरूरी।
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

        if (!running || stopping) return

        val text = normalize(command)

        if (
            isStopCommand(text) ||
            text == "stop" ||
            text == "स्टॉप" ||
            text == "बंद हो जाओ"
        ) {

            enterSleepMode()
            return
        }

        if (text.isBlank()) {

            waitingForCommand = true
            speak("कृपया कमांड बताइए")
            return
        }

        try {

            val result = manager.execute(command)
            speak(result)

        } catch (_: Exception) {

            speak("कमांड चलाने में समस्या आई")
        }
    }

    private fun speak(message: String) {

        if (!running || stopping) return

        speaking = true

        nextListen?.let {
            handler.removeCallbacks(it)
        }

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

                            if (!stopping && running) {

                                speaking = false
                                listenAgain(500)
                            }
                        }
                    }

                    override fun onError(id: String?) {

                        handler.post {

                            if (!stopping && running) {

                                speaking = false
                                listenAgain(1200)
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onError(error: Int) {

        listening = false

        if (!running || stopping || speaking) return

        val delay = when (error) {

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> 2500L

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {

                enterSleepMode()
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
        stopping = true
        listening = false
        speaking = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}

        recognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}

        tts = null

        super.onDestroy()
    }
}
