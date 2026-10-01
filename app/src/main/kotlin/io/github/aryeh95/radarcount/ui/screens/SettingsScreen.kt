package io.github.aryeh95.radarcount.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import io.hammerhead.karooext.models.ViewConfig
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import io.github.aryeh95.radarcount.BuildConfig
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.ComboIdleSetting
import io.github.aryeh95.radarcount.datatypes.ComboLayout
import io.github.aryeh95.radarcount.engine.Units
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.data.SensitivitySetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.github.aryeh95.radarcount.ui.theme.RadarColors
import kotlinx.coroutines.launch

/** Scrolling column shared by the settings tabs, with room at the bottom for the back pill. */
@Composable
private fun TabColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
        Spacer(modifier = Modifier.height(72.dp))
    }
}

/** General settings: units, colours, speed mode, counting sensitivity, reset behaviour. */
@Composable
fun GeneralSettingsTab(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }

    TabColumn {
        EnumDropdown(
            title = stringResource(R.string.settings_units),
            current = settings.units,
            labels = listOf(stringResource(R.string.settings_units_auto), stringResource(R.string.settings_units_metric), stringResource(R.string.settings_units_imperial))
        ) { save(settings.copy(units = it)) }
        EnumDropdown(
            title = stringResource(R.string.settings_theme),
            current = settings.theme,
            labels = listOf(stringResource(R.string.settings_theme_auto), stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_dark))
        ) { save(settings.copy(theme = it)) }
        EnumDropdown(
            title = stringResource(R.string.settings_speed),
            current = settings.speed,
            labels = listOf(stringResource(R.string.settings_speed_relative), stringResource(R.string.settings_speed_absolute))
        ) { save(settings.copy(speed = it)) }
        Hint(stringResource(R.string.settings_speed_hint))
        EnumDropdown(
            title = stringResource(R.string.settings_sensitivity),
            current = settings.sensitivity,
            labels = listOf(stringResource(R.string.settings_sensitivity_strict), stringResource(R.string.settings_sensitivity_normal), stringResource(R.string.settings_sensitivity_relaxed))
        ) { save(settings.copy(sensitivity = it)) }
        val sens = settings.sensitivity
        Hint(
            stringResource(R.string.settings_sensitivity_desc, sens.closeThresholdM, metersToFeet(sens.closeThresholdM)) + " " + stringResource(
                when (sens) {
                    SensitivitySetting.STRICT -> R.string.settings_sensitivity_strict_note
                    SensitivitySetting.NORMAL -> R.string.settings_sensitivity_normal_note
                    SensitivitySetting.RELAXED -> R.string.settings_sensitivity_relaxed_note
                }
            )
        )
        ToggleRow(stringResource(R.string.settings_reset_on_ride_start), settings.resetOnRideStart) {
            save(settings.copy(resetOnRideStart = it))
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = { scope.launch { repository.resetToDefaults() } }) {
            Text(stringResource(R.string.settings_reset_defaults), color = MaterialTheme.colorScheme.primary)
        }
        // Five taps on the version line unlock a Developer tab, as on
        // Android. It holds diagnostics for ride-file analysis.
        var versionTaps by remember { mutableIntStateOf(0) }
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = RadarColors.neutral,
            modifier = Modifier
                .padding(4.dp)
                .clickable {
                    versionTaps++
                    if (versionTaps >= 5) {
                        versionTaps = 0
                        if (!settings.developerMode) save(settings.copy(developerMode = true))
                    }
                }
        )
    }
}

/** Radar combo field layout, with a live preview drawn at the field's true size. */
@Composable
fun RadarFieldTab(repository: SettingsRepository, extension: RadarCountExtension?) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }
    val imperial by (extension?.useImperial ?: kotlinx.coroutines.flow.MutableStateFlow(settings.units == UnitsSetting.IMPERIAL)).collectAsState()
    val liveConfig by (extension?.comboViewConfig ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val fieldConfig = liveConfig ?: extension?.savedComboViewConfig(settings)

    TabColumn {
        Hint(stringResource(R.string.settings_radar_field_hint))
        ComboPreviewRow(settings, imperial, fieldConfig)
        EnumDropdown(
            title = stringResource(R.string.settings_combo_idle),
            current = settings.comboIdle,
            labels = listOf(stringResource(R.string.settings_combo_idle_count), stringResource(R.string.settings_combo_all))
        ) { save(settings.copy(comboIdle = it)) }
        EnumDropdown(
            title = stringResource(R.string.settings_combo_active),
            current = settings.comboActive,
            labels = listOf(stringResource(R.string.settings_combo_active_speed_distance), stringResource(R.string.settings_combo_all))
        ) { save(settings.copy(comboActive = it)) }
        if (settings.comboActive == ComboActiveSetting.SPEED_DISTANCE) {
            ToggleRow(stringResource(R.string.settings_combo_badge), settings.comboBadge) {
                save(settings.copy(comboBadge = it))
            }
        }
        ToggleRow(stringResource(R.string.settings_combo_header), settings.comboHeader) {
            save(settings.copy(comboHeader = it))
        }
        ToggleRow(stringResource(R.string.settings_combo_captions), settings.comboCaptions) {
            save(settings.copy(comboCaptions = it))
        }
        Hint(stringResource(R.string.settings_combo_captions_hint))
        ToggleRow(stringResource(R.string.settings_combo_units_in_captions), settings.comboUnitsInCaptions) {
            save(settings.copy(comboUnitsInCaptions = it))
        }
    }
}

/** Developer diagnostics; the tab appears once unlocked from the version line. */
@Composable
fun DeveloperTab(repository: SettingsRepository, extension: RadarCountExtension?) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }
    val fieldConfig by (extension?.comboViewConfig ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val density = LocalDensity.current.density

    TabColumn {
        Hint(
            fieldConfig?.let { c ->
                "Radar field: grid ${c.gridSize.first}x${c.gridSize.second}, view ${c.viewSize.first}x${c.viewSize.second} px, text ${c.textSize} sp, density $density"
            } ?: "Radar field size not reported yet. Show a ride page with the Radar field once."
        )
        ToggleRow(stringResource(R.string.settings_trace_tracks), settings.traceTracks) {
            save(settings.copy(traceTracks = it))
        }
        Hint(stringResource(R.string.settings_trace_tracks_hint))
        ToggleRow("Tint Radar field bounds", settings.debugFieldBounds) {
            save(settings.copy(debugFieldBounds = it))
        }
        Hint("Paints the Radar field's whole view blue so the area the Karoo actually gives it is visible.")
        TextButton(onClick = { save(settings.copy(developerMode = false, traceTracks = false, debugFieldBounds = false)) }) {
            Text(stringResource(R.string.settings_developer_hide), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The Karoo's floating back button: a light blue pill in the bottom-left corner. */
@Composable
fun BackPill(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(start = 0.dp, bottom = 12.dp).size(width = 72.dp, height = 56.dp),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = RadarColors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

/**
 * Live preview of the Radar combo field in both states, drawn from the same
 * [ComboLayout] plan the field uses, at the proportions of the Karoo's
 * half-width field.
 */
@Composable
private fun ComboPreviewRow(settings: Settings, imperial: Boolean, config: ViewConfig?) {
    val labels = ComboLayout.Labels(
        count = stringResource(R.string.combo_count),
        vehicles = stringResource(R.string.combo_vehicles),
        speedRel = stringResource(R.string.combo_speed_rel),
        speedAbs = stringResource(R.string.combo_speed_abs),
        dist = stringResource(R.string.combo_dist),
        speedUnit = Units.speedUnitCaption(imperial),
        distUnit = Units.distanceUnitCaption(imperial)
    )
    val showsAbsolute = settings.speed == SpeedSetting.ABSOLUTE
    // Sample car chosen for wide digits, the worst case for the fit: 174 m
    // (570 ft) away, closing at 20 m/s (45 mph) on a rider doing 7 m/s.
    val relative = RadarCountExtension.toUserSpeedUnits(20.0, imperial)
    val speed = if (showsAbsolute) relative + RadarCountExtension.toUserSpeedUnits(7.0, imperial) else relative
    val idle = ComboLayout.plan(false, 48, null, null, showsAbsolute, settings, labels)
    val active = ComboLayout.plan(true, 48, speed, Units.distanceValue(174, imperial), showsAbsolute, settings, labels)
    val dark = when (settings.theme) {
        ThemeSetting.DARK -> true
        ThemeSetting.LIGHT -> false
        ThemeSetting.AUTO -> isSystemInDarkTheme()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldPreview(idle, dark, config, settings.comboHeader)
        Text(stringResource(R.string.settings_preview_idle), style = MaterialTheme.typography.bodySmall, color = RadarColors.textSecondary)
        FieldPreview(active, dark, config, settings.comboHeader)
        Text(stringResource(R.string.settings_preview_active), style = MaterialTheme.typography.bodySmall, color = RadarColors.textSecondary)
    }
}

/**
 * Draws a plan the way the field does: the Karoo's header strip above a
 * drawing area of exactly the pixel size the Karoo last gave the field,
 * with fonts from the same [ComboLayout.sizes] rule. Until a Radar field
 * has been shown once since boot, a half-width field is assumed.
 */
@Composable
private fun FieldPreview(plan: ComboLayout.Plan, dark: Boolean, config: ViewConfig?, header: Boolean) {
    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    val label = if (dark) Color(0xFFBBBBBB) else Color(0xFF555555)
    val density = LocalDensity.current.density
    // Fallback before the Karoo has reported a size: its half-width field, 127x67 dp, text 46 sp.
    val cfg = config ?: ViewConfig(gridSize = 30 to 12, viewSize = ((density * 127).toInt() to (density * 67).toInt()), textSize = 46)
    val headerDp = if (header) ComboLayout.HEADER_DP else 0f
    val sz = ComboLayout.sizes(plan, cfg.viewSize.first, cfg.viewSize.second, cfg.textSize, density, wideGrid = cfg.gridSize.first >= 60, headerDp = headerDp)
    val widthDp = (cfg.viewSize.first / density).dp
    val tileDp = cfg.viewSize.second / density
    // Same geometry as the field: our header strip, then the cells centred in the rest.
    Column(
        modifier = Modifier
            .width(widthDp)
            .height(tileDp.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
    ) {
        if (header) {
            Row(modifier = Modifier.fillMaxWidth().height(headerDp.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.ic_radar), contentDescription = null, tint = RadarColors.safe, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RADAR", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        // Compose clips text to its line box where Glance lets the glyphs
        // spill into the padding, so the digits are drawn with a tight line
        // height and the whole block is allowed to overflow the area, which
        // is what the field does on the Karoo.
        Box(modifier = Modifier.fillMaxWidth().height((tileDp - headerDp).dp).padding(horizontal = 2.dp).wrapContentHeight(unbounded = true), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plan.badge != null) {
                    Column {
                        if (plan.cells.any { it.caption != null }) Spacer(modifier = Modifier.height((sz.captionSp * 1.0f).dp))
                        MonoValue(plan.badge, fg, sz.badgeSp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                plan.cells.forEachIndexed { i, c ->
                    if (i > 0) Spacer(modifier = Modifier.width(sz.gapDp.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (c.caption != null) Text(c.caption, color = label, fontSize = sz.captionSp.sp, lineHeight = (sz.captionSp * 1.1f).sp, maxLines = 1)
                        MonoValue(c.value, fg, sz.valueSp)
                    }
                }
            }
        }
    }
}

/** Value text as the field draws it: 97% of the size, monospace, bold. */
@Composable
private fun MonoValue(text: String, color: Color, sizeSp: Int) {
    Text(
        text = text,
        color = color,
        fontSize = (sizeSp * 0.97f).sp,
        lineHeight = (sizeSp * 0.9f).sp,
        fontFamily = FontFamily.Monospace,
        softWrap = false,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

/** Rounded to the nearest 5 ft so the description reads like a rule, not a conversion. */
private fun metersToFeet(m: Int): Int = (Math.round(m * 3.28084 / 5.0) * 5).toInt()

private inline fun <reified T : Enum<T>> next(current: T): T {
    val values = enumValues<T>()
    return values[(current.ordinal + 1) % values.size]
}

/**
 * A setting with a few options, drawn as the Karoo's outlined dropdown with a
 * floating label. [options] are the choices in order; [selected] is the
 * current one; picking a choice calls [onSelect] with its index.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = options[selected],
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { expanded = false; onSelect(i) }
                )
            }
        }
    }
}

/** Cycles an enum setting from a dropdown: the options are listed in declaration order. */
@Composable
private inline fun <reified T : Enum<T>> EnumDropdown(title: String, current: T, labels: List<String>, crossinline onSelect: (T) -> Unit) {
    DropdownRow(title, labels, current.ordinal) { onSelect(enumValues<T>()[it]) }
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
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = RadarColors.textPrimary, modifier = Modifier.weight(1f))
    }
}
