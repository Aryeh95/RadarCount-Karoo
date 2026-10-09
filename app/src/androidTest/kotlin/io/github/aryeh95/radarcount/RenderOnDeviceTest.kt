package io.github.aryeh95.radarcount

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.ComboIdleSetting
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.datatypes.ApproachSpeedDataType
import io.github.aryeh95.radarcount.datatypes.ClosestDistanceDataType
import io.github.aryeh95.radarcount.datatypes.ComboDataType
import io.github.aryeh95.radarcount.datatypes.FieldDataType
import io.github.aryeh95.radarcount.datatypes.VehicleCountDataType
import io.github.aryeh95.radarcount.datatypes.VehiclesPerHourDataType
import io.github.aryeh95.radarcount.datatypes.render.FieldColors
import io.github.aryeh95.radarcount.engine.TargetTracker
import io.hammerhead.karooext.models.ViewConfig
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every RadarCount field the way the Karoo receives it and applies the views on this
 * device, so a view Android rejects (some RemoteViews calls are not allowed on the Karoo 2's
 * Android 8) shows up here instead of as a blank field on the bike. Every field fills the whole
 * tile, the single fields with their own header. Then maps every glyph and icon of the field's
 * bitmaps, header and value, to where the applied view puts it, and reports CLIP if any falls
 * outside the tile, and SHRINK (a warning, not a failure) if the view had to scale a bitmap down,
 * which softens it. For each single field and size a HEADER line gives the size its name was
 * drawn at against the Karoo's own, to show where it had to shrink to stay on one line, and
 * HEADER-BAD (a failure) if the name is larger than the Karoo's or runs into the icon. Writes
 * one PNG per field, size and state, on the tile colour, plus report.txt, to the app's external
 * files dir:
 *
 *   adb pull /sdcard/Android/data/io.github.aryeh95.radarcount/files/render .
 *
 * Fails at the end, listing every combination that threw or clipped.
 */
@RunWith(AndroidJUnit4::class)
class RenderOnDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private data class Size(val name: String, val config: ViewConfig)

    /** A state to render. Its inputs are rendered in order on one fresh field and the last is checked; earlier ones prime it (the combo's hold). */
    private data class Case(val name: String, val inputs: List<FieldDataType.RenderInput>)

    private fun sizes(): List<Size> {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels
        fun dp(v: Int) = (v * dm.density).toInt()
        fun cfg(grid: Pair<Int, Int>, width: Int, height: Int, text: Int, alignment: ViewConfig.Alignment = ViewConfig.Alignment.RIGHT) =
            ViewConfig(
                gridSize = grid,
                viewSize = width to height,
                textSize = text,
                alignment = alignment,
                boundariesEnabled = true,
                preview = false,
            )
        return listOf(
            // The half-width tiles exactly as the Karoo 2 and Karoo 3 report them, whatever this device is.
            Size("k2-half", cfg(30 to 15, 238, 148, 50)),
            Size("k2-half-left", cfg(30 to 15, 238, 148, 50, ViewConfig.Alignment.LEFT)),
            Size("k2-half-center", cfg(30 to 15, 238, 148, 50, ViewConfig.Alignment.CENTER)),
            // The owner's Karoo 2, half width on a five-row page (Label Size Large).
            Size("k2-half-small", cfg(30 to 12, 238, 126, 41)),
            Size("k2-half-small-left", cfg(30 to 12, 238, 126, 41, ViewConfig.Alignment.LEFT)),
            Size("k2-half-small-center", cfg(30 to 12, 238, 126, 41, ViewConfig.Alignment.CENTER)),
            // The Karoo 3, half width on a five-row page (Label Size Small).
            Size("k3-half", cfg(30 to 12, 238, 126, 46)),
            Size("k3-half-left", cfg(30 to 12, 238, 126, 46, ViewConfig.Alignment.LEFT)),
            Size("k3-half-center", cfg(30 to 12, 238, 126, 46, ViewConfig.Alignment.CENTER)),
            Size("half-small", cfg(30 to 12, w / 2 - 2, dp(67), 46)),
            Size("half", cfg(30 to 15, w / 2 - 2, dp(85), 46)),
            Size("full", cfg(60 to 15, w - 2, dp(85), 69)),
            Size("full-tall", cfg(60 to 30, w - 2, dp(160), 90)),
        )
    }

    /** Cases that differ only for the Vehicle Speed field, which the others skip. */
    private fun speedOnly(case: Case) = case.name.startsWith("tag") || case.name.startsWith("pass-held-tag")

    /** A held pass speed only shows in the Vehicle Speed and Radar fields; the others skip these. */
    private fun passOnly(case: Case) = case.name.startsWith("pass-held")

    private fun cases(): List<Case> {
        val threat = FieldDataType.PREVIEW_INPUT
        val clear = threat.copy(state = WidgetState.Clear, closingSpeedMps = null)
        val all = threat.settings.copy(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)
        val bare = threat.settings.copy(comboHeader = false, comboCaptions = false)
        val glued = threat.settings.copy(comboUnitsInCaptions = false)
        val relative = threat.settings.copy(speedMode = SpeedSetting.RELATIVE, comboSpeedMode = SpeedSetting.RELATIVE)
        // A car that closed at 20 m/s on a rider doing 7 m/s, held for 10 s in both fields. Its time
        // is set when each case is rendered, so the hold has not run out by then.
        val hold = threat.settings.copy(speedPassHold = PassHoldSetting.S10, comboPassHold = PassHoldSetting.S10)
        val pass = TargetTracker.Pass(seq = 1, atMs = 0L, closingMps = 20.0, riderMps = 7.0, cars = 1)
        val held = clear.copy(settings = hold, lastPass = pass)
        // The fastest speed the tracker reports, 40 m/s: three digits, 169 km/h absolute.
        val fast = held.copy(lastPass = pass.copy(closingMps = 40.0), settings = hold.copy(speedMode = SpeedSetting.ABSOLUTE, comboSpeedMode = SpeedSetting.ABSOLUTE))
        return listOf(
            Case("approaching", listOf(threat)),
            Case("clear", listOf(clear)),
            Case("held", listOf(threat, clear)),
            Case("no-radar", listOf(threat.copy(state = WidgetState.NotConnected, closingSpeedMps = null))),
            Case("imperial", listOf(threat.copy(useImperial = true))),
            Case("absolute", listOf(threat.copy(settings = threat.settings.copy(speedMode = SpeedSetting.ABSOLUTE, comboSpeedMode = SpeedSetting.ABSOLUTE)))),
            // Relative speed: Vehicle Speed's header reads VEHICLE REL SPEED, the longest field name.
            Case("relative", listOf(threat.copy(settings = relative))),
            Case("relative-clear", listOf(clear.copy(settings = relative))),
            Case("relative-imperial", listOf(threat.copy(useImperial = true, settings = relative))),
            Case("glued-imperial", listOf(threat.copy(useImperial = true, settings = glued))),
            Case("all", listOf(threat.copy(settings = all))),
            Case("all-clear", listOf(clear.copy(settings = all))),
            Case("bare", listOf(threat.copy(settings = bare))),
            Case("bare-clear", listOf(clear.copy(settings = bare))),
            Case("dark", listOf(threat.copy(settings = threat.settings.copy(theme = ThemeSetting.DARK)))),
            Case("tint", listOf(threat.copy(settings = threat.settings.copy(debugFieldBounds = true)))),
            Case("tint-bare-clear", listOf(clear.copy(settings = bare.copy(debugFieldBounds = true)))),
            // The single fields with their header turned off: the value has the whole tile.
            Case("no-header", listOf(threat.copy(settings = threat.settings.copy(countHeader = false, speedHeader = false, distanceHeader = false, rateHeader = false)))),
            Case("no-header-no-radar", listOf(threat.copy(state = WidgetState.NotConnected, closingSpeedMps = null, settings = threat.settings.copy(countHeader = false, speedHeader = false, distanceHeader = false, rateHeader = false)))),
            // A passed car's speed held after it has gone by: grey and marked PASSED.
            Case("pass-held", listOf(threat.copy(settings = hold), held)),
            Case("pass-held-absolute", listOf(held.copy(settings = hold.copy(speedMode = SpeedSetting.ABSOLUTE, comboSpeedMode = SpeedSetting.ABSOLUTE)))),
            Case("pass-held-imperial", listOf(held.copy(useImperial = true))),
            Case("pass-held-captions-off", listOf(held.copy(settings = hold.copy(comboCaptions = false)))),
            Case("pass-held-bare", listOf(held.copy(settings = hold.copy(comboCaptions = false, comboHeader = false, speedHeader = false)))),
            Case("pass-held-glued-imperial", listOf(held.copy(useImperial = true, settings = hold.copy(comboUnitsInCaptions = false)))),
            Case("pass-held-all", listOf(held.copy(settings = hold.copy(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)))),
            // The next car is on the radar but its speed is not measured yet: its live distance beside the held speed.
            Case("pass-held-next-car-no-speed", listOf(held.copy(state = WidgetState.Threat(ThreatLevel.APPROACHING, vehicleCount = 1, nearestDistanceM = 96)))),
            Case("pass-held-fast", listOf(fast)),
            Case("pass-held-fast-imperial-glued", listOf(fast.copy(useImperial = true, settings = fast.settings.copy(comboUnitsInCaptions = false)))),
            Case("pass-held-fast-all", listOf(fast.copy(settings = fast.settings.copy(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)))),
            // Three-digit mph under the default PASSED MPH caption.
            Case("pass-held-fast-imperial", listOf(fast.copy(useImperial = true))),
            Case("pass-held-fast-all-imperial", listOf(fast.copy(useImperial = true, settings = fast.settings.copy(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)))),
            Case("pass-held-dark", listOf(held.copy(settings = hold.copy(theme = ThemeSetting.DARK)))),
            // A held speed in relative mode, under the VEHICLE REL SPEED header.
            Case("pass-held-relative", listOf(held.copy(settings = hold.copy(speedMode = SpeedSetting.RELATIVE, comboSpeedMode = SpeedSetting.RELATIVE)))),
        )
    }

    private fun newFields(extension: RadarCountExtension): List<FieldDataType> =
        listOf(
            ComboDataType(extension),
            VehicleCountDataType(extension),
            ApproachSpeedDataType(extension),
            ClosestDistanceDataType(extension),
            VehiclesPerHourDataType(extension),
        )

    @Test
    fun everyFieldRendersOnThisDevice() {
        lateinit var extension: RadarCountExtension
        instrumentation.runOnMainSync { extension = RadarCountExtension() }
        val outDir =
            File(context.getExternalFilesDir(null), "render").apply {
                deleteRecursively()
                mkdirs()
            }
        val dm = context.resources.displayMetrics
        val report = StringBuilder()
        report.appendLine(
            "Device: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} " +
                "(API ${android.os.Build.VERSION.SDK_INT}), ${dm.widthPixels}x${dm.heightPixels} px, " +
                "density ${dm.density}"
        )
        val failures = mutableListOf<String>()
        var rendered = 0
        var shrunk = 0

        val cases = cases()
        for (index in newFields(extension).indices) for (size in sizes()) for (case in cases) {
            // A fresh field per case so one case's hold does not leak into the next.
            val type = newFields(extension)[index]
            if (speedOnly(case) && type !is ApproachSpeedDataType) continue
            if (passOnly(case) && type !is ApproachSpeedDataType && type !is ComboDataType) continue
            val name = "${type.typeId}_${size.name}_${case.name}"
            try {
                // A pass is decided just now, so its hold is running.
                val now = System.currentTimeMillis()
                val inputs = case.inputs.map { it.copy(lastPass = it.lastPass?.copy(atMs = now)) }
                if (passOnly(case)) {
                    val held = if (type is ComboDataType) ComboDataType.held(inputs.last(), now) else ApproachSpeedDataType.held(inputs.last(), now)
                    check(held != null) { "the pass is not held" }
                }
                val frame = inputs.map { type.renderForTest(context, it, size.config) }.last()
                // Once per field and size: the name is the same in every state.
                if (case === cases.first()) frame.header?.let {
                    val shrunk = if (it.labelSp < it.karooSp - 0.005f) ", shrunk to ${"%.0f".format(it.labelSp / it.karooSp * 100)}% to fit one line" else ""
                    report.appendLine("HEADER ${type.typeId}_${size.name}: ${it.label} at ${"%.1f".format(it.labelSp)} sp (Karoo's ${"%.1f".format(it.karooSp)} sp)$shrunk")
                    // The name is never larger than the Karoo's and never runs into the icon.
                    val (icon, label) = it.image.glyphs
                    if (it.labelSp > it.karooSp + 0.005f || Rect.intersects(icon, label)) {
                        val line = "HEADER-BAD ${type.typeId}_${size.name}: name $label at ${"%.1f".format(it.labelSp)} sp, icon $icon"
                        failures += line
                        report.appendLine(line)
                    }
                }
                val theme = inputs.last().settings.theme
                val w = size.config.viewSize.first
                val h = size.config.viewSize.second
                val tile = if (FieldColors.night(theme, FieldColors.systemNight(context))) Color.BLACK else Color.WHITE
                var bitmap: Bitmap? = null
                val placed = mutableListOf<RectF>()
                var scale = 1f
                var error: Throwable? = null
                instrumentation.runOnMainSync {
                    try {
                        val parent = FrameLayout(context)
                        val view: View = frame.views.apply(context, parent)
                        parent.addView(view, FrameLayout.LayoutParams(w, h))
                        parent.measure(
                            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
                        )
                        parent.layout(0, 0, w, h)
                        bitmap =
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                val canvas = Canvas(it)
                                canvas.drawColor(tile)
                                parent.draw(canvas)
                            }
                        // Where each image view put each glyph of its bitmap, in view pixels.
                        for ((id, fieldImage) in frame.images) {
                            val image = view.findViewById<ImageView>(id)
                            var x = image.paddingLeft.toFloat()
                            var y = image.paddingTop.toFloat()
                            var v: View = image
                            while (v !== parent) {
                                x += v.left
                                y += v.top
                                v = v.parent as View
                            }
                            placed +=
                                fieldImage.glyphs.map { r ->
                                    RectF(r).also {
                                        image.imageMatrix.mapRect(it)
                                        it.offset(x, y)
                                    }
                                }
                            scale = minOf(scale, RectF(0f, 0f, 100f, 100f).also { image.imageMatrix.mapRect(it) }.width() / 100f)
                        }
                    } catch (t: Throwable) {
                        error = t
                    }
                }
                error?.let { throw it }
                FileOutputStream(File(outDir, "$name.png")).use {
                    bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                rendered++
                val bitmaps = frame.images.values.joinToString(" + ") { "${it.bitmap.width}x${it.bitmap.height}" }
                report.appendLine("OK    $name  (bitmap $bitmaps)")
                if (scale < 0.999f) {
                    shrunk++
                    report.appendLine("SHRINK $name: shown at ${"%.1f".format(scale * 100)}% in ${w}x$h")
                }
                val outside =
                    placed.filter { it.left < -0.5f || it.top < -0.5f || it.right > w + 0.5f || it.bottom > h + 0.5f } +
                        frame.images.values.flatMap { i -> i.clipped.map { RectF(it) } }
                if (outside.isNotEmpty()) {
                    val rects = outside.joinToString { "[%.1f,%.1f,%.1f,%.1f]".format(it.left, it.top, it.right, it.bottom) }
                    val line = "CLIP  $name: $rects outside ${w}x$h"
                    failures += line
                    report.appendLine(line)
                }
            } catch (t: Throwable) {
                val line = "FAIL  $name: ${t.javaClass.name}: ${t.message}"
                failures += line
                report.appendLine(line)
                report.appendLine("      cause: ${generateSequence(t) { it.cause }.last().let { "${it.javaClass.name}: ${it.message}" }}")
            }
        }
        report.appendLine("Rendered $rendered, shrunk $shrunk, failed or clipped ${failures.size}")
        File(outDir, "report.txt").writeText(report.toString())
        assertTrue("Fields failed to render or clipped:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
