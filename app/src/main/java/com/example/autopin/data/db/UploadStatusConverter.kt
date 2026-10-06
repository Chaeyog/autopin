package com.example.autopin.data.db

import androidx.room.TypeConverter
import com.example.autopin.data.UploadStatus

class UploadStatusConverter {

    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): UploadStatus {
        return try {
            UploadStatus.valueOf(value)
        } catch (_: Exception) {
            UploadStatus.WAITING
        }
    }
}
