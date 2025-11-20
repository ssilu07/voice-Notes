package com.royals.voicenotes

// ==================== Note.kt ====================

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: String,
    val noteType: String = "text", // "text" or "audio"
    val audioFilePath: String? = null // Path to audio file for audio notes
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_AUDIO = "audio"
    }

    fun isAudioNote(): Boolean = noteType == TYPE_AUDIO
    fun isTextNote(): Boolean = noteType == TYPE_TEXT
}

// ==================== NoteDao.kt ====================

@Database(entities = [Note::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns with default values
                database.execSQL("ALTER TABLE notes ADD COLUMN noteType TEXT NOT NULL DEFAULT 'text'")
                database.execSQL("ALTER TABLE notes ADD COLUMN audioFilePath TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Use this carefully - it will delete all data if migration fails
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}