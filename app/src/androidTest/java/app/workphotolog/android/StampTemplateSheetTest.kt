package app.workphotolog.android

import android.view.KeyEvent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StampTemplateSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fieldKitsApplyAndTheirBundledMarksSurviveReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = StampLogoStore(context)
        assertEquals(21, fieldKitTemplates.map { it.tag }.toSet().size)
        for (kit in fieldKitTemplates) {
            val restored = StampTemplate.fromJson(kit.template.toJson())
            assertEquals(kit.template, restored)
            val logo = store.loadForRender(restored.logoId)
            assertNotNull(kit.title, logo)
            logo?.recycle()
        }

        var applied: StampTemplate? = null
        compose.setContent {
            MaterialTheme {
                StampTemplateSheet(StampTemplate(), onApply = { applied = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("fieldKitStyles").performClick()
        compose.onNodeWithTag("exploreCategorySHIPPING").performScrollTo().performClick()
        compose.onNodeWithTag("kitPatrolPost").assertDoesNotExist()
        compose.onNodeWithTag("kitParcelRoute").performScrollTo().performClick()
        compose.onNodeWithTag("editTemplate").performClick()
        compose.onNodeWithTag("stampEditorLOGO").performScrollTo().performClick()
        val logoPreview = hasContentDescription("Logo crop preview")
        compose.waitUntil(10_000) { compose.onAllNodes(logoPreview).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(logoPreview).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("applyTemplate").performClick()
        compose.runOnIdle {
            assertEquals(BuiltInStampMark.PARCEL.id, applied?.logoId)
            assertEquals(StampLayout.TICKET, applied?.layout)
        }
    }

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

    @Test
    fun layerInspectorAppliesAndRestoresIndependentStyle() {
        val initial = StampTemplate(preset = StampPreset.FIELD_NOTE)
        var applied: StampTemplate? = null
        compose.setContent {
            MaterialTheme {
                StampTemplateSheet(initial, onApply = { applied = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("editTemplate").performClick()
        compose.onNodeWithTag("stampEditorLAYERS").performScrollTo().performClick()
        compose.onNodeWithTag("stampSeparateLayers").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("stampDragGroupCLOCK").performScrollTo().performClick()
        compose.onNodeWithTag("stampLayerFontMONO").performScrollTo().performClick()
        compose.onNodeWithTag("stampLayerColorGOLD").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
        compose.runOnIdle {
            val updated = checkNotNull(applied)
            val restored = StampTemplate.fromJson(updated.toJson())
            assertEquals(updated, restored)
            assertEquals(StampTypography.MONO, restored.styleFor(StampGroup.CLOCK).typography)
            assertEquals(StampTextColor.GOLD.argb, restored.styleFor(StampGroup.CLOCK).colorArgb)
            assertEquals(StampLayerStyle(), restored.styleFor(StampGroup.TITLE))
        }
    }

    @Test
    fun draggingTheTitleMovesItWithoutMovingTheClock() {
        val geometry = StampPreviewGeometry()
        var template by mutableStateOf(StampTemplate(preset = StampPreset.FIELD_NOTE, layerEditing = true))
        var selected by mutableStateOf(StampGroup.WHOLE)
        compose.setContent {
            MaterialTheme {
                StudioPreviewFrame(
                    template,
                    Modifier.width(250.dp).height(270.dp),
                    movingGroup = selected,
                    placement = geometry,
                    onSelectLayer = { selected = it },
                    onMove = { group, offset -> template = template.withOffset(group, offset) },
                )
            }
        }
        val start = compose.runOnIdle {
            val title = checkNotNull(geometry.groups[StampGroup.TITLE])
            Offset(title.centerX(), title.centerY())
        }
        compose.onNodeWithTag("stampDragCanvas").performTouchInput {
            down(start)
            moveBy(Offset(0f, -height * 0.15f))
            moveBy(Offset(0f, -height * 0.15f))
            up()
        }
        compose.runOnIdle {
            assertEquals(StampGroup.TITLE, selected)
            assertTrue(template.offsetFor(StampGroup.TITLE).y < -0.10f)
            assertEquals(StampOffset(), template.offsetFor(StampGroup.CLOCK))
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
}
