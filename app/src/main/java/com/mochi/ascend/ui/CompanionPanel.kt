package com.mochi.ascend.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.companion.CompanionService
import com.mochi.ascend.companion.ForegroundWatcher
import com.mochi.ascend.companion.WatchAccessibilityService
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.Settings
import com.mochi.ascend.data.WatchLevel

/**
 * Companion controls.
 *
 * Every watch tier states plainly what it can see, because "he is watching
 * you" is only fair if you know how far he can look.
 */
@Composable
fun CompanionPanel(settings: Settings) {
    val context = LocalContext.current
    val companion = settings.companion
    val overlayOk = CompanionService.canOverlay(context)
    val usageOk = ForegroundWatcher.hasPermission(context)

    Panel {
        Text("Companion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "He walks around on top of your apps. Only he is touchable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        ToggleRow("Put him on screen", "Draws over other apps.", companion.enabled) { on ->
            if (on && !overlayOk) {
                runCatching {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            } else {
                Actions.saveSettings(settings.copy(companion = companion.copy(enabled = on)))
                if (on) CompanionService.start(context) else CompanionService.stop(context)
            }
        }

        if (companion.enabled && !overlayOk) {
            Spacer(Modifier.height(8.dp))
            Pill("Grant overlay permission", {
                runCatching {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            }, selected = true)
        }

        Spacer(Modifier.height(16.dp))
        Text("How much he can see", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        WatchLevel.entries.forEach { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(level.label, {
                    Actions.saveSettings(settings.copy(companion = companion.copy(watch = level)))
                }, selected = companion.watch == level)
                Spacer(Modifier.width(8.dp))
                Text(
                    level.blurb,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (companion.watch == level) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (companion.watch == WatchLevel.APP && !usageOk) {
            Spacer(Modifier.height(8.dp))
            Pill("Grant usage access", { runCatching { context.startActivity(ForegroundWatcher.settingsIntent()) } })
        }
        if (companion.watch == WatchLevel.SCREEN && !WatchAccessibilityService.connected) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn on \"Ascend\" under Settings > Accessibility > Installed apps. " +
                    "That grants broad access to what is on your screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Pill("Open accessibility settings", {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
        }

        Spacer(Modifier.height(16.dp))
        ToggleRow("Speak", "Uses your device's voice. No audio files shipped.", companion.speak) { on ->
            Actions.saveSettings(settings.copy(companion = companion.copy(speak = on)))
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("Hold to speak to him", "Hold the cat, then use the mic.", companion.voiceInput) { on ->
            Actions.saveSettings(settings.copy(companion = companion.copy(voiceInput = on)))
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("Let him joke", "He slips in a joke now and then.", companion.jokes) { on ->
            Actions.saveSettings(settings.copy(companion = companion.copy(jokes = on)))
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("Petting gives XP", "Small reward, entirely optional.", companion.petGivesXp) { on ->
            Actions.saveSettings(settings.copy(companion = companion.copy(petGivesXp = on)))
        }

        if (CompanionService.running) {
            Spacer(Modifier.height(14.dp))
            Pill("Hide him", { CompanionService.stop(context) }, accent = Red)
        }
    }
}