package de.devsmoms.nctodo.data

import java.util.UUID

/**
 * Names of everything stored for one account. The account that existed before
 * multi-account support keeps the old names ([LEGACY_ID]), so updating the app
 * needs no data migration and nobody has to log in again.
 */
data class AccountStorage(val id: String) {
    private val legacy = id == LEGACY_ID
    private fun suffixed(base: String) = if (legacy) base else "${base}_$id"

    val credentialPrefs = suffixed("secure_credentials")
    val credentialKeyAlias = suffixed("nctodo_credentials_v1")
    val credentialAad = if (legacy) "nctodo-credentials-v1" else "nctodo-credentials-v1:$id"

    val settingsPrefs = suffixed("settings")

    val cacheFile = if (legacy) "offline-cache.bin" else "offline-cache-$id.bin"
    val cacheKeyAlias = suffixed("nctodo_cache_v1")
    val cacheAad = if (legacy) "nctodo-cache-v1" else "nctodo-cache-v1:$id"

    companion object {
        const val LEGACY_ID = "default"

        /** Random id; only [a-z0-9] so it is safe in file names, key aliases and work names. */
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

        fun isValidId(id: String) = id == LEGACY_ID || id.matches(Regex("[a-z0-9]{16}"))
    }
}
