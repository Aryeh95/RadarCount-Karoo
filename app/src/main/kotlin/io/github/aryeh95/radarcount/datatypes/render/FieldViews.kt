package io.github.aryeh95.radarcount.datatypes.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import io.github.aryeh95.radarcount.R
import io.hammerhead.karooext.models.ViewConfig

/**
 * One frame of a field: the views sent to the Karoo and the images inside
 * them, by ImageView id, and for a single field the header it drew.
 */
class FieldFrame internal constructor(
    val views: RemoteViews,
    val images: Map<Int, FieldImage>,
    private val layout: Int,
    private val tint: Int?,
    val header: HeaderImage? = null
) {
    /** True when [other] puts exactly the same pixels on screen, so this frame need not be sent. */
    fun looksLike(other: FieldFrame?): Boolean =
        other != null && layout == other.layout && tint == other.tint && images.keys == other.images.keys &&
            images.all { (id, image) -> image.bitmap.sameAs(other.images.getValue(id).bitmap) }
}

/**
 * Wraps field bitmaps in RemoteViews. Only calls the Karoo 2's Android 8
 * accepts are used: setImageViewBitmap, setViewVisibility and
 * setBackgroundColor. Alignment is baked into the layouts.
 */
object FieldViews {

    /**
     * The Radar field: the header strip at the top, if shown, and the cells
     * centred under it; together the size of the tile. [tint] paints the
     * whole view to show its real bounds.
     */
    fun tile(context: Context, image: ComboImage, tint: Int?): FieldFrame {
        val layout = if (image.header != null) R.layout.field_tile_header else R.layout.field_tile
        val views = RemoteViews(context.packageName, layout)
        val images = mutableMapOf(R.id.field_image to image.body)
        image.header?.let { images[R.id.field_header] = it }
        for ((id, i) in images) views.setImageViewBitmap(id, i.bitmap)
        if (tint != null) views.setInt(R.id.field_root, "setBackgroundColor", tint)
        return FieldFrame(views, images, layout, tint)
    }

    /**
     * A single field: its [header] at the top and the value under it,
     * aligned per the field setting. Without a header (the Radar field's
     * NO RADAR) the value has the whole tile.
     */
    fun value(context: Context, header: HeaderImage?, image: FieldImage, alignment: ViewConfig.Alignment): FieldFrame {
        val layout = when (alignment) {
            ViewConfig.Alignment.LEFT -> R.layout.field_value_left
            ViewConfig.Alignment.CENTER -> R.layout.field_value_center
            ViewConfig.Alignment.RIGHT -> R.layout.field_value_right
        }
        val views = RemoteViews(context.packageName, layout)
        val images = mutableMapOf(R.id.field_image to image)
        if (header != null) images[R.id.field_header] = header.image else views.setViewVisibility(R.id.field_header, View.GONE)
        for ((id, i) in images) views.setImageViewBitmap(id, i.bitmap)
        return FieldFrame(views, images, layout, null, header)
    }

    /**
     * Applies [views] the way the Karoo does, in a view of exactly
     * [widthPx] x [heightPx], and draws the result: the settings preview,
     * pixel for pixel what the field shows. Main thread only.
     */
    fun draw(context: Context, views: RemoteViews, widthPx: Int, heightPx: Int): Bitmap {
        val view = views.apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}
