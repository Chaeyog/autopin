package com.example.autopin.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.autopin.data.UploadStatus

@Entity(
    tableName = "upload_queue_items",
    indices = [
        Index(value = ["uri"], unique = true),
    ]
)
data class UploadQueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val dateAdded: Long,
    val status: UploadStatus = UploadStatus.WAITING,
    val errorMessage: String? = null
)
