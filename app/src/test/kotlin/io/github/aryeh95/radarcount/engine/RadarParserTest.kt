package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RadarParser")
class RadarParserTest {

    private val parser = RadarParser("threat", "error", (1..8).map { "range$it" })

    @Test
    @DisplayName("an empty data point is level 0 with no targets")
    fun emptyMap() {
        assertThat(parser.errorCode(emptyMap<String, Double>())).isNull()
        assertThat(parser.parse(emptyMap<String, Double>())).isEqualTo(RadarPacket(0, emptyList()))
    }

    @Test
    @DisplayName("a positive error is reported whatever else the packet holds")
    fun errorField() {
        assertThat(parser.errorCode(mapOf("error" to 2.0, "threat" to 3.0, "range1" to 10.0))).isEqualTo(2)
    }

    @Test
    @DisplayName("an error of 0 is no error")
    fun zeroError() {
        assertThat(parser.errorCode(mapOf("error" to 0.0, "threat" to 1.0))).isNull()
    }

    @Test
    @DisplayName("keeps positive ranges only, in the radar's order")
    fun ranges() {
        val packet = parser.parse(
            mapOf("threat" to 2.0, "range1" to 80.0, "range2" to 0.0, "range3" to 35.5, "range5" to -1.0, "range8" to 120.0)
        )
        assertThat(packet.level).isEqualTo(2)
        assertThat(packet.rangesM).containsExactly(80, 35, 120).inOrder()
    }

    @Test
    @DisplayName("levels 0 to 3 pass through, anything else reads as 0")
    fun levels() {
        fun level(v: Double) = parser.parse(mapOf("threat" to v)).level
        assertThat((0..3).map { level(it.toDouble()) }).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(level(2.9)).isEqualTo(2)
        assertThat(level(4.0)).isEqualTo(0)
        assertThat(level(7.0)).isEqualTo(0)
        assertThat(level(-1.0)).isEqualTo(0)
    }
}
