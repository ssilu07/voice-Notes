package com.royals.voicenotes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: String,
    val noteType: String = TYPE_TEXT,
    val audioFilePath: String? = null,
    val isPinned: Boolean = false,
    val category: String = CATEGORY_GENERAL,
    val isArchived: Boolean = false,
    val reminderTime: Long? = null
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_AUDIO = "audio"

        const val CATEGORY_GENERAL = "general"
        const val CATEGORY_WORK = "work"
        const val CATEGORY_PERSONAL = "personal"
        const val CATEGORY_IDEAS = "ideas"

        val ALL_CATEGORIES = listOf(CATEGORY_GENERAL, CATEGORY_WORK, CATEGORY_PERSONAL, CATEGORY_IDEAS)
    }

    fun isAudioNote(): Boolean = noteType == TYPE_AUDIO
    fun isTextNote(): Boolean = noteType == TYPE_TEXT
    fun hasReminder(): Boolean = reminderTime != null && reminderTime > System.currentTimeMillis()
}
