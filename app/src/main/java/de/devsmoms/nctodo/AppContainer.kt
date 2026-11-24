package de.devsmoms.nctodo

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.data.AccountRegistry
import de.devsmoms.nctodo.data.AccountStorage
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.CredentialStore
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.OpenSync
import de.devsmoms.nctodo.data.Settings
import de.devsmoms.nctodo.sync.AppVisibility
import de.devsmoms.nctodo.sync.Notifier
import de.devsmoms.nctodo.sync.SyncScheduler
import de.devsmoms.nctodo.ui.LinkPreviews
import de.devsmoms.nctodo.widget.TaskWidget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NcTodoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Application-wide singletons: accounts, the active session, sync scheduling. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val registry = AccountRegistry(app)
    val notifier = Notifier(app).also { it.createChannels() }
    val scheduler = SyncScheduler(app)

    private val _sessions = MutableStateFlow<List<AccountSession>>(emptyList())
    /** All logged-in accounts in the order they were added. */
    val sessions: StateFlow<List<AccountSession>> = _sessions

    private val _active = MutableStateFlow<AccountSession?>(null)
    /** The account shown in the app; null = login screen. */
    val active: StateFlow<AccountSession?> = _active

    /** Settings of the active account (defaults while logged out). */
    val settings: StateFlow<Settings> = active
        .flatMapLatest { it?.settings ?: flowOf(Settings()) }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Credentials of the active account. */
    val account: StateFlow<Credentials?> = active
        .flatMapLatest { s -> s?.credentials ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Messages for the UI (e.g. invalid password). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    private val loaded = CompletableDeferred<Unit>()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private var lastStop: Long? = null
    private var foregroundJob: Job? = null

    init {
        scope.launch {
            val opened = withContext(Dispatchers.IO) {
                if (!registry.initialized()) migrateSingleAccount()
                registry.ids().mapNotNull { id ->
                    AccountSession.open(app, id, scope) ?: run {
                        // Credentials gone (e.g. Keystore key invalidated): remove the rest too.
                        AccountSession.wipe(app, id)
                        registry.remove(id)
                        null
                    }
                }
            }
            opened.forEach(::attach)
            _sessions.value = opened
            _active.value = opened.firstOrNull { it.id == registry.active } ?: opened.firstOrNull()
            loaded.complete(Unit)
            _ready.value = true
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onAppVisible()
            override fun onStop(owner: LifecycleOwner) = onAppHidden()
        })
        // Keep home screen widgets in sync with the offline stores of all accounts (debounced).
        scope.launch {
            sessions
                .flatMapLatest { list -> if (list.isEmpty()) flowOf(0) else combine(list.map { it.repository.data }) { it.size } }
                .debounce(800)
                .collectLatest { runCatching { TaskWidget.refreshAll(app) } }
        }
    }

    /** Versions before 2.2 had exactly one account; it keeps its storage under [AccountStorage.LEGACY_ID]. */
    private fun migrateSingleAccount() {
        if (CredentialStore(app, AccountStorage(AccountStorage.LEGACY_ID)).load() != null) {
            registry.add(AccountStorage.LEGACY_ID)
        }
        registry.markInitialized()
        scheduler.cancelLegacyWork()
    }

    /** Wires an account's background behaviour; everything runs in the account's own scope. */
    private fun attach(session: AccountSession) {
        session.repository.onLocalChange = {
            // Upload immediately while the app runs; WorkManager covers offline / app closed.
            session.scope.launch { safeSync(session, pushOnly = true) }
            scheduler.requestPush(session.id)
        }
        // Background interval and reminder time are per account.
        session.scope.launch {
            session.settings.collectLatest { scheduler.applySettings(session.id, it) }
        }
    }

    suspend fun awaitLoaded() = loaded.await()
    fun isLoggedIn() = _sessions.value.isNotEmpty()
    fun session(id: String?): AccountSession? = id?.let { i -> _sessions.value.firstOrNull { it.id == i } }

    /** Display name used in notifications and messages; only needed with several accounts. */
    fun labelFor(session: AccountSession): String? =
        if (_sessions.value.size > 1) session.displayName() else null

    // ---------------------------------------------------------------- accounts

    /**
     * Adds an account and makes it active. Logging in again with an existing account
     * (e.g. after a new app password) only replaces its credentials.
     */
    suspend fun login(server: String, user: String, password: String): AccountSession {
        val creds = CalDavRepository.discover(server, user, password)
        val existing = _sessions.value.firstOrNull { samePrincipal(it.credentials.value, creds) }
        val session = if (existing != null) {
            withContext(Dispatchers.IO) { existing.replaceCredentials(creds) }
            existing
        } else {
            val id = AccountStorage.newId()
            val created = withContext(Dispatchers.IO) { AccountSession.create(app, id, creds, scope) }
            registry.add(id)
            attach(created)
            _sessions.value = _sessions.value + created
            created
        }
        activate(session)
        safeSync(session)
        return session
    }

    fun switchTo(id: String) {
        val target = session(id) ?: return
        if (_active.value?.id == id) return
        activate(target)
        scope.launch { safeSync(target) }
    }

    private fun activate(session: AccountSession) {
        registry.active = session.id
        _active.value = session
    }

    /**
     * Logs one account out and deletes everything stored for it: credentials, Keystore keys,
     * offline data incl. unsent changes, settings, scheduled work and its notifications.
     */
    suspend fun logout(id: String) {
        val target = session(id) ?: return
        scheduler.cancel(id)
        notifier.cancelAccount(id)
        _sessions.value = _sessions.value - target
        registry.remove(id)
        if (_active.value?.id == id) {
            _active.value = session(registry.active) ?: _sessions.value.firstOrNull()
        }
        // Must finish even if the screen that started it goes away.
        withContext(NonCancellable + Dispatchers.IO) { target.destroy() }
        LinkPreviews.clear()
        if (_sessions.value.isEmpty()) notifier.cancelAll()
        runCatching { TaskWidget.refreshAll(app) }
    }

    private fun samePrincipal(a: Credentials, b: Credentials) =
        a.principalUrl.trimEnd('/').equals(b.principalUrl.trimEnd('/'), ignoreCase = true)

    // ---------------------------------------------------------------- sync triggers

    /** Manual refresh (pull to refresh) of the active account. */
    fun syncNow() {
        val s = _active.value ?: return
        scope.launch { safeSync(s) }
    }

    private suspend fun safeSync(session: AccountSession, pushOnly: Boolean = false) {
        if (session !in _sessions.value) return
        try {
            session.repository.sync(pushOnly)
        } catch (_: AuthException) {
            val who = labelFor(session)?.let { " ($it)" }.orEmpty()
            _events.tryEmit("Anmeldung abgelehnt$who – App-Passwort widerrufen? Bitte Konto neu hinzufügen.")
        }
    }

    private fun onAppVisible() {
        AppVisibility.inForeground = true
        scope.launch {
            awaitLoaded()
            val session = _active.value ?: return@launch
            val s = session.settings.value
            val pausedFor = lastStop?.let { System.currentTimeMillis() - it }
            val syncOnOpen = when (s.openSync) {
                OpenSync.ALWAYS -> true
                OpenSync.NEVER -> false
                // Cold start counts as "after a pause".
                OpenSync.AFTER_INACTIVITY -> pausedFor == null || pausedFor >= s.inactivityMin * 60_000L
            }
            if (syncOnOpen) safeSync(session)
        }
        foregroundJob?.cancel()
        // Foreground interval of the active account; restarts when switching accounts.
        foregroundJob = scope.launch {
            active
                .flatMapLatest { s -> s?.settings?.map { s to it.foregroundIntervalSec } ?: flowOf(null) }
                .distinctUntilChanged()
                .collectLatest { pair ->
                    val (session, interval) = pair ?: return@collectLatest
                    if (interval <= 0) return@collectLatest
                    while (isActive) {
                        delay(interval * 1000L)
                        safeSync(session)
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
