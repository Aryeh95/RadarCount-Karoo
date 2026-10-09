package io.github.aryeh95.radarcount

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots of RadarCount's Field tab for the extension library, taken on
 * the device with its own status bar, exactly as `adb shell screencap` would,
 * and with the settings as they are on the device (nothing is changed):
 *
 *   1-field-tab.png       the five field cards, closed
 *   2-radar-previews.png  the Radar card open at its previews
 *   3-radar-settings.png  the Radar card scrolled to its settings
 *   4-vehicle-speed.png   the Vehicle Speed card open at its preview
 *
 * Run with
 *
 *   adb shell am instrument -w -e class io.github.aryeh95.radarcount.FieldSettingsShotsTest \
 *       io.github.aryeh95.radarcount.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/io.github.aryeh95.radarcount/files/shots
 */
@RunWith(AndroidJUnit4::class)
class FieldSettingsShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    private val outDir: File by lazy {
        File(context.getExternalFilesDir(null), "shots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun str(id: Int) = context.getString(id)

    /** A field card's header, the row that opens and closes it. */
    private fun cardHeader(title: Int): SemanticsNodeInteraction =
        compose.onNode(hasClickAction() and hasText(str(title).uppercase(), substring = true))

    /** Lets the previews, drawn off the main thread, land before a shot. */
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
    }

    /** Scrolls the tab so [y] (px in the window) sits [marginDp] below the top of the scrolling area. */
    private fun scrollToTop(y: Float, marginDp: Float) {
        // The tab's scrolling column, not a dropdown's text field (which can scroll its text too).
        val scroller = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val top = scroller.fetchSemanticsNode().boundsInWindow.top
        val delta = y - top - marginDp * density
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        settle()
    }

    private fun shot(name: String) {
        settle()
        val bitmap: Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "no screenshot for $name" }
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun fieldSettingsShots() {
        compose.onNode(hasClickAction() and hasText(str(R.string.tab_radar_field))).performClick()
        settle()
        shot("1-field-tab")

        cardHeader(R.string.datatype_combo).performClick()
        settle()
        val hint = compose.onNode(hasText(str(R.string.settings_radar_field_hint)))
        scrollToTop(hint.fetchSemanticsNode().boundsInWindow.top, marginDp = 8f)
        shot("2-radar-previews")

        // The "No vehicle on radar" dropdown's label, which sits on the box's top edge.
        val idle = compose.onAllNodes(hasText(str(R.string.settings_combo_idle)), useUnmergedTree = true)[0]
        scrollToTop(idle.fetchSemanticsNode().boundsInWindow.top, marginDp = 4f)
        shot("3-radar-settings")

        // Opening Vehicle Speed closes Radar; scroll so its content starts at the top.
        cardHeader(R.string.datatype_speed).performClick()
        settle()
        scrollToTop(cardHeader(R.string.datatype_speed).fetchSemanticsNode().boundsInWindow.bottom, marginDp = 4f)
        shot("4-vehicle-speed")
    }

    private companion object {
        const val SETTLE_MS = 1500L
    }
}
