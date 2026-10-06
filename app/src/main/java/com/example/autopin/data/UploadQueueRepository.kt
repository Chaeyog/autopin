package com.example.autopin.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.autopin.data.db.AutoPinDatabase
import com.example.autopin.data.db.UploadQueueItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.UUID

class UploadQueueRepository(context: Context) {

    private val db = AutoPinDatabase.getInstance(context)
    private val dao = db.uploadQueueDao()
    private val folderScanner = FolderScanner()
    private val scanMutex = Mutex()

    val dbHash: Int
        get() = System.identityHashCode(db)

    val allQueueItemsFlow: Flow<List<UploadQueueItemEntity>> = dao.getAllQueueItemsFlow().catch { e ->
        Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting all queue items flow", e)
        emit(emptyList())
    }
    val waitingCountFlow: Flow<Int> = dao.getWaitingCountFlow().catch { e ->
        Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting waiting count flow", e)
        emit(0)
    }
    val uploadedCountFlow: Flow<Int> = dao.getUploadedCountFlow().catch { e ->
        Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting uploaded count flow", e)
        emit(0)
    }
    val failedCountFlow: Flow<Int> = dao.getFailedCountFlow().catch { e ->
        Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error collecting failed count flow", e)
        emit(0)
    }

    suspend fun scanFolder(context: Context, folderUri: Uri) {
        val scanId = UUID.randomUUID().toString().take(8)
        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] DB_IDENTITY dbHash=$dbHash")

        if (!scanMutex.tryLock()) {
            Log.d("AutoPinTrace", "[AUTOPIN_TRACE] Scan requested while scan is already running. Skipping concurrent scan. (scanId=$scanId)")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                try {
                    dao.removeDuplicateRows()
                } catch (e: Exception) {
                    Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error removing duplicate rows", e)
                }

                val existingUris = try {
                    dao.getAllExistingUris().toSet()
                } catch (e: Exception) {
                    Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error getting existing URIs", e)
                    emptySet()
                }

                val scanResult = folderScanner.scanFolderForMedia(context, folderUri, scanId)
                Log.i("AutoPinTrace", "[AUTOPIN_TRACE] SCAN_RESULT_RECEIVED (scanId=$scanId, count=${scanResult.discoveredItems.size})")

                enqueueDiscoveredMedia(scanResult, existingUris)
            }
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error scanning folder: $folderUri (scanId=$scanId)", e)
        } finally {
            scanMutex.unlock()
        }
    }

    suspend fun enqueueDiscoveredMedia(
        scanResult: ScanResult,
        existingUris: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val scanId = scanResult.scanId
        try {
            val discoveredItems = scanResult.discoveredItems
            val candidatesToInsert = mutableListOf<UploadQueueItemEntity>()

            for (item in discoveredItems) {
                if (existingUris.contains(item.uri)) {
                    Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ALREADY_EXISTS (pre-filter): ${item.fileName}")
                } else {
                    candidatesToInsert.add(item)
                    Log.i("AutoPinTrace", "[AUTOPIN_TRACE] CANDIDATE: filename=${item.fileName}, uri=${item.uri}")
                }
            }

            Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ENQUEUE_START (scanId=$scanId, candidateCount=${candidatesToInsert.size})")

            var newlyInsertedCount = 0

            if (candidatesToInsert.isNotEmpty()) {
                val insertResults = try {
                    dao.insertAll(candidatesToInsert)
                } catch (e: Exception) {
                    Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error inserting candidates into Room", e)
                    emptyList()
                }

                insertResults.forEachIndexed { index, rowId ->
                    val item = candidatesToInsert[index]
                    if (rowId != -1L) {
                        newlyInsertedCount++
                        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] INSERT_RESULT: filename=${item.fileName}, rowId=$rowId")
                    } else {
                        Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ALREADY_EXISTS (Room Conflict): ${item.fileName}")
                    }
                }
            }

            // Direct DAO Verification Queries
            val directTotal = try {
                dao.getTotalCount()
            } catch (_: Exception) {
                -1
            }
            val directWaiting = try {
                dao.getWaitingCountDirect()
            } catch (_: Exception) {
                -1
            }

            Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ROOM_DIRECT (scanId=$scanId, dbHash=$dbHash, totalCount=$directTotal, waitingCount=$directWaiting)")

            try {
                val allRows = dao.getAllRowsDirect()
                allRows.forEach { row ->
                    Log.i("AutoPinTrace", "[AUTOPIN_TRACE] ROOM_ROW (id=${row.id}, fileName=${row.fileName}, status=${row.status.name}, uri=${row.uri})")
                }
            } catch (e: Exception) {
                Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error fetching direct rows", e)
            }

            newlyInsertedCount
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in enqueueDiscoveredMedia", e)
            0
        }
    }

    suspend fun cleanupDuplicates() = withContext(Dispatchers.IO) {
        try {
            dao.removeDuplicateRows()
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error in cleanupDuplicates", e)
        }
    }

    suspend fun retryFailed() = withContext(Dispatchers.IO) {
        try {
            dao.retryAllFailed()
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error retrying failed items", e)
        }
    }

    suspend fun clearUploaded() = withContext(Dispatchers.IO) {
        try {
            dao.clearAllUploaded()
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error clearing uploaded items", e)
        }
    }

    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        try {
            dao.deleteById(id)
        } catch (e: Exception) {
            Log.e("AutoPinTrace", "[AUTOPIN_TRACE] Error deleting item $id", e)
        }
    }
}
