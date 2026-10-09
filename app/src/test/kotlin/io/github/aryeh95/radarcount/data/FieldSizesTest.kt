package io.github.aryeh95.radarcount.data

import com.google.common.truth.Truth.assertThat
import io.hammerhead.karooext.models.ViewConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FieldSizes")
class FieldSizesTest {

    @Test
    @DisplayName("a size round-trips with its alignment")
    fun roundTrip() {
        for (alignment in ViewConfig.Alignment.entries) {
            val c = ViewConfig(gridSize = 30 to 12, viewSize = 238 to 126, textSize = 41, alignment = alignment)
            assertThat(FieldSizes.decode(FieldSizes.encode(c))).isEqualTo(c)
        }
    }

    @Test
    @DisplayName("only the size is kept, not whether it was a preview")
    fun previewFlagDropped() {
        val c = ViewConfig(gridSize = 60 to 15, viewSize = 478 to 160, textSize = 69, preview = true)
        assertThat(FieldSizes.decode(FieldSizes.encode(c))).isEqualTo(c.copy(preview = false))
    }

    @Test
    @DisplayName("a size saved without its alignment reads as right-aligned")
    fun fiveParts() {
        assertThat(FieldSizes.decode("30,15,238,148,50")).isEqualTo(ViewConfig(gridSize = 30 to 15, viewSize = 238 to 148, textSize = 50))
    }

    @Test
    @DisplayName("missing or unreadable sizes give null, so the preview falls back to a half-width tile")
    fun unreadable() {
        for (bad in listOf(null, "", "30,15,238", "30,15,238,148,x", "30,15,0,148,50", "30,15,238,148,50,UP", "1,2,3,4,5,RIGHT,7")) {
            assertThat(FieldSizes.decode(bad)).isNull()
        }
    }
}
