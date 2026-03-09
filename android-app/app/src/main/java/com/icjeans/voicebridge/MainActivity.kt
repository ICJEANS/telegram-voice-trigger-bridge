package com.icjeans.voicebridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat.startForegroundService
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val requestCodeSpeech = 1001
    private val requestCodeMic = 2001

    private lateinit var tokenInput: EditText
    private lateinit var chatIdInput: EditText
    private lateinit var triggersInput: EditText
    private lateinit var resultText: TextView

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenInput = findViewById(R.id.tokenInput)
        chatIdInput = findViewById(R.id.chatIdInput)
        triggersInput = findViewById(R.id.triggersInput)
        resultText = findViewById(R.id.resultText)

        findViewById<Button>(R.id.recordButton).setOnClickListener {
            saveConfig()
            ensureMicAndStartSpeech()
        }

        findViewById<Button>(R.id.startBgButton).setOnClickListener {
            saveConfig()
            ensureMicAndStartBackground()
        }

        findViewById<Button>(R.id.stopBgButton).setOnClickListener {
            stopService(Intent(this, BackgroundVoiceService::class.java))
            resultText.text = "백그라운드 감지 중지됨"
        }
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("voice_bridge", MODE_PRIVATE)
        prefs.edit()
            .putString("token", tokenInput.text.toString().trim())
            .putString("chatId", chatIdInput.text.toString().trim())
            .putString("triggers", triggersInput.text.toString().trim())
            .apply()
    }

    private fun ensureMicAndStartSpeech() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCodeMic)
            return
        }
        startSpeech()
    }

    private fun ensureMicAndStartBackground() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCodeMic)
            return
        }
        startForegroundService(this, Intent(this, BackgroundVoiceService::class.java))
        resultText.text = "백그라운드 감지 시작됨"
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "AI야로 시작해서 말해줘")
        }
        startActivityForResult(intent, requestCodeSpeech)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestCodeSpeech && resultCode == RESULT_OK) {
            val list = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = list?.firstOrNull().orEmpty()
            val prompt = extractPrompt(text, triggersInput.text.toString())

            if (prompt == null) {
                resultText.text = "트리거 미감지: $text"
                return
            }

            resultText.text = "전송 중: $prompt"
            sendToTelegram(prompt)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeMic && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeech()
        }
    }

    private fun extractPrompt(text: String, triggerCsv: String): String? {
        val triggers = triggerCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val input = text.trim()
        if (input.isEmpty()) return null

        for (trigger in triggers) {
            if (input.startsWith(trigger)) {
                return input.removePrefix(trigger).trim(' ', ':', ',', '-')
                    .ifEmpty { null }
            }
        }
        return null
    }

    private fun sendToTelegram(prompt: String) {
        val token = tokenInput.text.toString().trim()
        val chatId = chatIdInput.text.toString().trim()
        if (token.isEmpty() || chatId.isEmpty()) {
            resultText.text = "토큰/채팅ID를 입력해줘"
            return
        }

        Thread {
            try {
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val bodyJson = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", prompt)
                }
                val req = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(req).execute().use { resp ->
                    runOnUiThread {
                        resultText.text = if (resp.isSuccessful) "전송 완료: $prompt" else "전송 실패: ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultText.text = "에러: ${e.message}"
                }
            }
        }.start()
    }
}
