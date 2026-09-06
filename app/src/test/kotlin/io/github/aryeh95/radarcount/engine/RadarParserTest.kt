package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RadarParser")
class RadarParserTest {

    private val ranges = (1..8).map { "range$it" }
    private val parser = RadarParser("threat", "error", ranges)

    @Test
    @DisplayName("empty map is a clear snapshot with no targets")
    fun emptyMap() {
        val result = parser.parse(emptyMap<String, Double>()) as RadarParseResult.Data
        assertThat(result.snapshot.threatLevel).isEqualTo(ThreatLevel.CLEAR)
        assertThat(result.snapshot.targetDistancesM).isEmpty()
        assertThat(result.snapshot.vehicleCount).isEqualTo(0)
        assertThat(result.snapshot.nearestDistanceM).isEqualTo(0)
    }

    @Test
    @DisplayName("error field wins over everything else")
    fun errorField() {
        val result = parser.parse(mapOf("error" to 2.0, "threat" to 3.0, "range1" to 10.0))
        assertThat(result).isEqualTo(RadarParseResult.Error(2))
    }

    @Test
    @DisplayName("zero error is not an error")
    fun zeroError() {
        val result = parser.parse(mapOf("error" to 0.0, "threat" to 1.0))
        assertThat(result).isInstanceOf(RadarParseResult.Data::class.java)
    }

    @Test
    @DisplayName("collects only positive ranges, keeps order, nearest is min")
    fun ranges() {
        val result = parser.parse(
            mapOf("threat" to 2.0, "range1" to 80.0, "range2" to 0.0, "range3" to 35.5, "range5" to -1.0, "range8" to 120.0)
        ) as RadarParseResult.Data
        assertThat(result.snapshot.threatLevel).isEqualTo(ThreatLevel.WARNING)
        assertThat(result.snapshot.targetDistancesM).containsExactly(80, 35, 120).inOrder()
        assertThat(result.snapshot.vehicleCount).isEqualTo(3)
        assertThat(result.snapshot.nearestDistanceM).isEqualTo(35)
    }

    @Test
    @DisplayName("unknown threat level maps to CLEAR")
    fun unknownThreat() {
        assertThat(RadarParser.mapThreatLevel(7)).isEqualTo(ThreatLevel.CLEAR)
        assertThat(RadarParser.mapThreatLevel(-1)).isEqualTo(ThreatLevel.CLEAR)
    }
}
