package io.github.aryeh95.radarcount.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.SettingsRepository

/**
 * The app's one screen: a tab row in the style other Karoo extensions use,
 * with the live status, the app-wide settings, a card per data field, and
 * a Developer tab once it has been unlocked. The floating back pill closes
 * the app.
 */
@Composable
fun MainScreen(repository: SettingsRepository, extension: RadarCountExtension?, onClose: () -> Unit) {
    val settings by repository.settings.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val titles = buildList {
        add(stringResource(R.string.tab_status))
        add(stringResource(R.string.tab_settings))
        add(stringResource(R.string.tab_radar_field))
        if (settings.developerMode) add(stringResource(R.string.tab_developer))
    }
    if (tab >= titles.size) tab = 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Fixed row: every tab visible. Four titles share 256 dp on a
            // Karoo 3, so the default 16 dp tab padding is replaced by 4 dp.
            TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
                titles.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }) {
                        Text(
                            title,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp)
                        )
                    }
                }
            }
            when (tab) {
                0 -> StatusTab(extension, onResetCount = { extension?.radarEngine?.resetPassCounts() })
                1 -> GeneralSettingsTab(repository)
                2 -> FieldsTab(repository, extension)
                3 -> DeveloperTab(repository, extension)
            }
        }
        BackPill(onClose, Modifier.align(Alignment.BottomStart))
    }
}
