package de.devsmoms.nctodo

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.CredentialStore
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.EncryptedFileStore
import de.devsmoms.nctodo.data.OpenSync
import de.devsmoms.nctodo.data.Remote
import de.devsmoms.nctodo.data.Settings
import de.devsmoms.nctodo.data.SettingsStore
import de.devsmoms.nctodo.sync.AppVisibility
import de.devsmoms.nctodo.sync.Notifier
import de.devsmoms.nctodo.sync.SyncScheduler
import de.devsmoms.nctodo.sync.TaskRepository
import de.devsmoms.nctodo.widget.TaskWidget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NcTodoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Application-wide singletons: session, offline store, sync scheduling. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val credentialStore = CredentialStore(app)
    private val settingsStore = SettingsStore(app)
    val notifier = Notifier(app).also { it.createChannels() }
    val scheduler = SyncScheduler(app)

    @Volatile private var remote: Remote? = null
    private val _account = MutableStateFlow<Credentials?>(null)
    val account: StateFlow<Credentials?> = _account

    val settings: StateFlow<Settings> = settingsStore.settings
    val repository = TaskRepository(EncryptedFileStore(app), { remote })

    /** Messages for the UI (e.g. invalid password). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    private val loaded = CompletableDeferred<Unit>()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private var lastStop: Long? = null
    private var foregroundJob: Job? = null

    init {
        repository.onLocalChange = {
            // Upload immediately while the app runs; WorkManager covers offline / app closed.
            scope.launch { safeSync(pushOnly = true) }
            scheduler.requestPush()
        }
        scope.launch(Dispatchers.IO) {
            val creds = credentialStore.load()
            if (creds != null) {
                remote = CalDavRepository(creds)
                repository.load()
            }
            _account.value = creds
            loaded.complete(Unit)
            _ready.value = true
            if (creds != null) scheduler.applySettings(settings.value)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onAppVisible()
            override fun onStop(owner: LifecycleOwner) = onAppHidden()
        })
        // Keep home screen widgets in sync with the offline store (debounced).
        scope.launch {
            combine(repository.data, account) { d, a -> d to a }
                .debounce(800)
                .collectLatest { runCatching { TaskWidget.refreshAll(app) } }
        }
        // React to settings changes (background interval, reminder time).
        scope.launch {
            settings.drop(1).collectLatest { if (isLoggedIn()) scheduler.applySettings(it) }
        }
    }

    suspend fun awaitLoaded() = loaded.await()
    fun isLoggedIn() = credentialStore.load() != null

    fun updateSettings(change: (Settings) -> Settings) = settingsStore.update(change)

    var lastSelection: String?
        get() = settingsStore.lastSelection
        set(value) { settingsStore.lastSelection = value }

    // ---------------------------------------------------------------- session

    suspend fun login(server: String, user: String, password: String) {
        val creds = CalDavRepository.discover(server, user, password)
        // A different account must never see the previous account's cache.
        repository.clear()
        credentialStore.save(creds)
        remote = CalDavRepository(creds)
        _account.value = creds
        scheduler.applySettings(settings.value)
        safeSync()
    }

    suspend fun logout() {
        scheduler.cancelAll()
        notifier.cancelAll()
        remote = null
        _account.value = null
        repository.clear()
        credentialStore.clear()
    }

    // ---------------------------------------------------------------- sync triggers

    fun syncNow() {
        scope.launch { safeSync() }
    }

    private suspend fun safeSync(pushOnly: Boolean = false) {
        if (remote == null) return
        try {
            repository.sync(pushOnly)
        } catch (_: AuthException) {
            _events.tryEmit("Anmeldung abgelehnt – App-Passwort widerrufen? Bitte abmelden und neu anmelden.")
        }
    }

    private fun onAppVisible() {
        AppVisibility.inForeground = true
        scope.launch {
            awaitLoaded()
            if (!isLoggedIn()) return@launch
            val s = settings.value
            val pausedFor = lastStop?.let { System.currentTimeMillis() - it }
            val syncOnOpen = when (s.openSync) {
                OpenSync.ALWAYS -> true
                OpenSync.NEVER -> false
                // Cold start counts as "after a pause".
                OpenSync.AFTER_INACTIVITY -> pausedFor == null || pausedFor >= s.inactivityMin * 60_000L
            }
            if (syncOnOpen) safeSync()
        }
        foregroundJob?.cancel()
        foregroundJob = scope.launch {
            settings.map { it.foregroundIntervalSec }.distinctUntilChanged().collectLatest { interval ->
                if (interval <= 0) return@collectLatest
                while (isActive) {
                    delay(interval * 1000L)
                    if (isLoggedIn()) safeSync()
                }
            }
        }
    }

    private fun onAppHidden() {
        AppVisibility.inForeground = false
        lastStop = System.currentTimeMillis()
        foregroundJob?.cancel()
        foregroundJob = null
    }
}
