package com.royals.voicenotes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceNotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Create notification channel for reminders
        NotificationHelper.createNotificationChannel(this)
    }
}
