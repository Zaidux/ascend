package com.mochi.ascend.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.ai.AiClient
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Store
import com.mochi.ascend.notify.Reminders
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(data: AppData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = data.settings

    var baseUrl by remember { mutableStateOf(settings.ai.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.ai.apiKey) }
    var model by remember { mutableStateOf(settings.ai.model) }
    var catName by remember { mutableStateOf(settings.catName) }
    var backup by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            Actions.saveSettings(settings.copy(remindersEnabled = true))
            Reminders.rescheduleAll(context)
            status = "Reminders on."
        } else {
            status = "Notification permission denied."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Everything stays on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader("Theme") }
        item {
            Panel {
                Text(
                    "Tap to switch. The sprite and every rung of the ladder take the accent colour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ThemeSwatches(current = settings.themeId) { id ->
                    Actions.saveSettings(settings.copy(themeId = id))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Dark", { Actions.saveSettings(settings.copy(dark = true)) }, selected = settings.dark)
                    Pill("Light", { Actions.saveSettings(settings.copy(dark = false)) }, selected = !settings.dark)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "Sprite on",
                        { Actions.saveSettings(settings.copy(showCat = true)) },
                        selected = settings.showCat,
                    )
                    Pill(
                        "Sprite off",
                        { Actions.saveSettings(settings.copy(showCat = false)) },
                        selected = !settings.showCat,
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Sprite name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Pill(
                    "Save name",
                    {
                        Actions.saveSettings(settings.copy(catName = catName.trim().ifBlank { "Mochi" }))
                        status = "Name saved."
                    },
                    selected = true,
                )
            }
        }

        item { SectionHeader("Reminders") }
        item {
            Panel {
                Text(
                    "Local notifications only — no server, no push service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "On",
                        {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            if (needsPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Actions.saveSettings(settings.copy(remindersEnabled = true))
                                Store.inBackground { Reminders.rescheduleAll(context) }
                                status = "Reminders on."
                            }
                        },
                        selected = settings.remindersEnabled,
                    )
                    Pill(
                        "Off",
                        {
                            Actions.saveSettings(settings.copy(remindersEnabled = false))
                            Store.inBackground { Reminders.rescheduleAll(context) }
                            status = "Reminders off."
                        },
                        selected = !settings.remindersEnabled,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Set a time on any goal, in the goal editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("AI planner") }
        item {
            Panel {
                Text(
                    "Any OpenAI-compatible endpoint. Without this, the planner still " +
                        "works offline using built-in templates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "Save",
                        {
                            Actions.saveSettings(
                                settings.copy(
                                    ai = settings.ai.copy(
                                        baseUrl = baseUrl.trim(),
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                    ),
                                ),
                            )
                            status = "AI settings saved."
                        },
                        selected = true,
                    )
                    Pill(
                        "Test",
                        {
                            status = "Testing..."
                            scope.launch {
                                val result = runCatching {
                                    AiClient.complete(
                                        settings.ai.copy(
                                            baseUrl = baseUrl.trim(),
                                            apiKey = apiKey.trim(),
                                            model = model.trim(),
                                        ),
                                        "Reply with JSON only.",
                                        "Return {\"ok\":true}",
                                    )
                                }
                                status = result.fold(
                                    onSuccess = { "Connection works." },
                                    onFailure = { "Failed: ${it.message?.take(160)}" },
                                )
                            }
                        },
                    )
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Failed") || status.contains("denied")) Red else Green,
                    )
                }
            }
        }

        item { SectionHeader("Backup") }
        item {
            Panel {
                Text(
                    "Export writes your goals and history to JSON. Paste an export back " +
                        "in to restore, or to move to a new phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = backup,
                    onValueChange = { backup = it },
                    label = { Text("Export / import JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(
                        "Export",
                        {
                            backup = Store.exportText()
                            status = "Exported ${backup.length} characters."
                        },
                    )
                    Pill(
                        "Copy",
                        {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Ascend backup", backup))
                            status = "Copied to clipboard."
                        },
                        selected = backup.isNotBlank(),
                    )
                    Pill(
                        "Import",
                        {
                            status = Store.importText(backup).fold(
                                onSuccess = { "Imported." },
                                onFailure = { "Import failed: ${it.message?.take(120)}" },
                            )
                            Store.inBackground { Reminders.rescheduleAll(context) }
                        },
                        accent = Red,
                    )
                }
            }
        }

        item { SectionHeader("About") }
        item {
            Panel {
                Text("Ascend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "A personal goal ladder. No accounts, no tracking, no network calls " +
                        "except the AI planner you configure yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${data.goals.size} goals · ${data.checkIns.size} check-ins",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A tappable preview of each theme: its four rung colours as a swatch row. */
@Composable
private fun ThemeSwatches(current: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        APP_THEMES.forEach { theme ->
            val selected = theme.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onPick(theme.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(theme.glyph, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    theme.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                theme.horizons.forEach { color ->
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}
