package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RadarStatus")
class RadarStatusTest {

    @Test
    @DisplayName("ranged targets: one vehicle each, nearest is the smallest range")
    fun ranged() {
        val live = RadarStatus.Live.of(RadarPacket(2, listOf(80, 35, 120)))
        assertThat(live).isEqualTo(RadarStatus.Live(level = 2, vehicles = 3, nearestM = 35))
        assertThat(live.traffic).isSameInstanceAs(live)
    }

    @Test
    @DisplayName("a threat level with no range yet is one vehicle at no known distance")
    fun threatWithoutRange() {
        val live = RadarStatus.Live.of(RadarPacket(1, emptyList()))
        assertThat(live.traffic).isNotNull()
        assertThat(live.vehicles).isEqualTo(1)
        assertThat(live.nearestM).isEqualTo(0)
    }

    @Test
    @DisplayName("a range at level 0 is still traffic")
    fun rangeAtLevelZero() {
        assertThat(RadarStatus.Live.of(RadarPacket(0, listOf(60))))
            .isEqualTo(RadarStatus.Live(level = 0, vehicles = 1, nearestM = 60))
    }

    @Test
    @DisplayName("level 0 and no ranges is clear: live, but no traffic")
    fun clear() {
        val live = RadarStatus.Live.of(RadarPacket(0, emptyList()))
        assertThat(live).isEqualTo(RadarStatus.Live.CLEAR)
        assertThat(live.traffic).isNull()
    }

    @Test
    @DisplayName("no traffic while the radar is off, searching or lost")
    fun notLive() {
        for (s in listOf(RadarStatus.Off, RadarStatus.Searching, RadarStatus.Lost)) {
            assertThat(s.traffic).isNull()
        }
    }
}
