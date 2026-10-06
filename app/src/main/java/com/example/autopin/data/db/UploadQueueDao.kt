package com.example.autopin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueueDao {

    @Query("SELECT * FROM upload_queue_items ORDER BY dateAdded DESC")
    fun getAllQueueItemsFlow(): Flow<List<UploadQueueItemEntity>>

    @Query("SELECT COUNT(*) FROM upload_queue_items WHERE status = 'WAITING'")
    fun getWaitingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM upload_queue_items WHERE status = 'UPLOADED'")
    fun getUploadedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM upload_queue_items WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT uri FROM upload_queue_items")
    suspend fun getAllExistingUris(): List<String>

    @Query("SELECT COUNT(*) FROM upload_queue_items")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM upload_queue_items WHERE status = 'WAITING'")
    suspend fun getWaitingCountDirect(): Int

    @Query("SELECT * FROM upload_queue_items")
    suspend fun getAllRowsDirect(): List<UploadQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<UploadQueueItemEntity>): List<Long>

    @Update
    suspend fun update(item: UploadQueueItemEntity)

    @Query("DELETE FROM upload_queue_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE upload_queue_items SET status = 'WAITING', errorMessage = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM upload_queue_items WHERE status = 'UPLOADED'")
    suspend fun clearAllUploaded()

    @Query(
        """
        DELETE FROM upload_queue_items 
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY uri 
                    ORDER BY 
                        CASE status 
                            WHEN 'UPLOADED' THEN 1 
                            WHEN 'UPLOADING' THEN 2 
                            WHEN 'FAILED' THEN 3 
                            WHEN 'WAITING' THEN 4 
                            ELSE 5 
                        END ASC, 
                        id ASC
                ) as rn 
                FROM upload_queue_items
            ) WHERE rn = 1
        )
        """
    )
    suspend fun removeDuplicateRows()
}
