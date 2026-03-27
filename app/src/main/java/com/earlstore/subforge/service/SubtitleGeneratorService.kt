package com.earlstore.subforge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.earlstore.subforge.model.SubtitleItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SubtitleGeneratorService : Service() {

    companion object {
        const val CHANNEL_ID = "subforge_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _subtitles = MutableStateFlow<List<SubtitleItem>>(emptyList())
    val subtitles: StateFlow<List<SubtitleItem>> = _subtitles

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _statusText = MutableStateFlow("Ready")
    val statusText: StateFlow<String> = _statusText

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentIndex = 0
    private var currentStartTime = 0L
    private var segmentDuration = 5000L // 5 second segments

    inner class LocalBinder : Binder() {
        fun getService(): SubtitleGeneratorService = this@SubtitleGeneratorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("SubForge running...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    fun startGeneration(videoUri: Uri, language: String) {
        if (_isProcessing.value) return

        _isProcessing.value = true
        _subtitles.value = emptyList()
        _progress.value = 0f
        _statusText.value = "Initializing speech recognition..."
        currentIndex = 0
        currentStartTime = 0L

        scope.launch {
            try {
                processAudio(videoUri, language)
            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"
                _isProcessing.value = false
            }
        }
    }

    private suspend fun processAudio(videoUri: Uri, language: String) {
        withContext(Dispatchers.Main) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@SubtitleGeneratorService)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    _statusText.value = "Listening for speech..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        else -> "Recognition error: $error"
                    }
                    _statusText.value = errorMsg

                    // Continue to next segment
                    currentStartTime += segmentDuration
                    if (currentStartTime < 300000) { // Max 5 minutes demo
                        startNextSegment(language)
                    } else {
                        finishProcessing()
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""

                    if (text.isNotBlank()) {
                        currentIndex++
                        val endTime = currentStartTime + segmentDuration
                        val item = SubtitleItem(
                            index = currentIndex,
                            startTime = currentStartTime,
                            endTime = endTime,
                            text = text
                        )
                        _subtitles.value = _subtitles.value + item
                        _statusText.value = "Generated: $currentIndex subtitles"
                    }

                    currentStartTime += segmentDuration
                    _progress.value = (currentStartTime.toFloat() / 300000f).coerceAtMost(1f)

                    if (currentStartTime < 300000) {
                        startNextSegment(language)
                    } else {
                        finishProcessing()
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            startNextSegment(language)
        }
    }

    private fun startNextSegment(language: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun finishProcessing() {
        _isProcessing.value = false
        _progress.value = 1f
        _statusText.value = "Done! ${_subtitles.value.size} subtitles generated"
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun stopGeneration() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isProcessing.value = false
        _statusText.value = "Stopped. ${_subtitles.value.size} subtitles generated"
    }

    fun exportSrt(): String {
        val sb = StringBuilder()
        _subtitles.value.forEach { item ->
            sb.appendLine(item.toSrt())
        }
        return sb.toString()
    }

    fun exportVtt(): String {
        val sb = StringBuilder("WEBVTT\n\n")
        _subtitles.value.forEach { item ->
            sb.appendLine(item.toVtt())
        }
        return sb.toString()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SubForge Processing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows subtitle generation progress"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SubForge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
