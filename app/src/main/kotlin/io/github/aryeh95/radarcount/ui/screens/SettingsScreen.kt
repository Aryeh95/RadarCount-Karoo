package io.github.aryeh95.radarcount.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aryeh95.radarcount.BuildConfig
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.FieldSizes
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.data.SensitivitySetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.github.aryeh95.radarcount.datatypes.ApproachSpeedDataType
import io.github.aryeh95.radarcount.datatypes.ClosestDistanceDataType
import io.github.aryeh95.radarcount.datatypes.ComboDataType
import io.github.aryeh95.radarcount.datatypes.FieldDataType
import io.github.aryeh95.radarcount.datatypes.VehicleCountDataType
import io.github.aryeh95.radarcount.datatypes.VehiclesPerHourDataType
import io.github.aryeh95.radarcount.datatypes.render.FieldColors
import io.github.aryeh95.radarcount.datatypes.render.FieldViews
import io.github.aryeh95.radarcount.datatypes.renderField
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.ui.theme.BackButton
import io.github.aryeh95.radarcount.ui.theme.CardInside
import io.github.aryeh95.radarcount.ui.theme.CardWhite
import io.github.aryeh95.radarcount.ui.theme.Ink
import io.github.aryeh95.radarcount.ui.theme.InkMuted
import io.github.aryeh95.radarcount.ui.theme.KarooSlate
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** App-wide settings: units, colours, speed mode, counting sensitivity, reset behaviour. Each field's own are on the Field tab. */
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
        TextButton(onClick = { scope.launch { repository.resetChoices() } }) {
            Text(stringResource(R.string.settings_reset_defaults), color = MaterialTheme.colorScheme.primary)
        }
        // Five taps on the version line unlock a Developer tab, as on
        // Android. It holds diagnostics for ride-file analysis.
        var versionTaps by remember { mutableIntStateOf(0) }
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
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

/**
 * One collapsible card per data field, in the order of the Karoo's picker:
 * its icon, name and a one-line description, and when opened, live
 * previews drawn by the field's own code at the size the Karoo last gave
 * it, then that field's settings. One card is open at a time.
 */
@Composable
fun FieldsTab(repository: SettingsRepository, extension: RadarCountExtension?) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }
    // Without a running extension, follow the units setting itself.
    val imperial = extension?.imperialUnits?.collectAsState()?.value ?: (settings.units == UnitsSetting.IMPERIAL)
    val noConfigs = remember { MutableStateFlow(emptyMap<String, ViewConfig>()) }
    val liveConfigs by (extension?.fieldViewConfigs ?: noConfigs).collectAsState()
    fun config(typeId: String): ViewConfig? = liveConfigs[typeId] ?: FieldSizes.saved(settings, typeId)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggle(typeId: String) {
        open = if (open == typeId) null else typeId
    }

    val approaching = FieldDataType.PREVIEW_INPUT.copy(settings = settings, useImperial = imperial)
    val clear = approaching.copy(state = RadarStatus.Live.CLEAR, closingSpeedMps = null)
    val passed = clear.copy(lastPass = PREVIEW_PASS)

    TabColumn {
        Hint(stringResource(R.string.settings_fields_hint))

        FieldCard(R.string.datatype_combo, R.string.datatype_combo_desc, R.drawable.ic_radar, open == ComboDataType.TYPE_ID, { toggle(ComboDataType.TYPE_ID) }) {
            Hint(stringResource(R.string.settings_radar_field_hint))
            FieldPreviews(
                ComboDataType.TYPE_ID, config(ComboDataType.TYPE_ID), settings,
                buildList {
                    add(R.string.settings_preview_idle to clear)
                    add(R.string.settings_preview_active to approaching)
                    if (settings.comboPassHold != PassHoldSetting.OFF) add(R.string.settings_preview_passed to passed)
                }
            )
            SpeedModeDropdown(settings.comboSpeedMode) { save(settings.copy(comboSpeedMode = it)) }
            Hint(stringResource(R.string.settings_combo_speed_hint))
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
            PassHoldDropdown(settings.comboPassHold) { save(settings.copy(comboPassHold = it)) }
        }

        FieldCard(R.string.datatype_count, R.string.datatype_count_desc, R.drawable.ic_car, open == VehicleCountDataType.TYPE_ID, { toggle(VehicleCountDataType.TYPE_ID) }) {
            FieldPreviews(VehicleCountDataType.TYPE_ID, config(VehicleCountDataType.TYPE_ID), settings, listOf(null to approaching))
            HeaderToggle(settings.countHeader) { save(settings.copy(countHeader = it)) }
        }

        FieldCard(R.string.datatype_speed, R.string.datatype_speed_desc, R.drawable.ic_speed, open == ApproachSpeedDataType.TYPE_ID, { toggle(ApproachSpeedDataType.TYPE_ID) }) {
            FieldPreviews(
                ApproachSpeedDataType.TYPE_ID, config(ApproachSpeedDataType.TYPE_ID), settings,
                if (settings.speedPassHold == PassHoldSetting.OFF) listOf(null to approaching)
                else listOf(R.string.settings_preview_live to approaching, R.string.settings_preview_passed to passed)
            )
            SpeedModeDropdown(settings.speedMode) { save(settings.copy(speedMode = it)) }
            Hint(stringResource(R.string.settings_speed_hint))
            HeaderToggle(settings.speedHeader) { save(settings.copy(speedHeader = it)) }
            PassHoldDropdown(settings.speedPassHold) { save(settings.copy(speedPassHold = it)) }
        }

        FieldCard(R.string.datatype_distance, R.string.datatype_distance_desc, R.drawable.ic_vehicle_distance, open == ClosestDistanceDataType.TYPE_ID, { toggle(ClosestDistanceDataType.TYPE_ID) }) {
            FieldPreviews(ClosestDistanceDataType.TYPE_ID, config(ClosestDistanceDataType.TYPE_ID), settings, listOf(null to approaching))
            HeaderToggle(settings.distanceHeader) { save(settings.copy(distanceHeader = it)) }
        }

        FieldCard(R.string.datatype_rate, R.string.datatype_rate_desc, R.drawable.ic_car, open == VehiclesPerHourDataType.TYPE_ID, { toggle(VehiclesPerHourDataType.TYPE_ID) }) {
            FieldPreviews(VehiclesPerHourDataType.TYPE_ID, config(VehiclesPerHourDataType.TYPE_ID), settings, listOf(null to approaching))
            HeaderToggle(settings.rateHeader) { save(settings.copy(rateHeader = it)) }
        }
    }
}

/**
 * The sample pass the "After a pass" previews hold: a car that closed at
 * 20 m/s on a rider doing 7 m/s, decided at [PREVIEW_NOW_MS], the time the
 * previews are drawn at, so it is always held.
 */
private const val PREVIEW_NOW_MS = 0L
private val PREVIEW_PASS = FieldDataType.previewPass(seq = 1, atMs = PREVIEW_NOW_MS)

/** The single fields' header on or off. */
@Composable
private fun HeaderToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(stringResource(R.string.settings_field_header), checked, onChange)
    Hint(stringResource(R.string.settings_field_header_hint))
}

/** Absolute (road) or relative (closing) speed, for one field. */
@Composable
private fun SpeedModeDropdown(current: SpeedSetting, onSelect: (SpeedSetting) -> Unit) {
    EnumDropdown(
        title = stringResource(R.string.settings_speed),
        current = current,
        labels = listOf(stringResource(R.string.settings_speed_relative), stringResource(R.string.settings_speed_absolute)),
        onSelect = onSelect
    )
}

/** How long a field keeps a passed car's speed. */
@Composable
private fun PassHoldDropdown(current: PassHoldSetting, onSelect: (PassHoldSetting) -> Unit) {
    EnumDropdown(
        title = stringResource(R.string.settings_pass_hold),
        current = current,
        labels = listOf(
            stringResource(R.string.settings_pass_hold_off), stringResource(R.string.settings_pass_hold_3),
            stringResource(R.string.settings_pass_hold_5), stringResource(R.string.settings_pass_hold_10)
        )
    ) { onSelect(it) }
    Hint(stringResource(R.string.settings_pass_hold_hint))
}

/**
 * A field's settings card in the style of Barberfish's: a header row with
 * the field's icon, name and description that opens and closes it, and
 * [content] (previews and settings) on the surface colour while open.
 */
@Composable
private fun FieldCard(title: Int, description: Int, icon: Int, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, CARD_BORDER, shape)
            .background(CardWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = KarooSlate, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title).uppercase(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.settings_collapse else R.string.settings_expand),
                tint = InkMuted,
                modifier = Modifier.size(28.dp)
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(tween(CARD_ANIM_MS)), exit = shrinkVertically(tween(CARD_ANIM_MS))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInside)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

private val CARD_BORDER = Color(0xFFD6DDE3)
private const val CARD_ANIM_MS = 200

/**
 * Live previews of field [typeId], one per sample with its caption (none
 * for a null caption), drawn at the size [config] the Karoo last gave the
 * field; see [FieldPreview].
 */
@Composable
private fun FieldPreviews(typeId: String, config: ViewConfig?, settings: Settings, samples: List<Pair<Int?, FieldDataType.RenderInput>>) {
    val dark = FieldColors.night(settings.theme, isSystemInDarkTheme())
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((caption, input) in samples) {
            FieldPreview(typeId, input, dark, config)
            if (caption != null) Text(stringResource(caption), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

/**
 * The field's own RemoteViews for [input], drawn by its own render code,
 * applied here and drawn at exactly the pixel size the Karoo last gave the
 * field, on the tile colour; narrowed to the screen if wider. Until the
 * field has been shown in a ride once, a half-width field is assumed. The
 * frame is drawn off the main thread; the last one stays up until the next
 * is ready, so a setting change shows without a blank.
 */
@Composable
private fun FieldPreview(typeId: String, input: FieldDataType.RenderInput, dark: Boolean, config: ViewConfig?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Fallback before the Karoo has reported a size: its half-width field, 127x67 dp, text 46 sp.
    val cfg = config ?: ViewConfig(gridSize = 30 to 12, viewSize = ((density.density * 127).toInt() to (density.density * 67).toInt()), textSize = 46)
    val (w, h) = cfg.viewSize
    // Night mode is a key: AUTO colours follow it, as on the Karoo.
    val uiMode = LocalConfiguration.current.uiMode
    var bitmap by remember(typeId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(typeId, input, cfg, uiMode) {
        val frame = withContext(Dispatchers.Default) {
            renderField(typeId, context, input, cfg, context.resources.displayMetrics.density, PREVIEW_NOW_MS)
        } ?: return@LaunchedEffect
        bitmap = FieldViews.draw(context, frame.views, w, h).asImageBitmap()
    }
    Box(
        modifier = Modifier
            .widthIn(max = with(density) { w.toDp() })
            .fillMaxWidth()
            .aspectRatio(w.toFloat() / h.coerceAtLeast(1))
            .background(if (dark) Color.Black else Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

private val FIELD_TYPE_IDS = listOf(
    ComboDataType.TYPE_ID, VehicleCountDataType.TYPE_ID, ApproachSpeedDataType.TYPE_ID,
    ClosestDistanceDataType.TYPE_ID, VehiclesPerHourDataType.TYPE_ID
)

/** Developer diagnostics; the tab appears once unlocked from the version line. */
@Composable
fun DeveloperTab(repository: SettingsRepository, extension: RadarCountExtension?) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun save(s: Settings) = scope.launch { repository.update(s) }
    val noConfigs = remember { MutableStateFlow(emptyMap<String, ViewConfig>()) }
    val liveConfigs by (extension?.fieldViewConfigs ?: noConfigs).collectAsState()
    val density = LocalDensity.current.density

    TabColumn {
        Hint(
            "Field sizes in a ride (density $density):\n" + FIELD_TYPE_IDS.joinToString("\n") { id ->
                val c = liveConfigs[id] ?: FieldSizes.saved(settings, id)
                "$id: " + (c?.let { "grid ${it.gridSize.first}x${it.gridSize.second}, view ${it.viewSize.first}x${it.viewSize.second} px, text ${it.textSize} sp, ${it.alignment.name.lowercase()}" + if (id in liveConfigs) "" else " (saved)" }
                    ?: "not shown in a ride yet")
            }
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

/**
 * The Karoo's floating back button, as the stock screens and Barberfish draw
 * it: a slate pill tucked against the left edge in the bottom corner, with a
 * thin black arrow.
 */
@Composable
fun BackPill(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(bottom = 16.dp).offset(x = (-8).dp).size(width = 62.dp, height = 50.dp),
        shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
        color = BackButton
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back), modifier = Modifier.size(18.dp), tint = Color.Black)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

/** Rounded to the nearest 5 ft so the description reads like a rule, not a conversion. */
private fun metersToFeet(m: Int): Int = (Math.round(m * 3.28084 / 5.0) * 5).toInt()

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
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
    }
}
