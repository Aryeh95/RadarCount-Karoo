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
import io.github.aryeh95.radarcount.BuildConfig
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.engine.Units
import io.github.aryeh95.radarcount.ui.theme.RadarColors

/**
 * Status screen: radar state, vehicles passed, approach speed.
 * There are no settings; everything is driven by the Karoo profile.
 */
@Composable
fun DashboardScreen(extension: RadarCountExtension?, onSettingsClick: () -> Unit, onResetCount: () -> Unit) {
    val state = extension?.radarEngine?.widgetState?.collectAsState()?.value ?: WidgetState.NotConnected
    val passCount = extension?.radarEngine?.passCount?.collectAsState()?.value ?: 0
    val closing = extension?.radarEngine?.closingSpeedMps?.collectAsState()?.value
    val rider = extension?.riderSpeedMps?.collectAsState()?.value ?: 0.0
    val imperial = extension?.useImperial?.collectAsState()?.value ?: false

    val statusColor = when (state) {
        is WidgetState.Clear -> RadarColors.safe
        is WidgetState.Threat -> when (state.level) {
            ThreatLevel.CRITICAL -> RadarColors.danger
            ThreatLevel.CLEAR -> RadarColors.safe
            else -> RadarColors.caution
        }
        else -> RadarColors.neutral
    }
    val statusText = when (state) {
        is WidgetState.NotConnected -> stringResource(R.string.dashboard_not_connected)
        is WidgetState.Connecting -> stringResource(R.string.dashboard_searching)
        is WidgetState.Clear -> stringResource(R.string.dashboard_clear)
        is WidgetState.Threat -> if (state.nearestDistanceM > 0) {
            Units.formatDistance(state.nearestDistanceM, imperial)
        } else {
            stringResource(R.string.widget_behind)
        }
        is WidgetState.ConnectionLost -> stringResource(R.string.dashboard_connection_lost)
    }

    val relative = closing?.let { RadarCountExtension.toUserSpeedUnits(it, imperial) }
    val absolute = relative?.plus(RadarCountExtension.toUserSpeedUnits(rider, imperial))
    val unit = Units.speedUnitLabel(imperial)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = RadarColors.textSecondary
            )
            TextButton(onClick = onSettingsClick) {
                Text(stringResource(R.string.settings_title), color = RadarColors.accent)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
                if (state is WidgetState.Threat && relative != null) relative.toString() else "--",
                if (state is WidgetState.Threat && absolute != null) stringResource(R.string.widget_absolute_label, absolute, unit) else ""
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.neutral
        )
    }
}

@Composable
private fun Stat(label: String, value: String, footer: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = RadarColors.textSecondary)
        Text(text = value, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = footer, style = MaterialTheme.typography.labelMedium, color = RadarColors.textSecondary)
    }
}
