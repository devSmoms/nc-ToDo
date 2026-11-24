package de.devsmoms.nctodo

import android.content.Context
import de.devsmoms.nctodo.data.AccountStorage
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.CredentialStore
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.EncryptedFileStore
import de.devsmoms.nctodo.data.Remote
import de.devsmoms.nctodo.data.Settings
import de.devsmoms.nctodo.data.SettingsStore
import de.devsmoms.nctodo.sync.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything belonging to one Nextcloud account: credentials, offline store, settings and
 * a coroutine scope for its background jobs. Accounts never share any of these.
 */
class AccountSession private constructor(
    context: Context,
    val id: String,
    private val credentialStore: CredentialStore,
    credentials: Credentials,
    parentScope: CoroutineScope,
) {
    val storage = AccountStorage(id)
    private val settingsStore = SettingsStore(context, storage.settingsPrefs)

    private val _credentials = MutableStateFlow(credentials)
    val credentials: StateFlow<Credentials> = _credentials

    @Volatile private var remote: Remote? = CalDavRepository(credentials)
    val repository = TaskRepository(EncryptedFileStore(context, storage), { remote })

    /** Child scope: cancelled on logout, so no job of this account outlives it. */
    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    val settings: StateFlow<Settings> = settingsStore.settings
    fun updateSettings(change: (Settings) -> Settings) = settingsStore.update(change)

    var lastSelection: String?
        get() = settingsStore.lastSelection
        set(value) { settingsStore.lastSelection = value }

    /** New app password for the same account: keeps the offline data. */
    fun replaceCredentials(c: Credentials) {
        credentialStore.save(c)
        remote = CalDavRepository(c)
        _credentials.value = c
    }

    /** Stops all work and deletes every trace of the account from the device. */
    suspend fun destroy() {
        scope.cancel()
        remote = null
        repository.clear()
        settingsStore.delete()
        credentialStore.clear()
    }

    companion object {
        /** Opens a stored account; null if its credentials are missing or cannot be decrypted. */
        suspend fun open(context: Context, id: String, parentScope: CoroutineScope): AccountSession? {
            val store = CredentialStore(context, AccountStorage(id))
            val creds = store.load() ?: return null
            return AccountSession(context, id, store, creds, parentScope).also { it.repository.load() }
        }

        fun create(context: Context, id: String, credentials: Credentials, parentScope: CoroutineScope): AccountSession {
            val store = CredentialStore(context, AccountStorage(id))
            store.save(credentials)
            return AccountSession(context, id, store, credentials, parentScope)
        }

        /** Removes leftovers of an account whose credentials could not be opened. */
        suspend fun wipe(context: Context, id: String) {
            val storage = AccountStorage(id)
            EncryptedFileStore(context, storage).clear()
            SettingsStore(context, storage.settingsPrefs).delete()
            CredentialStore(context, storage).clear()
        }
    }
}
