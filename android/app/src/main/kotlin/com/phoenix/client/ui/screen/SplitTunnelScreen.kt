package com.phoenix.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.phoenix.client.domain.model.AppInfo
import com.phoenix.client.ui.theme.PhoenixOrange
import com.phoenix.client.ui.viewmodel.SplitTunnelViewModel

@Composable
fun SplitTunnelScreen(
    onBack: () -> Unit,
    viewModel: SplitTunnelViewModel = hiltViewModel(),
) {
    val isEnabled    by viewModel.isEnabled.collectAsState()
    val excludedApps by viewModel.excludedApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Split Tunnel",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── Enable toggle ─────────────────────────────────────────────────
            item(key = "toggle") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Split Tunnel", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Only apps with the switch ON are routed through the VPN. " +
                                "Apps with the switch OFF bypass it entirely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = PhoenixOrange,
                            checkedTrackColor  = PhoenixOrange.copy(alpha = 0.4f),
                        ),
                    )
                }
                HorizontalDivider()
            }

            if (isEnabled) {
                // ── Select all / Deselect all ─────────────────────────────────
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val throughVpn = installedApps.size - excludedApps
                            .count { pkg -> installedApps.any { it.packageName == pkg } }
                        Text(
                            text = "$throughVpn of ${installedApps.size} apps through VPN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.includeAll() }) {
                            Text("All", color = PhoenixOrange, style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { viewModel.excludeAll() }) {
                            Text("None", color = PhoenixOrange, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    HorizontalDivider()
                }

                // ── App list or loading spinner ───────────────────────────────
                if (installedApps.isEmpty()) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = PhoenixOrange)
                        }
                    }
                } else {
                    items(installedApps, key = { it.packageName }) { app ->
                        AppRow(
                            app      = app,
                            checked  = app.packageName !in excludedApps,
                            onToggle = { viewModel.toggleApp(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor  = PhoenixOrange,
                checkedTrackColor  = PhoenixOrange.copy(alpha = 0.4f),
            ),
        )
    }
}
