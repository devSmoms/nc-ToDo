package de.devsmoms.nctodo.data

import android.content.Context

/** Which accounts exist (in display order) and which one is active. Contains no secrets. */
class AccountRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    @Synchronized
    fun ids(): List<String> =
        prefs.getString(KEY_IDS, null).orEmpty().split(',').filter { it.isNotBlank() && AccountStorage.isValidId(it) }

    @Synchronized
    fun add(id: String) {
        if (id !in ids()) prefs.edit().putString(KEY_IDS, (ids() + id).joinToString(",")).commit()
    }

    @Synchronized
    fun remove(id: String) {
        val rest = ids() - id
        prefs.edit().putString(KEY_IDS, rest.joinToString(",")).apply {
            if (active == id) putString(KEY_ACTIVE, rest.firstOrNull())
        }.commit()
    }

    var active: String?
        @Synchronized get() = prefs.getString(KEY_ACTIVE, null)?.takeIf { it in ids() } ?: ids().firstOrNull()
        @Synchronized set(value) { prefs.edit().putString(KEY_ACTIVE, value).commit() }

    /** True once the registry has been written; before that an old single-account install may exist. */
    fun initialized() = prefs.contains(KEY_IDS)

    @Synchronized
    fun markInitialized() {
        if (!initialized()) prefs.edit().putString(KEY_IDS, "").commit()
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_ACTIVE = "active"
    }
}
