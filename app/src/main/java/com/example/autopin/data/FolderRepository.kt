package com.example.autopin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile

sealed class FolderValidationResult {
    object None : FolderValidationResult()
    data class Valid(
        val uri: Uri,
        val folderName: String,
        val displayPath: String,
    ) : FolderValidationResult()
    data class Invalid(val message: String) : FolderValidationResult()
}

class FolderRepository {

    companion object {
        private const val PREFS_NAME = "autopin_prefs"
        private const val KEY_FOLDER_URI = "key_watched_folder_uri"
        private const val INACCESSIBLE_MSG = "Folder access is no longer available. Please select the folder again."
    }

    fun saveFolderUri(context: Context, uri: Uri): FolderValidationResult {
        val contentResolver = context.contentResolver
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e2: Exception) {
                Log.w("FolderRepository", "SecurityException taking persistable Uri permission", e2)
                return FolderValidationResult.Invalid("Failed to obtain persistent permission for the selected folder.")
            }
        } catch (e: Exception) {
            Log.w("FolderRepository", "Exception taking Uri permission", e)
            return FolderValidationResult.Invalid("Failed to process folder permissions: ${e.localizedMessage}")
        }

        val docFile = try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            Log.w("FolderRepository", "Exception accessing DocumentFile from tree Uri", e)
            null
        }

        try {
            if (docFile == null || (!docFile.exists()) || (!docFile.canRead())) {
                return FolderValidationResult.Invalid("The selected folder cannot be accessed. Please choose another folder.")
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putString(KEY_FOLDER_URI, uri.toString()) }

            val folderName = docFile.name ?: deriveFolderNameFromUri(uri)
            val displayPath = deriveDisplayPath(uri, folderName)

            return FolderValidationResult.Valid(
                uri = uri,
                folderName = folderName,
                displayPath = displayPath,
            )
        } catch (e: Exception) {
            Log.e("FolderRepository", "Error validating saved folder Uri", e)
            return FolderValidationResult.Invalid(INACCESSIBLE_MSG)
        }
    }

    fun getSavedFolder(context: Context): FolderValidationResult {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString(KEY_FOLDER_URI, null) ?: return FolderValidationResult.None

            val uri = try {
                uriString.toUri()
            } catch (e: Exception) {
                clearFolder(context)
                return FolderValidationResult.Invalid(INACCESSIBLE_MSG)
            }

            val docFile = try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: Exception) {
                Log.w("FolderRepository", "Inaccessible tree Uri: $uri", e)
                null
            }

            val isAccessible = try {
                docFile != null && docFile.exists() && docFile.canRead()
            } catch (e: Exception) {
                Log.w("FolderRepository", "SecurityException or error checking docFile accessibility", e)
                false
            }

            if (!isAccessible) {
                clearFolder(context)
                return FolderValidationResult.Invalid(INACCESSIBLE_MSG)
            }

            val folderName = try {
                docFile?.name ?: deriveFolderNameFromUri(uri)
            } catch (_: Exception) {
                deriveFolderNameFromUri(uri)
            }
            val displayPath = deriveDisplayPath(uri, folderName)

            return FolderValidationResult.Valid(
                uri = uri,
                folderName = folderName,
                displayPath = displayPath,
            )
        } catch (e: Exception) {
            Log.e("FolderRepository", "Catastrophic error in getSavedFolder", e)
            clearFolder(context)
            return FolderValidationResult.Invalid(INACCESSIBLE_MSG)
        }
    }

    fun clearFolder(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString(KEY_FOLDER_URI, null)

            if (uriString != null) {
                try {
                    val uri = uriString.toUri()
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Ignore permission release failures
                }
            }

            prefs.edit { remove(KEY_FOLDER_URI) }
        } catch (e: Exception) {
            Log.e("FolderRepository", "Error clearing saved folder", e)
        }
    }

    private fun deriveFolderNameFromUri(uri: Uri): String {
        val lastSegment = uri.lastPathSegment ?: return "Watched Folder"
        return if (lastSegment.contains(":")) {
            lastSegment.substringAfterLast(":")
        } else {
            lastSegment
        }
    }

    private fun deriveDisplayPath(uri: Uri, folderName: String): String {
        val lastSegment = uri.lastPathSegment
        return if (lastSegment != null && lastSegment.contains(":")) {
            val pathPart = lastSegment.substringAfter(":")
            pathPart.ifBlank { folderName }
        } else {
            folderName
        }
    }
}
