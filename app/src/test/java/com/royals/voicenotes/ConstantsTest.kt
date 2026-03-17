package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

class ConstantsTest {

    @Test
    fun maxTitleLength_isReasonable() {
        assertTrue(Constants.MAX_TITLE_LENGTH > 0)
        assertTrue(Constants.MAX_TITLE_LENGTH <= 100)
    }

    @Test
    fun recentNotesLimit_isPositive() {
        assertTrue(Constants.RECENT_NOTES_LIMIT > 0)
    }

    @Test
    fun minNoteLength_isPositive() {
        assertTrue(Constants.MIN_NOTE_LENGTH > 0)
    }

    @Test
    fun seekDuration_is10Seconds() {
        assertEquals(10_000, Constants.SEEK_DURATION_MS)
    }

    @Test
    fun timerInterval_isReasonable() {
        assertTrue(Constants.TIMER_UPDATE_INTERVAL_MS in 50..1000)
    }

    @Test
    fun prefsName_notEmpty() {
        assertTrue(Constants.PREFS_NAME.isNotEmpty())
    }

    @Test
    fun defaultSummarySentences_isPositive() {
        assertTrue(Constants.DEFAULT_SUMMARY_SENTENCES > 0)
    }

    @Test
    fun defaultMaxTags_isPositive() {
        assertTrue(Constants.DEFAULT_MAX_TAGS > 0)
    }
}
