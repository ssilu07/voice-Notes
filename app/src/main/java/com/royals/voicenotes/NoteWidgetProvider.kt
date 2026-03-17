package com.royals.voicenotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_OPEN_NOTE) {
            val noteId = intent.getIntExtra("noteId", -1)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("noteId", noteId)
            }
            context.startActivity(launchIntent)
        }
    }

    companion object {
        const val ACTION_OPEN_NOTE = "com.royals.voicenotes.ACTION_OPEN_NOTE"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_note_list)

            // Set up the RemoteViews adapter for the ListView
            val serviceIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.listViewWidget, serviceIntent)
            views.setEmptyView(R.id.listViewWidget, R.id.tvWidgetEmpty)

            // Set up click template for list items
            val clickIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_OPEN_NOTE
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.listViewWidget, clickPendingIntent)

            // Set up "Add" button to open the app
            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val addPendingIntent = PendingIntent.getActivity(
                context, 1, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetAdd, addPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
