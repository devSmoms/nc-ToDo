package de.devsmoms.nctodo.dav

import java.net.URI

/** Helpers for Nextcloud principal URLs like https://host/remote.php/dav/principals/users/bob/ */
object Principals {
    /** "https://host/remote.php/dav/" for a principal URL, or null if the layout is unknown. */
    fun davRoot(principalUrl: String): String? {
        val idx = principalUrl.indexOf("/principals/")
        return if (idx < 0) null else principalUrl.substring(0, idx + 1)
    }

    /** "principal:principals/users/bob" – the form Nextcloud expects in share requests. */
    fun shareHref(principalUrl: String): String? {
        val path = runCatching { URI(principalUrl).path }.getOrNull() ?: return null
        val idx = path.indexOf("/principals/")
        if (idx < 0) return null
        return "principal:" + path.substring(idx + 1).trimEnd('/')
    }

    fun isUserOrGroup(shareHref: String) =
        shareHref.startsWith("principal:principals/users/") || shareHref.startsWith("principal:principals/groups/")

    fun isUser(shareHref: String) = shareHref.startsWith("principal:principals/users/") && shareHref.length > "principal:principals/users/".length

    fun isGroup(shareHref: String) = shareHref.startsWith("principal:principals/groups/")

    /**
     * Builds a share href from a username typed by hand. Only conservative characters are
     * accepted so the value cannot escape the users/ collection.
     */
    fun fromUsername(username: String): String? {
        val u = username.trim()
        if (u.isEmpty() || u.length > 64) return null
        if (!u.all { it.isLetterOrDigit() || it in "._-@ " }) return null
        if (u == "." || u == "..") return null
        return "principal:principals/users/$u"
    }

    /**
     * Compares principals given in different notations, e.g. "principals/users/bob",
     * "/remote.php/dav/principals/users/bob/" or a full URL.
     */
    fun samePrincipal(a: String, b: String): Boolean {
        fun key(s: String): String? {
            val path = runCatching { URI(s.replace(" ", "%20")).path }.getOrNull() ?: s
            val idx = path.indexOf("principals/")
            return if (idx < 0) null else path.substring(idx).trimEnd('/')
        }
        val ka = key(a) ?: return false
        return ka == key(b)
    }

    fun samePath(a: String, b: String): Boolean {
        fun norm(s: String) = runCatching { URI(s).path }.getOrDefault(s).trimEnd('/')
        return norm(a) == norm(b)
    }
}
