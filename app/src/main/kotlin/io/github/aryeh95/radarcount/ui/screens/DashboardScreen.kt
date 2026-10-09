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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.engine.Units
import io.github.aryeh95.radarcount.ui.theme.RadarColors

/**
 * Status screen: radar state, vehicles passed, approach speed.
 * There are no settings; everything is driven by the Karoo profile.
 */
@Composable
fun DashboardScreen(extension: RadarCountExtension?, onResetCount: () -> Unit) {
    val state = extension?.radarEngine?.status?.collectAsState()?.value ?: RadarStatus.Off
    val passCount = extension?.radarEngine?.passCount?.collectAsState()?.value ?: 0
    val closing = extension?.radarEngine?.closingSpeedMps?.collectAsState()?.value
    val rider = extension?.riderSpeedMps?.collectAsState()?.value ?: 0.0
    val imperial = extension?.useImperial?.collectAsState()?.value ?: false

    val statusColor = when (state) {
        is RadarStatus.Live -> when (state.level) {
            3 -> RadarColors.danger
            0 -> RadarColors.safe
            else -> RadarColors.caution
        }
        else -> RadarColors.neutral
    }
    val statusText = when (state) {
        RadarStatus.Off -> stringResource(R.string.dashboard_not_connected)
        RadarStatus.Searching -> stringResource(R.string.dashboard_searching)
        RadarStatus.Lost -> stringResource(R.string.dashboard_connection_lost)
        is RadarStatus.Live -> when {
            state.vehicles == 0 -> stringResource(R.string.dashboard_clear)
            state.nearestM > 0 -> Units.distanceLabel(state.nearestM, imperial)
            else -> stringResource(R.string.widget_behind)
        }
    }

    val relative = closing?.let { RadarCountExtension.toUserSpeedUnits(it, imperial) }
    val absolute = relative?.plus(RadarCountExtension.toUserSpeedUnits(rider, imperial))
    val unit = Units.speedUnitLabel(imperial)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(stringResource(R.string.widget_count_label), passCount.toString(), "")
            Stat(
                stringResource(R.string.widget_approach_label, unit),
                if (state.traffic != null && relative != null) relative.toString() else "--",
                if (state.traffic != null && absolute != null) stringResource(R.string.widget_absolute_label, absolute, unit) else ""
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onResetCount) {
            Text(stringResource(R.string.dashboard_reset_count), color = RadarColors.accent)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_hint),
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Stat(label: String, value: String, footer: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = RadarColors.textSecondary)
        Text(text = value, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = RadarColors.textPrimary)
        Text(text = footer, style = MaterialTheme.typography.labelMedium, color = RadarColors.textSecondary)
    }
}
