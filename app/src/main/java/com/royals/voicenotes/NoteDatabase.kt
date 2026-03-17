package com.royals.voicenotes

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 5, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        private const val TAG = "NoteDatabase"

        @Volatile
        private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN noteType TEXT NOT NULL DEFAULT 'text'")
                database.execSQL("ALTER TABLE notes ADD COLUMN audioFilePath TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT 'general'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createFtsTables(database)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notes ADD COLUMN reminderTime INTEGER DEFAULT NULL")
            }
        }

        /**
         * Creates FTS4 virtual table and sync triggers for full-text search.
         * Called both during migration (existing users) and onCreate (new installs).
         */
        private fun createFtsTables(database: SupportSQLiteDatabase) {
            Log.d(TAG, "Creating FTS tables and triggers")

            // Create FTS4 virtual table backed by notes content table
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content=\"notes\", title, content)"
            )

            // Trigger: keep FTS in sync on INSERT
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ai AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

            // Trigger: keep FTS in sync on DELETE
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ad AFTER DELETE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                END
            """)

            // Trigger: keep FTS in sync on UPDATE
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_au AFTER UPDATE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

            // Populate FTS table with existing data
            database.execSQL(
                "INSERT INTO notes_fts(docid, title, content) SELECT rowid, title, content FROM notes"
            )
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Create FTS tables for fresh installs
                            createFtsTables(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
