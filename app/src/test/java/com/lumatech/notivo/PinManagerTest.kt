package com.lumatech.notivo

import com.lumatech.notivo.data.security.PinManager
import com.lumatech.notivo.data.security.PinVerifyResult
import com.lumatech.notivo.data.security.SecureKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {

    private class FakeSecureStore : SecureKeyValueStore {
        private val strings = mutableMapOf<String, String?>()
        private val ints = mutableMapOf<String, Int>()
        private val longs = mutableMapOf<String, Long>()

        override fun getString(key: String): String? = strings[key]
        override fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
        override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue
        override fun putInt(key: String, value: Int) {
            ints[key] = value
        }
        override fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
        override fun putLong(key: String, value: Long) {
            longs[key] = value
        }
    }

    @Test
    fun `setting and verifying valid PIN succeeds`() {
        val manager = PinManager(FakeSecureStore())
        assertFalse(manager.isPinSet())

        manager.setPin("1234")
        assertTrue(manager.isPinSet())
        assertEquals(PinVerifyResult.Success, manager.verifyPin("1234"))
    }

    @Test
    fun `invalid attempts decrement remaining count and lock out after 5 failures`() {
        var now = 1_000_000L
        val manager = PinManager(FakeSecureStore()) { now }
        manager.setPin("5678")

        for (expectedRemaining in 4 downTo 1) {
            val result = manager.verifyPin("0000")
            assertEquals(PinVerifyResult.InvalidPin(expectedRemaining), result)
        }

        val lockedResult = manager.verifyPin("0000")
        assertEquals(PinVerifyResult.LockedOut(30L), lockedResult)

        // Even valid PIN is rejected while locked out
        now += 10_000L
        assertEquals(PinVerifyResult.LockedOut(20L), manager.verifyPin("5678"))

        // After full 30s lockout expires, valid PIN works again
        now += 20_001L
        assertEquals(PinVerifyResult.Success, manager.verifyPin("5678"))
    }

    @Test
    fun `clearPin removes configured PIN`() {
        val manager = PinManager(FakeSecureStore())
        manager.setPin("9012")
        assertTrue(manager.isPinSet())

        manager.clearPin()
        assertFalse(manager.isPinSet())
        assertEquals(PinVerifyResult.NotConfigured, manager.verifyPin("9012"))
    }
}
