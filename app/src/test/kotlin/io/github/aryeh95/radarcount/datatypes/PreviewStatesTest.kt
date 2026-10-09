package io.github.aryeh95.radarcount.datatypes

import io.github.aryeh95.radarcount.data.PassHoldSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreviewStatesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun withoutAHoldThePreviewHasTwoFrames() {
        val states = FieldDataType.previewStates(PassHoldSetting.OFF, 1, now)
        assertEquals(2, states.size)
        assertNull(states.last().lastPass)
    }

    @Test
    fun aHeldPassAddsAThirdFrameThatBothFieldsShow() {
        for (hold in listOf(PassHoldSetting.S3, PassHoldSetting.S5, PassHoldSetting.S10)) {
            val states = FieldDataType.previewStates(hold, 7, now)
            assertEquals(3, states.size)
            val passed = states[2]
            val speed = passed.copy(settings = passed.settings.copy(speedPassHold = hold))
            val combo = passed.copy(settings = passed.settings.copy(comboPassHold = hold))
            assertNotNull(ApproachSpeedDataType.held(speed, now + 100), "Vehicle Speed holds the sample pass at $hold")
            assertNotNull(ComboDataType.held(combo, now + 100), "Radar holds the sample pass at $hold")
            assertEquals(7, ApproachSpeedDataType.held(speed, now + 100)?.seq)
        }
    }

    @Test
    fun theApproachingAndClearFramesHoldNothing() {
        val states = FieldDataType.previewStates(PassHoldSetting.S5, 1, now)
        for (input in states.take(2)) {
            val s = input.copy(settings = input.settings.copy(speedPassHold = PassHoldSetting.S5))
            assertNull(ApproachSpeedDataType.held(s, now + 100))
        }
    }
}
