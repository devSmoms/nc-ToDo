package de.devsmoms.nctodo.desktop

import com.sun.jna.platform.win32.Crypt32Util
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.MiniJson
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.LocalDataCodec
import de.devsmoms.nctodo.sync.LocalPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Where the desktop app keeps its (encrypted) files. */
object DesktopPaths {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    val isWindows = os.contains("win")

    val dataDir: Path by lazy {
        val home = System.getProperty("user.home")
        val dir = when {
            isWindows -> Path.of(System.getenv("APPDATA") ?: home, "NC Todo")
            os.contains("mac") -> Path.of(home, "Library", "Application Support", "NC Todo")
            else -> Path.of(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "nc-todo")
        }
        Files.createDirectories(dir)
        restrictToOwner(dir)
        dir
    }

    /** chmod 700 on Linux; Windows user folders are private already. */
    fun restrictToOwner(path: Path) {
        if (isWindows) return
        runCatching {
            Files.setPosixFilePermissions(
                path,
                PosixFilePermissions.fromString(if (Files.isDirectory(path)) "rwx------" else "rw-------"),
            )
        }
    }

    /** Shown in Nextcloud as part of the app password name ("NC Todo (<name>)"). */
    fun deviceName(): String =
        (System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull())
            ?.takeIf { it.isNotBlank() } ?: if (isWindows) "Windows" else "Linux"
}

class SecretStoreException(message: String) : Exception(message)

/** Small secrets (credentials, cache key) protected by the operating system. */
interface SecretStore {
    fun get(key: String): ByteArray?
    fun put(key: String, value: ByteArray)
    fun delete(key: String)

    companion object {
        fun forThisOs(): SecretStore =
            if (DesktopPaths.isWindows) WindowsDpapiStore(DesktopPaths.dataDir.resolve("secrets")) else LinuxSecretToolStore()

        internal fun checkKey(key: String) = require(key.matches(Regex("[a-z0-9-]{1,40}"))) { "Invalid secret key" }
    }
}

/**
 * Windows: DPAPI encrypts with a key bound to the Windows user account, so the files are
 * useless for other users or on another computer.
 */
class WindowsDpapiStore(private val dir: Path) : SecretStore {
    private fun file(key: String): Path {
        SecretStore.checkKey(key)
        return dir.resolve("$key.bin")
    }

    override fun get(key: String): ByteArray? {
        val f = file(key)
        if (!Files.exists(f)) return null
        return runCatching { Crypt32Util.cryptUnprotectData(Files.readAllBytes(f)) }.getOrNull()
    }

    override fun put(key: String, value: ByteArray) {
        Files.createDirectories(dir)
        writeAtomically(file(key), Crypt32Util.cryptProtectData(value))
    }

    override fun delete(key: String) {
        Files.deleteIfExists(file(key))
    }
}

/**
 * Linux: the desktop keyring (GNOME Keyring, KWallet, …) via the Secret Service command line
 * tool "secret-tool" from libsecret. Values are stored base64 encoded.
 */
class LinuxSecretToolStore : SecretStore {
    private fun run(vararg args: String, input: String? = null): Pair<Int, String> {
        val process = try {
            ProcessBuilder("secret-tool", *args).redirectErrorStream(false).start()
        } catch (_: java.io.IOException) {
            throw SecretStoreException(
                "Der Schlüsselbund ist nicht erreichbar: „secret-tool“ fehlt. " +
                    "Bitte das Paket „libsecret-tools“ installieren (z. B. sudo apt install libsecret-tools).",
            )
        }
        process.outputStream.use { out -> input?.let { out.write(it.toByteArray()) } }
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw SecretStoreException("Der Schlüsselbund antwortet nicht – ist er entsperrt?")
        }
        return process.exitValue() to output
    }

    override fun get(key: String): ByteArray? {
        SecretStore.checkKey(key)
        val (code, out) = run("lookup", "application", "nc-todo", "key", key)
        if (code != 0 || out.isBlank()) return null
        return runCatching { Base64.getDecoder().decode(out.trim()) }.getOrNull()
    }

    override fun put(key: String, value: ByteArray) {
        SecretStore.checkKey(key)
        val (code, _) = run("store", "--label=NC Todo ($key)", "application", "nc-todo", "key", key, input = Base64.getEncoder().encodeToString(value))
        if (code != 0) throw SecretStoreException("Speichern im Schlüsselbund fehlgeschlagen – ist er entsperrt?")
    }

    override fun delete(key: String) {
        SecretStore.checkKey(key)
        run("clear", "application", "nc-todo", "key", key)
    }
}

/** Login data in the OS secret store. */
class DesktopCredentialStore(private val secrets: SecretStore) {
    fun load(): Credentials? {
        val json = secrets.get(KEY)?.toString(Charsets.UTF_8) ?: return null
        val o = MiniJson.parseObject(json) ?: return null
        fun s(k: String) = MiniJson.string(o, k)
        if (s("serverUrl").isBlank() || s("password").isBlank()) return null
        return Credentials(s("serverUrl"), s("username"), s("password"), s("principalUrl"), s("calendarHomeUrl"), s("displayName").ifBlank { s("username") })
    }

    fun save(c: Credentials) = secrets.put(
        KEY,
        MiniJson.write(
            mapOf(
                "serverUrl" to c.serverUrl, "username" to c.username, "password" to c.password,
                "principalUrl" to c.principalUrl, "calendarHomeUrl" to c.calendarHomeUrl, "displayName" to c.displayName,
            ),
        ).toByteArray(Charsets.UTF_8),
    )

    fun clear() = secrets.delete(KEY)

    private companion object {
        const val KEY = "credentials"
    }
}

/**
 * Offline cache: AES-256-GCM encrypted file; the key itself lives in the OS secret store.
 * Writes go to a temporary file first and replace the old one atomically.
 */
class DesktopPersistence(private val secrets: SecretStore, private val file: Path) : LocalPersistence {
    @Volatile private var closed = false

    private fun key(): ByteArray = secrets.get(KEY_NAME)?.takeIf { it.size == 32 }
        ?: ByteArray(32).also { SecureRandom().nextBytes(it); secrets.put(KEY_NAME, it) }

    override suspend fun load(): LocalData? = withContext(Dispatchers.IO) {
        if (!Files.exists(file)) return@withContext null
        // Outside runCatching: a locked keyring must not delete the cache.
        val key = key()
        runCatching {
            val blob = Files.readAllBytes(file)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob, 0, IV_BYTES))
            cipher.updateAAD(AAD)
            LocalDataCodec.decode(String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8))
        }.getOrNull() ?: run {
            // Undecryptable or corrupt: start fresh, the server has the data.
            Files.deleteIfExists(file)
            null
        }
    }

    override suspend fun save(data: LocalData) = withContext(Dispatchers.IO) {
        if (closed) return@withContext
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        writeAtomically(file, iv + cipher.doFinal(LocalDataCodec.encode(data).toByteArray(Charsets.UTF_8)))
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        closed = true
        Files.deleteIfExists(file)
        secrets.delete(KEY_NAME)
    }

    private companion object {
        const val KEY_NAME = "cache-key"
        const val IV_BYTES = 12
        val AAD = "nctodo-desktop-cache-v1".toByteArray()
    }
}

/** Writes via a temporary file so a crash never leaves a half written file. */
internal fun writeAtomically(target: Path, bytes: ByteArray) {
    val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
    Files.write(tmp, bytes)
    DesktopPaths.restrictToOwner(tmp)
    try {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
