package ai.eqo

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTest {
    @Test
    fun eqoPackageNameIsUnderEqoNamespace() {
        assertEquals("ai.eqo", MainActivity::class.java.`package`?.name)
    }
}
