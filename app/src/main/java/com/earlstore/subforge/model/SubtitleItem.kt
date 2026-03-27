package com.earlstore.subforge.model

data class SubtitleItem(
    val index: Int,
    val startTime: Long,  // milliseconds
    val endTime: Long,    // milliseconds
    val text: String
) {
    fun startTimeFormatted(): String = formatTime(startTime)
    fun endTimeFormatted(): String = formatTime(endTime)

    private fun formatTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun toSrt(): String = "$index\n${startTimeFormatted()} --> ${endTimeFormatted()}\n$text\n"

    fun toVtt(): String {
        val start = startTimeFormatted().replace(',', '.')
        val end = endTimeFormatted().replace(',', '.')
        return "$start --> $end\n$text\n"
    }
}
