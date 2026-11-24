package app.workphotolog.android

import android.view.KeyEvent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StampTemplateSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scrollingAndBackKeepTheDraftUntilDiscarded() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                var open by rememberSaveable { mutableStateOf(false) }
                Button(onClick = { open = true }) { Text("Open templates") }
                if (open) {
                    StampTemplateSheet(StampTemplate(), onApply = { open = false }, onDismiss = { open = false })
                }
            }
        }

        compose.onNodeWithText("Open templates").performClick()
        compose.onNodeWithTag("stampSheet").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("stampSheet").assertDoesNotExist()

        compose.onNodeWithText("Open templates").performClick()
        compose.onNodeWithTag("templateCatalogScroll").performTouchInput { swipeDown(durationMillis = 100) }
        compose.onNodeWithTag("templateClock").performScrollTo().performClick()
        compose.onNodeWithTag("editTemplate").performClick()
        compose.onNodeWithTag("stampShowTitle").performScrollTo().performClick()
        compose.onNodeWithTag("stampEditorScroll").performTouchInput { swipeDown(durationMillis = 100) }
        compose.onNodeWithTag("stampShowTitle").assertIsOff()
        compose.onNodeWithContentDescription("Close templates").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("stampShowTitle").assertIsOff()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("stampShowTitle").assertIsOff()
        compose.onNodeWithTag("stampEditorSTYLE").performScrollTo().performClick()
        compose.onNodeWithTag("stampEditorScroll").performTouchInput { swipeDown(durationMillis = 100) }
        compose.onNodeWithText("Customize stamp").assertIsDisplayed()

        pressBack()
        compose.onNodeWithTag("editTemplate").assertIsDisplayed()
        compose.onNodeWithTag("editTemplate").performClick()
        compose.onNodeWithTag("stampShowTitle").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("Back to templates").performClick()
        compose.onNodeWithTag("editTemplate").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithTag("stampSheet").assertDoesNotExist()

        compose.onNodeWithText("Open templates").performClick()
        compose.onNodeWithTag("editTemplate").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Close templates").performClick()
        compose.onNodeWithTag("stampSheet").assertDoesNotExist()
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
}
