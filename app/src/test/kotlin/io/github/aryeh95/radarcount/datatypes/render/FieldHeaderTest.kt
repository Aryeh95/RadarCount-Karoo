package io.github.aryeh95.radarcount.datatypes.render

import com.google.common.truth.Truth.assertThat
import io.hammerhead.karooext.models.ViewConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("FieldHeader")
class FieldHeaderTest {

    private fun cfg(cols: Int, rows: Int, textSize: Int) =
        ViewConfig(
            gridSize = cols to rows,
            viewSize = 238 to 126,
            textSize = textSize,
            alignment = ViewConfig.Alignment.RIGHT,
            boundariesEnabled = true,
            preview = false,
        )

    @ParameterizedTest(name = "{0}x{1} at {2} sp has a {3} sp header")
    @DisplayName("takes the Karoo's header size from the grid span and Label Size")
    @CsvSource(
        "60, 15, 69, 19.2",
        "60, 30, 90, 19.2",
        "60, 12, 60, 17.6",
        "30, 15, 50, 17.6",
        "30, 12, 41, 17.6",
        "30, 12, 46, 15.5",
        "30, 12, 47, 15.5",
        "20, 12, 30, 12",
        "15, 12, 30, 11",
    )
    fun karooSp(cols: Int, rows: Int, textSize: Int, expected: Float) {
        assertThat(FieldHeader.karooSp(cfg(cols, rows, textSize))).isEqualTo(expected)
    }
}
