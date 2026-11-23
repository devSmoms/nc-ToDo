package de.devsmoms.nctodo.dav

import java.io.IOException

/** HTTP-level error reported by the server. Anything else of type IOException is a network problem. */
open class DavException(message: String, val code: Int = 0) : IOException(message)
class AuthException : DavException("Anmeldung fehlgeschlagen (Benutzername oder App-Passwort falsch)", 401)
class PreconditionFailedException : DavException("Die Aufgabe wurde zwischenzeitlich an anderer Stelle geändert", 412)
class NotFoundException : DavException("Nicht gefunden", 404)
