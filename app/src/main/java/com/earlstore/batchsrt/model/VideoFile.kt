package com.earlstore.batchsrt.model

import android.net.Uri

data class VideoFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val duration: Long = 0L,
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val progress: Float = 0f,
    val subtitleCount: Int = 0,
    val outputPath: String? = null,
    val error: String? = null
)

enum class ProcessingStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}
