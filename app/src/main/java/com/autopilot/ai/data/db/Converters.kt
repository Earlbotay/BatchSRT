package com.autopilot.ai.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromLongNullable(value: Long?): String = value?.toString() ?: ""

    @TypeConverter
    fun toLongNullable(value: String): Long? = value.toLongOrNull()
}
