package de.devsmoms.nctodo.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.devsmoms.nctodo.NcTodoApp
import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.data.Settings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Uploads pending changes (push) or does a full sync, also while the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NcTodoApp).container
        if (!c.isLoggedIn()) return Result.success()
        c.awaitLoaded()
        val pushOnly = inputData.getBoolean(KEY_PUSH_ONLY, false)
        return try {
            val changes = c.repository.sync(pushOnly)
            if (!pushOnly && c.settings.value.notifySharedChanges && !AppVisibility.inForeground) {
                c.notifier.showShared(changes)
            }
            val stillPending = c.repository.data.value.pending.isNotEmpty()
            if (c.repository.status.value.offline && stillPending) Result.retry() else Result.success()
        } catch (_: AuthException) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_PUSH_ONLY = "pushOnly"
    }
}

/** Daily "due today" reminder; reschedules itself for the next day. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NcTodoApp).container
        val settings = c.settings.value
        if (!c.isLoggedIn() || !settings.notifyDue) return Result.success()
        c.awaitLoaded()
        val today = LocalDate.now()
        val data = c.repository.data.value
        val due = data.tasks.values.flatten()
            .filter { t -> !t.todo.completed && t.todo.parentUid == null && t.todo.due?.let { !it.isAfter(today) } == true }
            .sortedBy { it.todo.due }
            .map { it.todo.summary }
        c.notifier.showDue(due)
        // Append (not replace): replacing would cancel this very worker.
        c.scheduler.scheduleReminder(settings, fromWorker = true)
        return Result.success()
    }
}

/** Tracks whether any activity is visible (set from the process lifecycle). */
object AppVisibility {
    @Volatile var inForeground: Boolean = false
}

class SyncScheduler(context: Context) {
    private val wm = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Uploads local changes as soon as there is a network – even if the app is closed meanwhile. */
    fun requestPush() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(SyncWorker.KEY_PUSH_ONLY to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_PUSH, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun applySettings(s: Settings) {
        if (s.backgroundIntervalMin <= 0) {
            wm.cancelUniqueWork(WORK_PERIODIC)
        } else {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(s.backgroundIntervalMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
        scheduleReminder(s)
    }

    fun scheduleReminder(s: Settings, fromWorker: Boolean = false) {
        if (!s.notifyDue) {
            wm.cancelUniqueWork(WORK_REMINDER)
            return
        }
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(s.dueTimeMinutes / 60, s.dueTimeMinutes % 60))
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_REMINDER, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll() {
        listOf(WORK_PUSH, WORK_PERIODIC, WORK_REMINDER).forEach { wm.cancelUniqueWork(it) }
    }

    private companion object {
        const val WORK_PUSH = "push"
        const val WORK_PERIODIC = "periodic-sync"
        const val WORK_REMINDER = "due-reminder"
    }
}
