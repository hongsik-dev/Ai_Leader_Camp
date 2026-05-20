package com.catchpro.app.data.local

import androidx.room.TypeConverter

private const val ListDelimiter = "\u001F"

class CatchProTypeConverters {
    @TypeConverter
    fun fromDelimitedString(value: String): List<String> {
        return value
            .split(ListDelimiter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toDelimitedString(values: List<String>): String {
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(ListDelimiter)
    }
}
