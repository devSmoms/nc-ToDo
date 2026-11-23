package de.devsmoms.nctodo.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.devsmoms.nctodo.MainActivity
import de.devsmoms.nctodo.R

class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUE, "Fällige Aufgaben", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tägliche Erinnerung an heute fällige Aufgaben" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARED, "Geteilte Listen", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Änderungen anderer Personen in geteilten Listen" },
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    fun showDue(titles: List<String>) {
        if (titles.isEmpty() || !canNotify()) return
        val title = if (titles.size == 1) "1 Aufgabe für heute" else "${titles.size} Aufgaben für heute"
        notify(ID_DUE, CHANNEL_DUE, title, titles)
    }

    fun showShared(changes: List<SharedChange>) {
        if (changes.isEmpty() || !canNotify()) return
        changes.forEach { c ->
            val parts = buildList {
                if (c.added.isNotEmpty()) add("${c.added.size} neu")
                if (c.completed.isNotEmpty()) add("${c.completed.size} erledigt")
            }
            val lines = c.added.map { "+ $it" } + c.completed.map { "✓ $it" }
            // One notification per list; a newer one replaces the older.
            notify(ID_SHARED_BASE + (c.listName.hashCode() and 0xFFFF), CHANNEL_SHARED, "${c.listName}: ${parts.joinToString(", ")}", lines)
        }
    }

    fun cancelAll() = manager.cancelAll()

    private fun notify(id: Int, channel: String, title: String, lines: List<String>) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        // On the lock screen only the count is shown, never task titles.
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.take(3).joinToString(", "))
            .setStyle(style)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            manager.notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime.
        }
    }

    companion object {
        const val CHANNEL_DUE = "due"
        const val CHANNEL_SHARED = "shared"
        private const val ID_DUE = 1
        private const val ID_SHARED_BASE = 1000
    }
}
