package com.example.autopin.data

import android.net.Uri

data class MediaFolder(
    val id: String,
    val name: String,
    val relativePath: String,
    val coverUri: Uri?,
    val itemCount: Int,
    val isStandardFolder: Boolean = false,
)

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val durationFormatted: String? = null,
    val dateModified: Long = 0L,
)
