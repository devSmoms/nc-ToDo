package de.devsmoms.nctodo

import de.devsmoms.nctodo.data.AccountStorage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class AccountStorageTest {

    @Test
    fun legacyAccountKeepsPreviousStorageNames() {
        // Upgrading from 2.1 must find the existing login, cache and settings unchanged.
        val s = AccountStorage(AccountStorage.LEGACY_ID)
        assertEquals("secure_credentials", s.credentialPrefs)
        assertEquals("nctodo_credentials_v1", s.credentialKeyAlias)
        assertEquals("nctodo-credentials-v1", s.credentialAad)
        assertEquals("settings", s.settingsPrefs)
        assertEquals("offline-cache.bin", s.cacheFile)
        assertEquals("nctodo_cache_v1", s.cacheKeyAlias)
        assertEquals("nctodo-cache-v1", s.cacheAad)
    }

    @Test
    fun accountsShareNoStorage() {
        val a = AccountStorage(AccountStorage.newId())
        val b = AccountStorage(AccountStorage.newId())
        val legacy = AccountStorage(AccountStorage.LEGACY_ID)
        for (other in listOf(b, legacy)) {
            assertNotEquals(other.credentialPrefs, a.credentialPrefs)
            assertNotEquals(other.credentialKeyAlias, a.credentialKeyAlias)
            assertNotEquals(other.credentialAad, a.credentialAad)
            assertNotEquals(other.settingsPrefs, a.settingsPrefs)
            assertNotEquals(other.cacheFile, a.cacheFile)
            assertNotEquals(other.cacheKeyAlias, a.cacheKeyAlias)
            assertNotEquals(other.cacheAad, a.cacheAad)
        }
    }

    @Test
    fun idsAreSafeForFileNames() {
        repeat(50) { assertTrue(AccountStorage.isValidId(AccountStorage.newId())) }
        assertTrue(AccountStorage.isValidId(AccountStorage.LEGACY_ID))
        assertFalse(AccountStorage.isValidId("../evil"))
        assertFalse(AccountStorage.isValidId(""))
        assertFalse(AccountStorage.isValidId("ABCDEF0123456789"))
    }
}
