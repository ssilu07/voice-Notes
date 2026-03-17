package com.royals.voicenotes

/**
 * App-wide constants to avoid magic numbers throughout the codebase.
 */
object Constants {

    // Note display
    const val MAX_TITLE_LENGTH = 30
    const val RECENT_NOTES_LIMIT = 5
    const val MIN_NOTE_LENGTH = 3

    // Text analysis
    const val DEFAULT_SUMMARY_SENTENCES = 3
    const val DEFAULT_MAX_TAGS = 5
    const val MIN_WORD_LENGTH = 3

    // Audio playback
    const val SEEK_DURATION_MS = 10_000

    // Timer / animation intervals
    const val TIMER_UPDATE_INTERVAL_MS = 100L
    const val RECORDING_ANIMATION_DURATION_MS = 500L

    // Biometric preferences
    const val PREFS_NAME = "voicenotes_secure_prefs"
    const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
}
