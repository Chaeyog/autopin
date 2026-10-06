package com.example.autopin.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.autopin.data.db.UploadQueueItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(
    val scanId: String,
    val folderName: String,
    val folderUri: String,
    val hasPermission: Boolean,
    val totalDiscovered: Int,
    val supportedCount: Int,
    val skippedUnsupported: Int,
    val discoveredItems: List<UploadQueueItemEntity>,
)

class FolderScanner {

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif",
        "mp4", "mov", "mkv", "webm",
    )

    private val supportedMimeTypes = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
        "video/mp4", "video/quicktime", "video/webm", "video/x-matroska",
    )

    suspend fun scanFolderForMedia(
        context: Context,
        folderUri: Uri,
        scanId: String
    ): ScanResult = withContext(Dispatchers.IO) {
        val hasPermission = try {
            context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == folderUri && perm.isReadPermission
            }
        } catch (e: Exception) {
            Log.w("AutoPinTrace", "[AUTOPIN_TRACE] Error checking persisted URI permissions for $folderUri", e)
            false
        }

        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] FOLDER_URI = $folderUri")
        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] URI_PERMISSION = $hasPermission")

        val folder = try {
            DocumentFile.fromTreeUri(context, folderUri)
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Failed to access folder tree URI: $folderUri", e)
            null
        }

        val folderName = try {
            folder?.name ?: "Selected Folder"
        } catch (_: Exception) {
            "Selected Folder"
        }

        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] SCAN_START (scanId=$scanId, folderName=$folderName, folderUri=$folderUri)")

        if (folder == null) {
            Log.w("AutoPinTrace", "[AUTOPIN_TRACE] DocumentFile.fromTreeUri returned null for $folderUri")
            return@withContext ScanResult(
                scanId = scanId,
                folderName = folderName,
                folderUri = folderUri.toString(),
                hasPermission = hasPermission,
                totalDiscovered = 0,
                supportedCount = 0,
                skippedUnsupported = 0,
                discoveredItems = emptyList(),
            )
        }

        val files = try {
            folder.listFiles()
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Exception calling listFiles() on $folderUri", e)
            emptyArray()
        }

        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] Children found: ${files.size}")

        val discoveredItems = mutableListOf<UploadQueueItemEntity>()
        var totalDiscoveredCount = 0
        var supportedCount = 0
        var skippedUnsupportedCount = 0

        for (file in files) {
            if (file.isFile) {
                totalDiscoveredCount++
                val fileUriStr = file.uri.toString()
                val fileName = file.name ?: "Unknown"
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val mimeType = file.type ?: getMimeTypeFromExtension(extension)

                if (isSupportedMedia(fileName, mimeType)) {
                    supportedCount++
                    val item = UploadQueueItemEntity(
                        uri = fileUriStr,
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = file.length(),
                        dateAdded = if (file.lastModified() > 0) file.lastModified() else System.currentTimeMillis(),
                        status = UploadStatus.WAITING,
                        errorMessage = null,
                    )
                    discoveredItems.add(item)
                    Log.i("AutoPinTrace", "[AUTOPIN_TRACE] DISCOVERED: filename=$fileName, contentUri=$fileUriStr, mimeType=$mimeType")
                } else {
                    skippedUnsupportedCount++
                    Log.d("AutoPinTrace", "[AUTOPIN_TRACE] SKIPPED UNSUPPORTED: $fileName (mimeType=$mimeType)")
                }
            }
        }

        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] SCANNER_FOUND (scanId=$scanId, totalDiscovered=$totalDiscoveredCount, count=${discoveredItems.size}, skipped=$skippedUnsupportedCount)")

        ScanResult(
            scanId = scanId,
            folderName = folderName,
            folderUri = folderUri.toString(),
            hasPermission = hasPermission,
            totalDiscovered = totalDiscoveredCount,
            supportedCount = supportedCount,
            skippedUnsupported = skippedUnsupportedCount,
            discoveredItems = discoveredItems,
        )
    }

    private fun isSupportedMedia(fileName: String, mimeType: String?): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in supportedExtensions) return true
        if (mimeType.isNullOrBlank()) return false
        val lowerMime = mimeType.lowercase()
        return supportedMimeTypes.contains(lowerMime) ||
                lowerMime.startsWith("image/") ||
                lowerMime.startsWith("video/")
    }

    private fun getMimeTypeFromExtension(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }
}
