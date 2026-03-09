package com.icjeans.voicebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

class BackgroundVoiceService : Service() {

    private val channelId = "voice_bridge_bg"
    private val client = OkHttpClient()
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false

    // Conversation state: after trigger heard, collect phrases and flush on 1s silence.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val buffer = StringBuilder()
    private var waitingAfterTrigger = false
    private val flushRunnable = Runnable { flushBufferedPrompt() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(101, buildNotification("백그라운드 감지 준비 중"))
        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        listening = false
        mainHandler.removeCallbacks(flushRunnable)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

                override fun onError(error: Int) {
                    if (listening) restartListeningSoon()
                }

                override fun onResults(results: android.os.Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = texts?.firstOrNull().orEmpty().trim()
                    processStreamingText(text)
                    if (listening) restartListeningSoon()
                }
            })
        }
    }

    private fun startListeningLoop() {
        listening = true
        startListeningOnce()
    }

    private fun restartListeningSoon() {
        mainHandler.postDelayed({
            if (listening) startListeningOnce()
        }, 250)
    }

    private fun startListeningOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processStreamingText(text: String) {
        if (text.isBlank()) return

        val prefs = getSharedPreferences("voice_bridge", MODE_PRIVATE)
        val triggerCsv = prefs.getString("triggers", "AI야,AI") ?: "AI야,AI"
        val triggers = triggerCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (!waitingAfterTrigger) {
            val (matched, remainder) = detectTrigger(text, triggers)
            if (!matched) {
                updateNotification("트리거 미감지")
                return
            }
            waitingAfterTrigger = true
            if (remainder.isNotBlank()) appendToBuffer(remainder)
            scheduleFlushIn1s()
            updateNotification("트리거 감지됨, 입력 수집 중...")
            return
        }

        // Already in capture mode; keep collecting until 1s silence.
        appendToBuffer(text)
        scheduleFlushIn1s()
        updateNotification("추가 입력 수집 중...")
    }

    private fun detectTrigger(text: String, triggers: List<String>): Pair<Boolean, String> {
        val input = text.trim()
        for (trigger in triggers) {
            if (input.startsWith(trigger)) {
                val remainder = input.removePrefix(trigger).trim(' ', ':', ',', '-')
                return true to remainder
            }
        }
        return false to ""
    }

    private fun appendToBuffer(part: String) {
        if (part.isBlank()) return
        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(part.trim())
    }

    private fun scheduleFlushIn1s() {
        mainHandler.removeCallbacks(flushRunnable)
        mainHandler.postDelayed(flushRunnable, 1000)
    }

    private fun flushBufferedPrompt() {
        val prompt = buffer.toString().trim()
        buffer.clear()
        waitingAfterTrigger = false

        if (prompt.isBlank()) {
            updateNotification("입력 없음 (전송 안 함)")
            return
        }

        val prefs = getSharedPreferences("voice_bridge", MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        val chatId = prefs.getString("chatId", "") ?: ""
        if (token.isBlank() || chatId.isBlank()) {
            updateNotification("토큰/채팅ID 없음")
            return
        }

        val sent = sendToTelegram(token, chatId, prompt)
        updateNotification(if (sent) "전송됨: $prompt" else "전송 실패")
    }

    private fun sendToTelegram(token: String, chatId: String, prompt: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val bodyJson = JSONObject().apply {
                put("chat_id", chatId)
                put("text", prompt)
            }
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Voice Bridge BG", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(101, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Voice Bridge 백그라운드")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
