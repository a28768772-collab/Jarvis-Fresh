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

        // MainActivity में भी इन्हीं preference keys का उपयोग करेंगे।
        private const val PREFS = "jarvis_voice_settings"
        private const val KEY_STYLE = "voice_style"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "speech_pitch"
        private const val KEY_VOICE_NAME = "voice_name"
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
    private var restartScheduled = false

    private var nextListen: Runnable? = null
    private var pendingSpeech: String? = null

    private val manager by lazy {
        JarvisCommandManager(this)
    }

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this, this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "Jarvis Voice Control",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
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

            WAKE -> {
                // सामान्य voice start जारी रहेगा।
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

        val openIntent = Intent(this, MainActivity::class.java)

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
            .setContentText("Jarvis voice control active")
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
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        stopping = false
        running = true

        waitingForCommand = intent?.getBooleanExtra(
            EXTRA_DIRECT_LISTEN,
            false
        ) == true

        if (waitingForCommand) {
            notifyBubble("awake")
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                recognizer =
                    SpeechRecognizer.createSpeechRecognizer(this)

                recognizer?.setRecognitionListener(this)
                listenAgain(1000L)
            } catch (_: Exception) {
                enterSleepMode()
            }
        } else {
            speak("इस फोन में वॉइस रिकग्निशन उपलब्ध नहीं है")
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
        } catch (_: Exception) {
        }
    }

    private fun isStopCommand(raw: String): Boolean {
        return normalize(raw) in listOf(
            "jarvis stop",
            "hey jarvis stop",
            "stop jarvis",
            "जार्विस स्टॉप",
            "जारविस स्टॉप",
            "जार्वेज स्टॉप",
            "जारवेज स्टॉप",
            "हे जार्विस स्टॉप",
            "हे जारविस स्टॉप",
            "जार्विस बंद हो जाओ",
            "जारविस बंद हो जाओ",
            "जार्विस सुनना बंद करो",
            "जारविस सुनना बंद करो",
            "stop listening",
            "jarvis stop listening",
            "जार्विस चुप हो जाओ"
        )
    }

    private fun enterSleepMode() {
        if (stopping) return

        stopping = true
        running = false
        waitingForCommand = false
        listening = false
        speaking = false
        restartScheduled = false
        pendingSpeech = null

        notifyBubble("sleep")

        nextListen?.let {
            handler.removeCallbacks(it)
        }
        nextListen = null
        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        try {
            tts?.stop()
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun listenAgain(delay: Long = 1200L) {
        if (!running || stopping || speaking || listening) return
        if (restartScheduled) return

        restartScheduled = true

        val task = Runnable {
            restartScheduled = false
            nextListen = null

            if (!running || stopping || speaking || listening) {
                return@Runnable
            }

            val currentRecognizer = recognizer

            if (currentRecognizer == null) {
                listenAgain(2500L)
                return@Runnable
            }

            try {
                listening = true

                currentRecognizer.startListening(
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
                            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
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
                listenAgain(2500L)
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
            listenAgain(1200L)
            return
        }

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

        if (waitingForCommand) {
            waitingForCommand = false

            if (wake != null) {
                val remaining = heard.substringAfter(wake).trim()

                if (remaining.isEmpty()) {
                    waitingForCommand = true
                    speak("जी, बताइए")
                } else {
                    executeCommand(remaining)
                }
            } else {
                executeCommand(heard)
            }

            return
        }

        if (wake != null) {
            val remaining = heard.substringAfter(wake).trim()

            if (remaining.isNotEmpty()) {
                executeCommand(remaining)
            } else {
                waitingForCommand = true
                speak("जी, बताइए")
            }
        } else {
            listenAgain(1000L)
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

        if (message.isBlank()) {
            speaking = false
            listenAgain(800L)
            return
        }

        speaking = true
        pendingSpeech = message

        nextListen?.let {
            handler.removeCallbacks(it)
        }
        nextListen = null
        restartScheduled = false

        if (listening) {
            try {
                recognizer?.cancel()
            } catch (_: Exception) {
            }
            listening = false
        }

        if (ready) {
            speakPendingMessage()
        }
    }

    private fun speakPendingMessage() {
        if (!running || stopping || !ready) return

        val message = pendingSpeech ?: return
        pendingSpeech = null

        val result = try {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                Bundle(),
                "jarvis_voice"
            ) ?: TextToSpeech.ERROR
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        if (result != TextToSpeech.SUCCESS) {
            speaking = false
            listenAgain(2000L)
        }
    }

    private fun applySavedVoiceSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        val style = prefs.getString(KEY_STYLE, "natural") ?: "natural"
        val rate = prefs.getFloat(KEY_RATE, 1.0f).coerceIn(0.5f, 2.0f)
        val savedPitch = prefs.getFloat(KEY_PITCH, 1.0f)
            .coerceIn(0.5f, 2.0f)

        // Robot style को pitch और speed से approximate किया जाता है।
        val pitch = when (style) {
            "robotic" -> (savedPitch * 0.8f).coerceIn(0.5f, 2.0f)
            "female" -> savedPitch.coerceAtLeast(1.05f).coerceAtMost(2.0f)
            "male" -> savedPitch.coerceAtMost(0.95f).coerceAtLeast(0.5f)
            else -> savedPitch
        }

        try {
            tts?.setSpeechRate(
                if (style == "robotic") {
                    (rate * 0.9f).coerceIn(0.5f, 2.0f)
                } else {
                    rate
                }
            )
            tts?.setPitch(pitch)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voiceName = prefs.getString(KEY_VOICE_NAME, null)
                val availableVoices = tts?.voices.orEmpty()

                val selectedVoice = availableVoices.firstOrNull {
                    it.name == voiceName
                }

                if (selectedVoice != null) {
                    tts?.voice = selectedVoice
                } else {
                    // यदि saved voice उपलब्ध नहीं है, तो Hindi voice खोजें।
                    val hindiVoice = availableVoices.firstOrNull {
                        it.locale.language.equals("hi", ignoreCase = true)
                    }

                    if (hindiVoice != null) {
                        tts?.voice = hindiVoice
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            return
        }

        val languageResult = try {
            tts?.setLanguage(Locale("hi", "IN"))
                ?: TextToSpeech.ERROR
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        ready =
            languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
            languageResult != TextToSpeech.ERROR

        if (!ready) {
            // Hindi voice unavailable हो तो device default language आजमाएँ।
            val fallback = try {
                tts?.setLanguage(Locale.getDefault())
                    ?: TextToSpeech.ERROR
            } catch (_: Exception) {
                TextToSpeech.ERROR
            }

            ready =
                fallback != TextToSpeech.LANG_MISSING_DATA &&
                fallback != TextToSpeech.LANG_NOT_SUPPORTED &&
                fallback != TextToSpeech.ERROR
        }

        if (!ready) return

        applySavedVoiceSettings()

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                }

                override fun onDone(id: String?) {
                    handler.post {
                        if (!stopping && running) {
                            speaking = false
                            listenAgain(700L)
                        }
                    }
                }

                override fun onError(id: String?) {
                    handler.post {
                        if (!stopping && running) {
                            speaking = false
                            listenAgain(1500L)
                        }
                    }
                }
            }
        )

        if (running && pendingSpeech != null) {
            speakPendingMessage()
        } else if (running) {
            speaking = false
            listenAgain(800L)
        }
    }

    override fun onError(error: Int) {
        listening = false

        if (!running || stopping || speaking) return

        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            enterSleepMode()
            return
        }

        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> 2500L

            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1200L

            else -> 1800L
        }

        listenAgain(delay)
    }

    override fun onReadyForSpeech(params: Bundle?) {
    }

    override fun onBeginningOfSpeech() {
    }

    override fun onRmsChanged(rmsdB: Float) {
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
    }

    override fun onPartialResults(partialResults: Bundle?) {
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        stopping = true
        listening = false
        speaking = false
        restartScheduled = false
        pendingSpeech = null

        handler.removeCallbacksAndMessages(null)
        nextListen = null

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null

        super.onDestroy()
    }
}
