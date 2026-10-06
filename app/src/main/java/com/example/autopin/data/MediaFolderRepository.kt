package com.example.autopin.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaFolderRepository {

    suspend fun getMediaFolders(context: Context): List<MediaFolder> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, FolderAccumulator>()

        queryMediaStore(
            context = context,
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            folderMap = folderMap
        )

        queryMediaStore(
            context = context,
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            folderMap = folderMap
        )

        val commonNames = setOf("DCIM", "Camera", "Pictures", "Screenshots", "Movies", "Download", "Downloads")

        folderMap.values.map { acc ->
            MediaFolder(
                id = acc.id,
                name = acc.name,
                relativePath = acc.relativePath,
                coverUri = acc.latestCoverUri,
                itemCount = acc.itemCount,
                isStandardFolder = acc.name in commonNames
            )
        }.sortedWith(
            compareByDescending<MediaFolder> { it.isStandardFolder }
                .thenByDescending { it.itemCount }
                .thenBy { it.name }
        )
    }

    suspend fun getMediaItemsInFolder(
        context: Context,
        folderId: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaItem>()

        queryMediaItems(
            context = context,
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            bucketId = folderId,
            isVideo = false,
            outputList = mediaItems
        )

        queryMediaItems(
            context = context,
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            bucketId = folderId,
            isVideo = true,
            outputList = mediaItems
        )

        mediaItems.sortedByDescending { it.dateModified }
    }

    private fun queryMediaStore(
        context: Context,
        contentUri: Uri,
        folderMap: MutableMap<String, FolderAccumulator>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.MediaColumns.RELATIVE_PATH
            } else {
                MediaStore.MediaColumns.DATA
            }
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                contentUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val bucketId = cursor.getString(bucketIdColumn) ?: continue
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Folder"
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val itemUri = ContentUris.withAppendedId(contentUri, id)

                    val relativePath = if (pathColumn != -1) {
                        val pathVal = cursor.getString(pathColumn) ?: ""
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            pathVal
                        } else {
                            deriveRelativePathFromFullPath(pathVal)
                        }
                    } else {
                        bucketName
                    }

                    val existing = folderMap[bucketId]
                    if (existing == null) {
                        folderMap[bucketId] = FolderAccumulator(
                            id = bucketId,
                            name = bucketName,
                            relativePath = relativePath,
                            latestCoverUri = itemUri,
                            latestDateModified = dateModified,
                            itemCount = 1
                        )
                    } else {
                        existing.itemCount++
                        if (dateModified > existing.latestDateModified) {
                            existing.latestDateModified = dateModified
                            existing.latestCoverUri = itemUri
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Suppress query failures safely
        }
    }

    private fun queryMediaItems(
        context: Context,
        contentUri: Uri,
        bucketId: String,
        isVideo: Boolean,
        outputList: MutableList<MediaItem>
    ) {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        if (isVideo) {
            projection.add(MediaStore.Video.VideoColumns.DURATION)
        }

        val selection = "${MediaStore.MediaColumns.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                contentUri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Media"
                    val dateModified = cursor.getLong(dateColumn)
                    val itemUri = ContentUris.withAppendedId(contentUri, id)

                    val durationMs = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val formattedDuration = if (isVideo && durationMs > 0) formatDuration(durationMs) else null

                    outputList.add(
                        MediaItem(
                            id = id,
                            uri = itemUri,
                            displayName = name,
                            isVideo = isVideo,
                            durationFormatted = formattedDuration,
                            dateModified = dateModified
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Suppress query failures safely
        }
    }

    private fun deriveRelativePathFromFullPath(fullPath: String): String {
        val emulatedPrefix = "/storage/emulated/0/"
        return if (fullPath.startsWith(emulatedPrefix)) {
            val relative = fullPath.substring(emulatedPrefix.length)
            if (relative.contains("/")) {
                relative.substringBeforeLast("/") + "/"
            } else {
                relative
            }
        } else {
            fullPath.substringAfterLast("/")
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private data class FolderAccumulator(
        val id: String,
        val name: String,
        val relativePath: String,
        var latestCoverUri: Uri?,
        var latestDateModified: Long,
        var itemCount: Int
    )
}
