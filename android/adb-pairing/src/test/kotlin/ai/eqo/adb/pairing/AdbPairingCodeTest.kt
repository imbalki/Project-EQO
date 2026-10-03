package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbPairingCodeTest {
    @Test
    fun sixDigitsParse() {
        val result = AdbPairingCode.parse("123456")
        assertTrue(result is AdbPairingCode.ParseResult.Ok)
        assertEquals("123456", (result as AdbPairingCode.ParseResult.Ok).code.digits)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val result = AdbPairingCode.parse("  123456 ")
        assertTrue(result is AdbPairingCode.ParseResult.Ok)
    }

    @Test
    fun fiveDigitsAreMalformed() {
        assertTrue(AdbPairingCode.parse("12345") is AdbPairingCode.ParseResult.Malformed)
    }

    @Test
    fun sevenDigitsAreMalformed() {
        assertTrue(AdbPairingCode.parse("1234567") is AdbPairingCode.ParseResult.Malformed)
    }

    @Test
    fun nonDigitsAreMalformed() {
        assertTrue(AdbPairingCode.parse("12345a") is AdbPairingCode.ParseResult.Malformed)
    }

    @Test
    fun emptyInputIsMalformed() {
        assertTrue(AdbPairingCode.parse("") is AdbPairingCode.ParseResult.Malformed)
    }

    @Test
    fun androidShowsSixDigits() {
        assertEquals(6, AdbPairingCode.LENGTH)
    }
}
