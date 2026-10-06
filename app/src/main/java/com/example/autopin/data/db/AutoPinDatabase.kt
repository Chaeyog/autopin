package com.example.autopin.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadQueueItemEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(UploadStatusConverter::class)
abstract class AutoPinDatabase : RoomDatabase() {

    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AutoPinDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS upload_queue_items_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uri TEXT NOT NULL,
                            fileName TEXT NOT NULL,
                            mimeType TEXT NOT NULL,
                            fileSize INTEGER NOT NULL,
                            dateAdded INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            errorMessage TEXT
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO upload_queue_items_new (uri, fileName, mimeType, fileSize, dateAdded, status, errorMessage)
                        SELECT uri, fileName, mimeType, fileSize, dateAdded, status, errorMessage
                        FROM upload_queue_items
                        ORDER BY 
                            CASE status
                                WHEN 'UPLOADED' THEN 1
                                WHEN 'UPLOADING' THEN 2
                                WHEN 'FAILED' THEN 3
                                WHEN 'WAITING' THEN 4
                                ELSE 5
                            END ASC,
                            id ASC
                        """.trimIndent()
                    )

                    db.execSQL("DROP TABLE IF EXISTS upload_queue_items")
                    db.execSQL("ALTER TABLE upload_queue_items_new RENAME TO upload_queue_items")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_queue_items_uri ON upload_queue_items (uri)")
                } catch (e: Exception) {
                    Log.e("AutoPinDatabase", "Error executing MIGRATION_1_2", e)
                }
            }
        }

        fun getInstance(context: Context): AutoPinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AutoPinDatabase::class.java,
                        "autopin_database"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .fallbackToDestructiveMigrationOnDowngrade(true)
                        .build()
                } catch (e: Exception) {
                    Log.e("AutoPinDatabase", "Failed to build database, using fallback", e)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AutoPinDatabase::class.java,
                        "autopin_database"
                    )
                        .fallbackToDestructiveMigration(true)
                        .build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
