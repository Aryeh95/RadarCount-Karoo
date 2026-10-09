package io.github.aryeh95.radarcount.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.engine.Units
import io.github.aryeh95.radarcount.ui.theme.ApproachAmber
import io.github.aryeh95.radarcount.ui.theme.CardWhite
import io.github.aryeh95.radarcount.ui.theme.ClearGreen
import io.github.aryeh95.radarcount.ui.theme.DangerRed
import io.github.aryeh95.radarcount.ui.theme.Ink
import io.github.aryeh95.radarcount.ui.theme.InkMuted
import io.github.aryeh95.radarcount.ui.theme.KarooSlate

/**
 * The Status tab: what the radar sees now, the cars passed and the last
 * approach speed, read from the running extension (all blank without one).
 */
@Composable
fun StatusTab(extension: RadarCountExtension?, onResetCount: () -> Unit) {
    val engine = extension?.radarEngine
    val status = engine?.status?.collectAsState()?.value ?: RadarStatus.Off
    val passed = engine?.passCount?.collectAsState()?.value ?: 0
    val closingMps = engine?.closingSpeedMps?.collectAsState()?.value
    val riderMps = extension?.riderSpeedMps?.collectAsState()?.value ?: 0.0
    val imperial = extension?.imperialUnits?.collectAsState()?.value ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusLine(status, imperial)
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(stringResource(R.string.status_passed), passed.toString())
            ApproachStat(closingMps.takeIf { status.traffic != null }, riderMps, imperial)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onResetCount) {
            Text(stringResource(R.string.status_reset_count), color = KarooSlate)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.status_hint),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** The radar's state in a word or two, or the nearest car's distance, coloured by its threat level. */
@Composable
private fun StatusLine(status: RadarStatus, imperial: Boolean) {
    val text = when (status) {
        RadarStatus.Off -> stringResource(R.string.status_off)
        RadarStatus.Searching -> stringResource(R.string.status_searching)
        RadarStatus.Lost -> stringResource(R.string.status_lost)
        is RadarStatus.Live -> when {
            status.vehicles == 0 -> stringResource(R.string.status_clear)
            status.nearestM > 0 -> Units.distanceLabel(status.nearestM, imperial)
            else -> stringResource(R.string.status_car_behind)
        }
    }
    val color = if (status is RadarStatus.Live) threatColor(status.level) else InkMuted
    Text(text = text, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
}

/** Green with nothing behind, red at the top level, amber for anything between. */
private fun threatColor(level: Int) = when (level) {
    0 -> ClearGreen
    3 -> DangerRed
    else -> ApproachAmber
}

/** The car behind's closing speed, its road speed under it; dashes while no car is tracked or measured. */
@Composable
private fun ApproachStat(closingMps: Double?, riderMps: Double, imperial: Boolean) {
    val unit = Units.speedUnitLabel(imperial)
    val closing = closingMps?.let { RadarCountExtension.toUserSpeedUnits(it, imperial) }
    val road = closing?.plus(RadarCountExtension.toUserSpeedUnits(riderMps, imperial))
    Stat(
        label = stringResource(R.string.status_approach, unit),
        value = closing?.toString() ?: "--",
        footer = road?.let { stringResource(R.string.status_road_speed, it, unit) } ?: ""
    )
}

@Composable
private fun Stat(label: String, value: String, footer: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Text(text = value, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(text = footer, style = MaterialTheme.typography.labelMedium, color = InkMuted)
    }
}
