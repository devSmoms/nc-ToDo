package de.devsmoms.nctodo.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject

data class Credentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val principalUrl: String,
    val calendarHomeUrl: String,
    val displayName: String,
)

/**
 * Stores the login encrypted with AES-256-GCM. The key lives in the Android Keystore
 * (hardware-backed / StrongBox where available) and can never be exported, so the
 * stored blob is useless when copied off the device. App backups are disabled in the
 * manifest, so the blob does not leave the device via backup either.
 */
class CredentialStore(context: Context, private val storage: AccountStorage) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(storage.credentialPrefs, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(storage.credentialKeyAlias, storage.credentialAad.toByteArray())

    @Volatile private var cached: Credentials? = null

    fun save(c: Credentials) {
        val json = JSONObject()
            .put("serverUrl", c.serverUrl)
            .put("username", c.username)
            .put("password", c.password)
            .put("principalUrl", c.principalUrl)
            .put("calendarHomeUrl", c.calendarHomeUrl)
            .put("displayName", c.displayName)
            .toString()
        val blob = cipher.encrypt(json.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_DATA, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
        cached = c
    }

    /** Returns null if nothing is stored or the data cannot be decrypted (e.g. key was invalidated). */
    @Synchronized
    fun load(): Credentials? {
        cached?.let { return it }
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val json = JSONObject(String(cipher.decrypt(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8))
            Credentials(
                serverUrl = json.getString("serverUrl"),
                username = json.getString("username"),
                password = json.getString("password"),
                principalUrl = json.getString("principalUrl"),
                calendarHomeUrl = json.getString("calendarHomeUrl"),
                displayName = json.optString("displayName", json.getString("username")),
            ).also { cached = it }
        } catch (_: Exception) {
            // Tampered or undecryptable data: wipe it and force a fresh login.
            clear()
            null
        }
    }

    /** Removes the blob, the preferences file itself and the Keystore key. */
    @Synchronized
    fun clear() {
        cached = null
        prefs.edit().clear().commit()
        app.deleteSharedPreferences(storage.credentialPrefs)
        cipher.deleteKey()
    }

    private companion object {
        const val KEY_DATA = "data"
    }
}
