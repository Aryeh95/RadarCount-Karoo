package io.github.ykn.variaradarpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ykn.variaradarpro.ui.theme.Accent
import io.github.ykn.variaradarpro.ui.theme.RadarColors
import io.github.ykn.variaradarpro.ui.theme.Surface

/**
 * Compact design system for Karoo 3 (~2.4" screen).
 * Minimal padding, no wasted space, no tooltips.
 */
private object Design {
    val touchTarget = 48.dp
    val cornerRadius = 12.dp
    val horizontalPadding = 16.dp
    val verticalPadding = 12.dp
    val sectionGap = 16.dp
}

/**
 * Section with title and card container.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = Design.horizontalPadding,
                top = Design.sectionGap,
                bottom = 8.dp
            )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(Design.cornerRadius)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

/**
 * Toggle switch with optional tappable value chip.
 */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: String? = null,
    onValueClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Design.touchTarget)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = Design.horizontalPadding, vertical = Design.verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else RadarColors.textSecondary,
            modifier = Modifier.weight(1f)
        )

        // Tappable value chip (shown when checked and value provided)
        if (value != null && checked && onValueClick != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(
                        color = Accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onValueClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.4f),
                uncheckedThumbColor = RadarColors.textSecondary,
                uncheckedTrackColor = Surface
            )
        )
    }
}

/**
 * Clickable item with value chip. Tap cycles through values.
 */
@Composable
fun SettingsItem(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Design.touchTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Design.horizontalPadding, vertical = Design.verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else RadarColors.textSecondary,
            modifier = Modifier.weight(1f)
        )

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(
                        color = Accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (showArrow) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = RadarColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Info row for read-only data.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Design.horizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = RadarColors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
