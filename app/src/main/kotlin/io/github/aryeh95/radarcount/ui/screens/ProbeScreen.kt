package io.github.aryeh95.radarcount.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.probe.ProbeResult
import io.github.aryeh95.radarcount.probe.ProbeRunner
import io.github.aryeh95.radarcount.probe.ProbeStore
import io.github.aryeh95.radarcount.ui.theme.RadarColors
import kotlinx.coroutines.launch

/**
 * Connection test for the upload feature. Points at tools/probe_server.py
 * and reports what the Karoo can and cannot do. Not part of a release.
 */
@Composable
fun ProbeScreen(store: ProbeStore, redirectTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { ProbeRunner(context, RadarCountExtension.instance?.karooSystem, store) }
    var url by remember { mutableStateOf(store.serverUrl) }
    var running by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<ProbeResult>() }

    fun add(r: ProbeResult) {
        val i = results.indexOfFirst { it.name == r.name }
        if (i >= 0) results[i] = r else results.add(r)
    }

    fun runAll() {
        store.serverUrl = url
        results.clear()
        running = true
        scope.launch {
            runner.runAll(::add)
            running = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { runAll() }

    // A redirect from the browser landed in MainActivity: finish the exchange.
    LaunchedEffect(redirectTick) {
        if (redirectTick > 0 && store.lastRedirect != null) add(runner.finishBrowserLogin())
    }

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
            TextButton(onClick = onBack) { Text("‹ Back", color = RadarColors.accent) }
            Text("Connection test", style = MaterialTheme.typography.titleLarge, color = RadarColors.textPrimary)
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running && url.isNotBlank(),
                onClick = {
                    val granted = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    if (granted) runAll() else permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            ) { Text(if (running) "Running…" else "Run checks") }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !running && url.isNotBlank(), onClick = { store.serverUrl = url; add(runner.startBrowserLogin()) }) {
                Text("Browser login", color = RadarColors.accent)
            }
            TextButton(
                enabled = !running && url.isNotBlank(),
                onClick = {
                    store.serverUrl = url
                    running = true
                    scope.launch { runner.deviceCodeLogin(::add); running = false }
                }
            ) { Text("Device code", color = RadarColors.accent) }
        }
        Spacer(Modifier.height(8.dp))
        results.forEach { r ->
            val mark = when (r.ok) { true -> "✓"; false -> "✗"; null -> "•" }
            val color = when (r.ok) { true -> Color(0xFF4CAF50); false -> Color(0xFFF44336); null -> RadarColors.textSecondary }
            Text("$mark ${r.name}", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                r.detail,
                color = RadarColors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
            )
        }
    }
}
