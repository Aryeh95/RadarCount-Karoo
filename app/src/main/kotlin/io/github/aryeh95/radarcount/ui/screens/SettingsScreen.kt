package io.github.aryeh95.radarcount.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aryeh95.radarcount.BuildConfig
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.data.SensitivitySetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.github.aryeh95.radarcount.ui.theme.RadarColors
import kotlinx.coroutines.launch

/**
 * Settings: tap a row to cycle its value, toggles for the booleans.
 * Designed for the Karoo's small touch screen: big rows, no dialogs.
 */
@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.settings_back), color = RadarColors.accent) }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = RadarColors.textPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        CycleRow(
            title = stringResource(R.string.settings_units),
            value = when (settings.units) {
                UnitsSetting.AUTO -> stringResource(R.string.settings_units_auto)
                UnitsSetting.METRIC -> stringResource(R.string.settings_units_metric)
                UnitsSetting.IMPERIAL -> stringResource(R.string.settings_units_imperial)
            },
            onClick = { save(settings.copy(units = next(settings.units))) }
        )
        CycleRow(
            title = stringResource(R.string.settings_theme),
            value = when (settings.theme) {
                ThemeSetting.AUTO -> stringResource(R.string.settings_theme_auto)
                ThemeSetting.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeSetting.DARK -> stringResource(R.string.settings_theme_dark)
            },
            onClick = { save(settings.copy(theme = next(settings.theme))) }
        )
        CycleRow(
            title = stringResource(R.string.settings_speed),
            value = when (settings.speed) {
                SpeedSetting.RELATIVE -> stringResource(R.string.settings_speed_relative)
                SpeedSetting.ABSOLUTE -> stringResource(R.string.settings_speed_absolute)
            },
            onClick = { save(settings.copy(speed = next(settings.speed))) }
        )
        Text(
            text = stringResource(R.string.settings_speed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        CycleRow(
            title = stringResource(R.string.settings_sensitivity),
            value = when (settings.sensitivity) {
                SensitivitySetting.STRICT -> stringResource(R.string.settings_sensitivity_strict)
                SensitivitySetting.NORMAL -> stringResource(R.string.settings_sensitivity_normal)
                SensitivitySetting.RELAXED -> stringResource(R.string.settings_sensitivity_relaxed)
            },
            onClick = { save(settings.copy(sensitivity = next(settings.sensitivity))) }
        )
        Text(
            text = stringResource(R.string.settings_sensitivity_hint),
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        ToggleRow(stringResource(R.string.settings_reset_on_ride_start), settings.resetOnRideStart) {
            save(settings.copy(resetOnRideStart = it))
        }
        ToggleRow(stringResource(R.string.settings_reset_lap), settings.resetLapOnLap) {
            save(settings.copy(resetLapOnLap = it))
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = { scope.launch { repository.resetToDefaults() } }) {
            Text(stringResource(R.string.settings_reset_defaults), color = RadarColors.accent)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.neutral,
            modifier = Modifier.padding(4.dp)
        )
    }
}

private inline fun <reified T : Enum<T>> next(current: T): T {
    val values = enumValues<T>()
    return values[(current.ordinal + 1) % values.size]
}

@Composable
private fun CycleRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = RadarColors.textPrimary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = RadarColors.accent)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = RadarColors.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
