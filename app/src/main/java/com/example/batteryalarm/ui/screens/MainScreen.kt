package com.example.batteryalarm.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.batteryalarm.BatteryService
import com.example.batteryalarm.PreferencesManager
import com.example.batteryalarm.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Helper: observe live battery level + charging state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun rememberBatteryState(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val lvl   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                level = (lvl * 100) / scale
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        // ACTION_BATTERY_CHANGED is sticky — the first registerReceiver call
        // immediately delivers the current value even without a broadcast.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return Pair(level, isCharging)
}

// ─────────────────────────────────────────────────────────────────────────────
// Share APK helper
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun shareApk(context: Context) = withContext(Dispatchers.IO) {
    try {
        // The installed APK path — works for monolithic (non-split) APKs.
        val sourceApk = File(context.applicationInfo.sourceDir)

        // Copy to a FileProvider-accessible cache directory so we can create
        // a content:// URI that other apps are allowed to read.
        val destDir = File(context.externalCacheDir ?: context.cacheDir, "share").apply { mkdirs() }
        val destApk = File(destDir, "${context.getString(R.string.app_name)}.apk")

        sourceApk.copyTo(destApk, overwrite = true)

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destApk
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "Share ${context.getString(R.string.app_name)} APK")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MainScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferencesManager: PreferencesManager,
    onNavigateToSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context        = LocalContext.current

    // Alarm state
    val maxEnabled  by preferencesManager.alarmEnabledFlow.collectAsState(initial = false)
    val maxTarget   by preferencesManager.targetPercentageFlow.collectAsState(initial = 80)
    val lowEnabled  by preferencesManager.lowAlarmEnabledFlow.collectAsState(initial = false)
    val lowTarget   by preferencesManager.lowTargetPercentageFlow.collectAsState(initial = 20)

    // Live battery
    val (batteryLevel, isCharging) = rememberBatteryState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    // ── Share APK button ──────────────────────────────────────
                    IconButton(
                        onClick = {
                            coroutineScope.launch { shareApk(context) }
                        }
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share APK",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // ── Settings button ───────────────────────────────────────
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Live Battery Status Card ──────────────────────────────────────
            BatteryStatusCard(level = batteryLevel, isCharging = isCharging)

            // ── Section label ─────────────────────────────────────────────────
            Text(
                text = "Alarms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // ── Max Battery Alarm Card ────────────────────────────────────────
            AlarmCard(
                title          = "Max Battery Alarm",
                description    = "Alert when battery is full while charging",
                icon           = Icons.Default.BatteryChargingFull,
                thresholdLabel = "${maxTarget}% threshold",
                isEnabled      = maxEnabled,
                accentColor    = Color(0xFF4ADE80),
                onToggle       = { newState ->
                    coroutineScope.launch {
                        preferencesManager.setAlarmEnabled(newState)
                        if (newState) {
                            val intent = Intent(context, BatteryService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                context.startForegroundService(intent)
                            else
                                context.startService(intent)
                        }
                    }
                },
                onClick = onNavigateToSettings
            )

            // ── Low Battery Alarm Card ────────────────────────────────────────
            AlarmCard(
                title          = "Low Battery Alarm",
                description    = "Alert when battery drops below threshold",
                icon           = Icons.Default.BatteryAlert,
                thresholdLabel = "${lowTarget}% threshold",
                isEnabled      = lowEnabled,
                accentColor    = Color(0xFFFB923C),
                onToggle       = { newState ->
                    coroutineScope.launch {
                        preferencesManager.setLowAlarmEnabled(newState)
                        if (newState) {
                            val intent = Intent(context, BatteryService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                context.startForegroundService(intent)
                            else
                                context.startService(intent)
                        }
                    }
                },
                onClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Settings shortcut ─────────────────────────────────────────────
            OutlinedButton(
                onClick  = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Configure Alarm Settings", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Battery Status Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BatteryStatusCard(level: Int, isCharging: Boolean) {
    val batteryColor = when {
        level >= 70 -> Color(0xFF4ADE80)
        level >= 30 -> Color(0xFFFBBF24)
        else        -> Color(0xFFF87171)
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            batteryColor.copy(alpha = 0.15f),
            batteryColor.copy(alpha = 0.04f)
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Battery percentage ring / indicator
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(batteryColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = batteryColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.width(20.dp))

                Column {
                    Text(
                        text = "$level%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                    Text(
                        text = if (isCharging) "Charging" else "Not Charging",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Battery bar
                BatteryBar(
                    level = level,
                    color = batteryColor
                )
            }
        }
    }
}

@Composable
private fun BatteryBar(level: Int, color: Color) {
    val segments = 5
    val filledSegments = ((level / 100f) * segments).toInt().coerceIn(0, segments)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(segments) { index ->
            val filled = (segments - 1 - index) < filledSegments
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (filled) color else color.copy(alpha = 0.15f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alarm Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlarmCard(
    title: String,
    description: String,
    icon: ImageVector,
    thresholdLabel: String,
    isEnabled: Boolean,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isEnabled)
            accentColor.copy(alpha = 0.10f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        animationSpec = tween(300),
        label = "cardBg"
    )
    val animatedAccent by animateColorAsState(
        targetValue = if (isEnabled) accentColor else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "accent"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(animatedBg)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(animatedAccent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedAccent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))
                // Threshold pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(animatedAccent.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = thresholdLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = animatedAccent
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = accentColor,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
