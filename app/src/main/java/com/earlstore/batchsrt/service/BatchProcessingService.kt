package com.earlstore.batchsrt.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.earlstore.batchsrt.R
import com.earlstore.batchsrt.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BatchProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "batch_processing"
        const val NOTIFICATION_ID = 200
        private val _processingState = MutableStateFlow<BatchProcessState>(BatchProcessState.Idle)
        val processingState = _processingState.asStateFlow()
        private val _fileStates = MutableStateFlow<List<VideoFile>>(emptyList())
        val fileStates = _fileStates.asStateFlow()
        private var currentSettings = BatchSettings()

        fun updateFiles(files: List<VideoFile>) { _fileStates.value = files }
        fun updateSettings(settings: BatchSettings) { currentSettings = settings }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentFileIndex = 0
    private val subtitles = mutableListOf<SubtitleEntry>()
    private var segmentStart = 0L
    private var isListening = false

    data class SubtitleEntry(val index: Int, val startMs: Long, val endMs: Long, val text: String)

    sealed class BatchProcessState {
        object Idle : BatchProcessState()
        data class Processing(val currentFile: Int, val totalFiles: Int, val fileName: String, val progress: Float) : BatchProcessState()
        object Completed : BatchProcessState()
        data class Error(val message: String) : BatchProcessState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Preparing batch processing...", 0f)
        startForeground(NOTIFICATION_ID, notification)
        startBatchProcessing()
        return START_NOT_STICKY
    }

    private fun startBatchProcessing() {
        scope.launch {
            val files = _fileStates.value
            if (files.isEmpty()) {
                _processingState.value = BatchProcessState.Error("No files to process")
                stopSelf()
                return@launch
            }

            for (i in files.indices) {
                currentFileIndex = i
                val file = files[i]
                _processingState.value = BatchProcessState.Processing(i + 1, files.size, file.name, 0f)
                updateNotification("Processing ${file.name} (${i + 1}/${files.size})", 0f)

                val updatedFiles = _fileStates.value.toMutableList()
                updatedFiles[i] = file.copy(status = ProcessingStatus.PROCESSING)
                _fileStates.value = updatedFiles

                try {
                    val result = processFile(file)
                    val completed = _fileStates.value.toMutableList()
                    completed[i] = file.copy(
                        status = ProcessingStatus.COMPLETED,
                        progress = 1f,
                        subtitleCount = result,
                        outputPath = getOutputPath(file.name)
                    )
                    _fileStates.value = completed
                } catch (e: Exception) {
                    val failed = _fileStates.value.toMutableList()
                    failed[i] = file.copy(
                        status = ProcessingStatus.FAILED,
                        error = e.message ?: "Unknown error"
                    )
                    _fileStates.value = failed
                }
            }

            _processingState.value = BatchProcessState.Completed
            updateNotification("Batch processing complete!", 1f)
            delay(2000)
            stopSelf()
        }
    }

    private suspend fun processFile(file: VideoFile): Int {
        subtitles.clear()
        val duration = getMediaDuration(file.uri)
        if (duration <= 0) throw Exception("Cannot read media duration")

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@BatchProcessingService)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSettings.sourceLanguage)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }

                var subtitleIndex = 0
                segmentStart = System.currentTimeMillis()

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) { isListening = true }
                    override fun onBeginningOfSpeech() { segmentStart = System.currentTimeMillis() }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { isListening = false }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val progress = subtitles.size.toFloat() / (duration / 3000f).coerceAtLeast(1f)
                        val updated = _fileStates.value.toMutableList()
                        if (currentFileIndex < updated.size) {
                            updated[currentFileIndex] = updated[currentFileIndex].copy(progress = progress.coerceAtMost(0.95f))
                            _fileStates.value = updated
                        }
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            subtitleIndex++
                            val endTime = System.currentTimeMillis()
                            subtitles.add(SubtitleEntry(subtitleIndex, segmentStart, endTime, text))
                        }
                        segmentStart = System.currentTimeMillis()
                        if (isListening) {
                            speechRecognizer?.startListening(intent)
                        } else {
                            writeSrtFile(file.name)
                            if (!continuation.isCompleted) continuation.resumeWith(Result.success(subtitles.size))
                        }
                    }

                    override fun onError(error: Int) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            writeSrtFile(file.name)
                            if (!continuation.isCompleted) continuation.resumeWith(Result.success(subtitles.size))
                        } else {
                            speechRecognizer?.startListening(intent)
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                continuation.invokeOnCancellation { speechRecognizer?.destroy() }
            }
        }
    }

    private fun writeSrtFile(fileName: String) {
        val baseName = fileName.substringBeforeLast(".")
        val outputDir = File(getExternalFilesDir(null), currentSettings.outputLocation)
        outputDir.mkdirs()

        when (currentSettings.outputFormat) {
            OutputFormat.SRT -> writeSrt(File(outputDir, "$baseName.srt"))
            OutputFormat.VTT -> writeVtt(File(outputDir, "$baseName.vtt"))
            OutputFormat.BOTH -> {
                writeSrt(File(outputDir, "$baseName.srt"))
                writeVtt(File(outputDir, "$baseName.vtt"))
            }
        }
    }

    private fun writeSrt(file: File) {
        file.bufferedWriter().use { writer ->
            subtitles.forEach { entry ->
                writer.appendLine("${entry.index}")
                writer.appendLine("${formatSrtTime(entry.startMs)} --> ${formatSrtTime(entry.endMs)}")
                writer.appendLine(entry.text)
                writer.appendLine()
            }
        }
    }

    private fun writeVtt(file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("WEBVTT")
            writer.appendLine()
            subtitles.forEach { entry ->
                writer.appendLine("${formatVttTime(entry.startMs)} --> ${formatVttTime(entry.endMs)}")
                writer.appendLine(entry.text)
                writer.appendLine()
            }
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val h = ms / 3600000 ; val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000 ; val mil = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, mil)
    }

    private fun formatVttTime(ms: Long): String {
        val h = ms / 3600000 ; val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000 ; val mil = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", h, m, s, mil)
    }

    private fun getOutputPath(fileName: String): String {
        return File(getExternalFilesDir(null), "${currentSettings.outputLocation}/${fileName.substringBeforeLast(".")}").absolutePath
    }

    private fun getMediaDuration(uri: Uri): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(this, uri, null)
            var duration = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackDuration = format.getLong(MediaFormat.KEY_DURATION) / 1000
                if (trackDuration > duration) duration = trackDuration
            }
            extractor.release()
            duration
        } catch (e: Exception) { 0L }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Batch Processing", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BatchSRT")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Float) {
        val notification = buildNotification(text, progress)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
