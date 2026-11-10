package com.lumatech.notivo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lumatech.notivo.data.db.dao.FolderDao
import com.lumatech.notivo.data.db.dao.NoteDao
import com.lumatech.notivo.data.db.dao.TaskDao
import com.lumatech.notivo.data.db.entity.FolderEntity
import com.lumatech.notivo.data.db.entity.NoteEntity
import com.lumatech.notivo.data.db.entity.TaskEntity
import com.lumatech.notivo.util.Constants

/**
 * Main Room database for Notivo storing folders, notes (including encrypted blobs), and tasks.
 */
@Database(
    entities = [
        FolderEntity::class,
        NoteEntity::class,
        TaskEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class NotivoDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN image_paths TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): NotivoDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NotivoDatabase::class.java,
                Constants.DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedFoldersCallback)
                .build()
        }

        private object SeedFoldersCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO folders (name, color_hex, is_private, created_at) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>("Osobiste", "#4285F4", 0, now)
                )
                db.execSQL(
                    "INSERT INTO folders (name, color_hex, is_private, created_at) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>("Studia", "#34A853", 0, now + 1L)
                )
                db.execSQL(
                    "INSERT INTO folders (name, color_hex, is_private, created_at) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>("Prywatne", "#EA4335", 1, now + 2L)
                )
            }
        }
    }
}
