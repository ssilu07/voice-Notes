package com.royals.voicenotes

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 6, exportSchema = false)
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 5->6: Fixing FTS triggers")
                fixFtsTriggers(database)
            }
        }

        /**
         * Fix FTS triggers - only fire on title/content changes, not on pin/archive/reminder updates.
         * Also rebuild FTS index to fix any out-of-sync data.
         */
        private fun fixFtsTriggers(database: SupportSQLiteDatabase) {
            // Drop old broken triggers
            database.execSQL("DROP TRIGGER IF EXISTS notes_fts_au")
            database.execSQL("DROP TRIGGER IF EXISTS notes_fts_ai")
            database.execSQL("DROP TRIGGER IF EXISTS notes_fts_ad")

            // Drop and recreate FTS table to fix any corruption
            database.execSQL("DROP TABLE IF EXISTS notes_fts")
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content=\"notes\", title, content)"
            )

            // Trigger: INSERT - only for new notes
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ai AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

            // Trigger: DELETE
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ad AFTER DELETE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                END
            """)

            // Trigger: UPDATE - ONLY when title or content changes (not pin/archive/reminder)
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_au AFTER UPDATE OF title, content ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

            // Rebuild FTS index from current data
            database.execSQL(
                "INSERT INTO notes_fts(docid, title, content) SELECT rowid, title, content FROM notes"
            )
        }

        /**
         * Creates FTS4 virtual table and sync triggers for full-text search.
         * Called for fresh installs via onCreate callback.
         */
        private fun createFtsTables(database: SupportSQLiteDatabase) {
            Log.d(TAG, "Creating FTS tables and triggers")

            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content=\"notes\", title, content)"
            )

            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ai AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_ad AFTER DELETE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                END
            """)

            // Only trigger on title/content changes
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS notes_fts_au AFTER UPDATE OF title, content ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES('delete', old.rowid, old.title, old.content);
                    INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content);
                END
            """)

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
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
