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
import de.devsmoms.nctodo.ui.forDisplay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Uploads pending changes (push) or does a full sync of one account, also while the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NcTodoApp).container
        c.awaitLoaded()
        // No session: the account was logged out in the meantime (or work from an old version).
        val session = c.session(inputData.getString(KEY_ACCOUNT)) ?: return Result.success()
        val pushOnly = inputData.getBoolean(KEY_PUSH_ONLY, false)
        val repo = session.repository
        return try {
            val changes = repo.sync(pushOnly)
            val settings = session.settings.value
            if (!pushOnly && settings.notifySharedChanges && !AppVisibility.inForeground) {
                val hideDeck = settings.hideDeckLists
                c.notifier.showShared(
                    session.id, c.labelFor(session),
                    changes.filterNot { hideDeck && it.listName.trimStart().startsWith("Deck:", ignoreCase = true) },
                )
            }
            val stillPending = repo.data.value.pending.isNotEmpty()
            if (repo.status.value.offline && stillPending) Result.retry() else Result.success()
        } catch (_: AuthException) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_PUSH_ONLY = "pushOnly"
    }
}

/** Daily "due today" reminder of one account; reschedules itself for the next day. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NcTodoApp).container
        c.awaitLoaded()
        val session = c.session(inputData.getString(SyncWorker.KEY_ACCOUNT)) ?: return Result.success()
        val settings = session.settings.value
        if (!settings.notifyDue) return Result.success()
        val today = LocalDate.now()
        val data = session.repository.data.value.forDisplay(settings.hideDeckLists)
        val due = data.tasks.values.flatten()
            .filter { t -> !t.todo.completed && t.todo.parentUid == null && t.todo.due?.let { !it.isAfter(today) } == true }
            .sortedBy { it.todo.due }
            .map { it.todo.summary }
        c.notifier.showDue(session.id, c.labelFor(session), due)
        // Append (not replace): replacing would cancel this very worker.
        c.scheduler.scheduleReminder(session.id, settings, fromWorker = true)
        return Result.success()
    }
}

/** Tracks whether any activity is visible (set from the process lifecycle). */
object AppVisibility {
    @Volatile var inForeground: Boolean = false
}

/** All work is unique per account, so accounts never replace or cancel each other's jobs. */
class SyncScheduler(context: Context) {
    private val wm = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Uploads local changes as soon as there is a network – even if the app is closed meanwhile. */
    fun requestPush(account: String) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(SyncWorker.KEY_ACCOUNT to account, SyncWorker.KEY_PUSH_ONLY to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(name(WORK_PUSH, account), ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun applySettings(account: String, s: Settings) {
        if (s.backgroundIntervalMin <= 0) {
            wm.cancelUniqueWork(name(WORK_PERIODIC, account))
        } else {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(s.backgroundIntervalMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(network)
                .setInputData(workDataOf(SyncWorker.KEY_ACCOUNT to account))
                .build()
            wm.enqueueUniquePeriodicWork(name(WORK_PERIODIC, account), ExistingPeriodicWorkPolicy.UPDATE, req)
        }
        scheduleReminder(account, s)
    }

    fun scheduleReminder(account: String, s: Settings, fromWorker: Boolean = false) {
        if (!s.notifyDue) {
            wm.cancelUniqueWork(name(WORK_REMINDER, account))
            return
        }
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(s.dueTimeMinutes / 60, s.dueTimeMinutes % 60))
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_ACCOUNT to account))
            .build()
        wm.enqueueUniqueWork(
            name(WORK_REMINDER, account), if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req,
        )
    }

    /** Removes every scheduled job of one account (logout). */
    fun cancel(account: String) {
        listOf(WORK_PUSH, WORK_PERIODIC, WORK_REMINDER).forEach { wm.cancelUniqueWork(name(it, account)) }
    }

    /** Jobs of versions before 2.2, which had no account id. */
    fun cancelLegacyWork() {
        listOf(WORK_PUSH, WORK_PERIODIC, WORK_REMINDER).forEach { wm.cancelUniqueWork(it) }
    }

    private fun name(base: String, account: String) = "$base:$account"

    private companion object {
        const val WORK_PUSH = "push"
        const val WORK_PERIODIC = "periodic-sync"
        const val WORK_REMINDER = "due-reminder"
    }
}
