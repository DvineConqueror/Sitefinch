package app.workphotolog.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdaterTest {
    @Test
    fun comparesStableVersionsNumerically() {
        assertTrue(isNewerRelease("0.10.0", "0.9.9"))
        assertTrue(isNewerRelease("1.0.0", "0.99.99"))
        assertFalse(isNewerRelease("0.9.9", "0.10.0"))
        assertFalse(isNewerRelease("0.3.0", "0.3.0"))
        assertFalse(isNewerRelease("0.4.0-rc.1", "0.3.0"))
        assertFalse(isNewerRelease("0.4.2147483648", "0.3.0"))
        assertFalse(isNewerRelease("0.4.0/../", "0.3.0"))
        assertFalse(isNewerRelease("0.4.0", "unknown"))
    }
}
