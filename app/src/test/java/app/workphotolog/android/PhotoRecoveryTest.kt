package app.workphotolog.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoRecoveryTest {
    @get:Rule val storage = TemporaryFolder()

    @Test
    fun removesOnlyOriginalsWithNoCommittedRecord() {
        val savedId = "d934d43e-eda6-4ed1-9446-bacfe4bde001"
        val abandonedId = "d934d43e-eda6-4ed1-9446-bacfe4bde002"
        val saved = File(storage.root, "$savedId.jpg").apply { writeText("saved") }
        val abandoned = File(storage.root, "$abandonedId.jpg").apply { writeText("abandoned") }
        val unrelated = File(storage.root, "unrelated.jpg").apply { writeText("other") }

        recoverAbandonedOriginals(storage.root, setOf(savedId))

        assertTrue(saved.exists())
        assertFalse(abandoned.exists())
        assertTrue(unrelated.exists())
    }
}
