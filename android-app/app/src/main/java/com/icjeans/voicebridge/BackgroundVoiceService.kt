package com.icjeans.voicebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
                    val text = texts?.firstOrNull().orEmpty()
                    val sent = processText(text)
                    val status = if (sent) "전송됨: $text" else "트리거 미감지"
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(101, buildNotification(status))
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
        android.os.Handler(mainLooper).postDelayed({
            if (listening) startListeningOnce()
        }, 800)
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

    private fun processText(text: String): Boolean {
        val prefs = getSharedPreferences("voice_bridge", MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        val chatId = prefs.getString("chatId", "") ?: ""
        val triggerCsv = prefs.getString("triggers", "AI야,AI") ?: "AI야,AI"
        if (token.isBlank() || chatId.isBlank()) return false

        val prompt = extractPrompt(text, triggerCsv) ?: return false
        return sendToTelegram(token, chatId, prompt)
    }

    private fun extractPrompt(text: String, triggerCsv: String): String? {
        val triggers = triggerCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val input = text.trim()
        if (input.isEmpty()) return null
        for (trigger in triggers) {
            if (input.startsWith(trigger)) {
                return input.removePrefix(trigger).trim(' ', ':', ',', '-').ifEmpty { null }
            }
        }
        return null
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

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Voice Bridge 백그라운드")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
