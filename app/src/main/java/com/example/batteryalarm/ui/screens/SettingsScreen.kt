package com.example.batteryalarm.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryalarm.PreferencesManager
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onNavigateBack: () -> Unit
) {
    // ── Max Battery Alarm state ──────────────────────────────────────────────
    val maxTarget      by preferencesManager.targetPercentageFlow.collectAsState(initial = 80)
    val maxVibration   by preferencesManager.vibrationEnabledFlow.collectAsState(initial = true)
    val maxRingtone    by preferencesManager.ringtoneUriFlow.collectAsState(initial = null)
    val maxVolume      by preferencesManager.alarmVolumeFlow.collectAsState(initial = 80)

    // ── Low Battery Alarm state ──────────────────────────────────────────────
    val lowEnabled     by preferencesManager.lowAlarmEnabledFlow.collectAsState(initial = false)
    val lowTarget      by preferencesManager.lowTargetPercentageFlow.collectAsState(initial = 20)
    val lowVibration   by preferencesManager.lowVibrationEnabledFlow.collectAsState(initial = true)
    val lowRingtone    by preferencesManager.lowRingtoneUriFlow.collectAsState(initial = null)
    val lowVolume      by preferencesManager.lowAlarmVolumeFlow.collectAsState(initial = 80)

    val coroutineScope = rememberCoroutineScope()

    // ── Ringtone pickers ─────────────────────────────────────────────────────
    val maxRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            coroutineScope.launch { preferencesManager.setRingtoneUri(uri?.toString() ?: "") }
        }
    }
    val lowRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            coroutineScope.launch { preferencesManager.setLowRingtoneUri(uri?.toString() ?: "") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

            // ═══════════════════════════════════════════════════════════════════
            // MAX BATTERY ALARM SECTION
            // ═══════════════════════════════════════════════════════════════════
            AlarmSectionHeader(
                title      = "Max Battery Alarm",
                subtitle   = "Triggers when battery hits the target % while charging",
                icon       = Icons.Default.BatteryChargingFull,
                iconColor  = Color(0xFF4ADE80)
            )

            // Target percentage
            PercentageSliderRow(
                label       = "Target Percentage",
                description = "Alarm fires when charging battery reaches this level",
                value       = maxTarget,
                range       = 10f..100f,
                steps       = 89,
                onValue     = { coroutineScope.launch { preferencesManager.setTargetPercentage(it) } }
            )

            SettingsDivider()

            // Ringtone
            RingtoneRow(
                label      = "Ringtone",
                currentUri = maxRingtone,
                onClick    = {
                    maxRingtoneLauncher.launch(buildRingtoneIntent(maxRingtone))
                }
            )

            SettingsDivider()

            // Alarm volume
            VolumeSliderRow(
                value   = maxVolume,
                onValue = { coroutineScope.launch { preferencesManager.setAlarmVolume(it) } }
            )

            SettingsDivider()

            // Vibration
            VibrationRow(
                checked   = maxVibration,
                onChanged = { coroutineScope.launch { preferencesManager.setVibrationEnabled(it) } }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════════════
            // LOW BATTERY ALARM SECTION
            // ═══════════════════════════════════════════════════════════════════
            AlarmSectionHeader(
                title      = "Low Battery Alarm",
                subtitle   = "Triggers when battery drops below threshold while unplugged",
                icon       = Icons.Default.BatteryAlert,
                iconColor  = Color(0xFFFB923C)
            )

            // Master enable toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Low Battery Alarm", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(
                        "Turn on to activate low battery monitoring",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked       = lowEnabled,
                    onCheckedChange = { coroutineScope.launch { preferencesManager.setLowAlarmEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFFFB923C)
                    )
                )
            }

            SettingsDivider()

            // Target percentage — disabled when low alarm is off
            PercentageSliderRow(
                label       = "Trigger Percentage",
                description = "Alarm fires when unplugged battery drops to this level",
                value       = lowTarget,
                range       = 5f..50f,
                steps       = 44,
                onValue     = { coroutineScope.launch { preferencesManager.setLowTargetPercentage(it) } },
                enabled     = lowEnabled
            )

            SettingsDivider()

            // Ringtone — disabled when low alarm is off
            RingtoneRow(
                label      = "Ringtone",
                currentUri = lowRingtone,
                enabled    = lowEnabled,
                onClick    = {
                    if (lowEnabled) lowRingtoneLauncher.launch(buildRingtoneIntent(lowRingtone))
                }
            )

            SettingsDivider()

            // Alarm volume — disabled when low alarm is off
            VolumeSliderRow(
                value   = lowVolume,
                enabled = lowEnabled,
                onValue = { coroutineScope.launch { preferencesManager.setLowAlarmVolume(it) } }
            )

            SettingsDivider()

            // Vibration — disabled when low alarm is off
            VibrationRow(
                checked   = lowVibration,
                enabled   = lowEnabled,
                onChanged = { coroutineScope.launch { preferencesManager.setLowVibrationEnabled(it) } }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlarmSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = iconColor.copy(alpha = 0.14f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = iconColor)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun PercentageSliderRow(
    label: String,
    description: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Int) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
            Text(
                text = "$value%",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Slider(
            value         = value.toFloat(),
            onValueChange = { if (enabled) onValue(it.toInt()) },
            valueRange    = range,
            steps         = steps,
            enabled       = enabled,
            modifier      = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RingtoneRow(
    label: String,
    currentUri: String?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                text = if (!currentUri.isNullOrEmpty()) "Custom ringtone selected" else "Default ringtone",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        OutlinedButton(
            onClick  = onClick,
            enabled  = enabled,
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text("Change", fontSize = 13.sp)
        }
    }
}

@Composable
private fun VolumeSliderRow(
    value: Int,
    onValue: (Int) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Alarm Volume", fontWeight = FontWeight.Medium, fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Text(
            "Independent of system volume",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                Icons.Default.VolumeDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value         = value.toFloat(),
                onValueChange = { if (enabled) onValue(it.toInt()) },
                valueRange    = 0f..100f,
                steps         = 99,
                enabled       = enabled,
                modifier      = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$value%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun VibrationRow(
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Vibration", fontWeight = FontWeight.Medium, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                "Vibrate when alarm triggers",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = { if (enabled) onChanged(it) },
            enabled         = enabled
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun buildRingtoneIntent(existingUri: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    if (!existingUri.isNullOrEmpty()) {
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingUri))
    }
}
