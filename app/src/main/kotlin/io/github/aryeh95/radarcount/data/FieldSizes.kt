package io.github.aryeh95.radarcount.data

import io.hammerhead.karooext.models.ViewConfig

/**
 * The size the Karoo last gave a field in a ride, kept per field type so
 * each settings preview draws at its field's real size. Stored as
 * "gw,gh,vw,vh,text,ALIGNMENT"; a size saved before the alignment was kept
 * ("gw,gh,vw,vh,text") reads as right-aligned.
 */
object FieldSizes {

    fun encode(config: ViewConfig): String =
        "${config.gridSize.first},${config.gridSize.second},${config.viewSize.first},${config.viewSize.second},${config.textSize},${config.alignment.name}"

    /** The size [encoded] holds, or null if it is missing or unreadable. */
    fun decode(encoded: String?): ViewConfig? {
        if (encoded.isNullOrEmpty()) return null
        val parts = encoded.split(',')
        if (parts.size != 5 && parts.size != 6) return null
        val n = parts.take(5).map { it.toIntOrNull() ?: return null }
        if (n[2] <= 0 || n[3] <= 0 || n[4] <= 0) return null
        val alignment = parts.getOrNull(5)?.let { a -> ViewConfig.Alignment.entries.firstOrNull { it.name == a } ?: return null }
            ?: ViewConfig.Alignment.RIGHT
        return ViewConfig(gridSize = n[0] to n[1], viewSize = n[2] to n[3], textSize = n[4], alignment = alignment)
    }

    /** The size the field [typeId] was last shown at in a ride, from [settings], or null if it has not been shown yet. */
    fun saved(settings: Settings, typeId: String): ViewConfig? = decode(settings.fieldSizes[typeId])
}
