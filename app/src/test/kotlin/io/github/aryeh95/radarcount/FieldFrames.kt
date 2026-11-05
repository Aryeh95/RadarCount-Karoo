package io.github.aryeh95.radarcount

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.RemoteViews
import fi.nikosavola.karooext.testing.inflate
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.datatypes.FieldDataType.RenderInput
import io.github.aryeh95.radarcount.datatypes.renderField
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.hammerhead.karooext.models.ViewConfig

// The fields are drawn into bitmaps, so a frame is checked against what the field's own render
// draws for the state the test expects.
internal typealias FieldImages = Map<Int, Bitmap>

internal fun framesOf(context: Context, config: ViewConfig, frame: RemoteViews): FieldImages {
    val root = frame.inflate(context, config)
    return listOf(R.id.field_image, R.id.field_header).mapNotNull { id ->
        val bitmap = (root.findViewById<ImageView>(id)?.drawable as? BitmapDrawable)?.bitmap
        bitmap?.let { id to it }
    }.toMap()
}

internal fun drawn(
    context: Context,
    config: ViewConfig,
    typeId: String,
    state: RadarStatus,
    passCount: Int = 0,
    closingSpeedMps: Double? = null,
    riderSpeedMps: Double = 0.0,
    useImperial: Boolean = false,
    settings: Settings = Settings(),
    rideTimeMs: Long = 0L,
): FieldImages {
    val input = RenderInput(state, passCount, closingSpeedMps, riderSpeedMps, useImperial, settings, rideTimeMs)
    val frame = requireNotNull(renderField(typeId, context, input, config, 1f, System.currentTimeMillis()))
    return frame.images.mapValues { it.value.bitmap }
}

internal fun sameImages(actual: FieldImages, wanted: FieldImages) =
    actual.keys == wanted.keys && actual.all { (id, bitmap) -> bitmap.sameAs(wanted.getValue(id)) }
