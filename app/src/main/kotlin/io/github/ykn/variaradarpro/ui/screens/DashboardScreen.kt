package io.github.ykn.variaradarpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.github.ykn.variaradarpro.ui.theme.RadarColors
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean dashboard optimized for cycling.
 * Shows radar status at a glance - big, bold, readable while riding.
 */
@Composable
fun DashboardScreen(
    widgetStateFlow: StateFlow<WidgetState>?,
    isNightModeFlow: StateFlow<Boolean>?,
    useImperialFlow: StateFlow<Boolean>?,
    onSettingsClick: () -> Unit
) {
    val widgetState = widgetStateFlow?.collectAsState()?.value ?: WidgetState.NotConnected
    val useImperial = useImperialFlow?.collectAsState()?.value ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.background)
    ) {
        // Top bar: Settings
        TopBar(onSettingsClick = onSettingsClick)

        // Main status - centered
        StatusContent(
            widgetState = widgetState,
            useImperial = useImperial,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun TopBar(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Settings icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RadarColors.surface)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = RadarColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StatusContent(
    widgetState: WidgetState,
    useImperial: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (widgetState) {
            is WidgetState.NotConnected -> DisconnectedStatus()
            is WidgetState.Connecting -> ConnectingStatus()
            is WidgetState.Clear -> ClearStatus()
            is WidgetState.Threat -> ThreatStatus(
                vehicleCount = widgetState.vehicleCount,
                distanceM = widgetState.nearestDistanceM,
                level = widgetState.level,
                useImperial = useImperial
            )
            is WidgetState.ConnectionLost -> ConnectionLostStatus()
        }
    }
}

@Composable
private fun DisconnectedStatus() {
    StatusIcon(
        icon = Icons.Default.LinkOff,
        color = RadarColors.neutral,
        size = 64
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.dashboard_not_connected),
        style = MaterialTheme.typography.headlineMedium,
        color = RadarColors.neutral,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.dashboard_connect_radar),
        style = MaterialTheme.typography.bodyLarge,
        color = RadarColors.textSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ConnectingStatus() {
    StatusIcon(
        icon = Icons.Default.Sensors,
        color = RadarColors.accent,
        size = 64
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.dashboard_searching),
        style = MaterialTheme.typography.headlineMedium,
        color = RadarColors.accent,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ClearStatus() {
    StatusIcon(
        icon = Icons.Outlined.CheckCircle,
        color = RadarColors.safe,
        size = 80
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.dashboard_clear),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = RadarColors.safe,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ThreatStatus(
    vehicleCount: Int,
    distanceM: Int,
    level: ThreatLevel,
    useImperial: Boolean
) {
    val threatColor = when (level) {
        ThreatLevel.CRITICAL -> RadarColors.danger
        ThreatLevel.WARNING -> RadarColors.caution
        ThreatLevel.APPROACHING -> RadarColors.caution
        ThreatLevel.CLEAR -> RadarColors.safe
    }

    // Car icon
    StatusIcon(
        icon = Icons.Default.DirectionsCar,
        color = threatColor,
        size = 56
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Big vehicle count
    Text(
        text = vehicleCount.toString(),
        fontSize = 72.sp,
        fontWeight = FontWeight.Bold,
        color = threatColor,
        textAlign = TextAlign.Center
    )

    // Distance
    if (distanceM > 0) {
        val distanceText = if (useImperial) {
            "${(distanceM * 3.281).toInt()}ft"
        } else {
            stringResource(R.string.distance_meters, distanceM)
        }
        Text(
            text = distanceText,
            style = MaterialTheme.typography.headlineMedium,
            color = RadarColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectionLostStatus() {
    StatusIcon(
        icon = Icons.Default.LinkOff,
        color = RadarColors.danger,
        size = 64
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.dashboard_connection_lost),
        style = MaterialTheme.typography.headlineMedium,
        color = RadarColors.danger,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.dashboard_reconnecting),
        style = MaterialTheme.typography.bodyLarge,
        color = RadarColors.textSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    color: Color,
    size: Int
) {
    Box(
        modifier = Modifier
            .size((size + 32).dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size.dp)
        )
    }
}
