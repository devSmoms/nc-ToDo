package de.devsmoms.nctodo.data

/** Login of one account; stored encrypted by the platform (Android Keystore, OS keyring, …). */
data class Credentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val principalUrl: String,
    val calendarHomeUrl: String,
    val displayName: String,
) {
    /** Never print the password, e.g. in logs or crash reports. */
    override fun toString() = "Credentials(serverUrl=$serverUrl, username=$username, password=***)"
}
