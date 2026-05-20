package com.example.batteryalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryalarm.ui.theme.VoltHaltTheme

class AlarmActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    // The alarm type shown on this screen (MAX or LOW).
    private var currentAlarmType: String = BatteryService.ALARM_TYPE_MAX

    // ── Receiver 1: service signals alarm has been stopped ───────────────────
    // Fired by BatteryService.broadcastAlarmStopped() — covers "Stop Alarm"
    // button in the notification and the activity's own stop button.
    private val alarmStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BatteryService.ACTION_ALARM_STOPPED) {
                finish()
            }
        }
    }

    // ── Receiver 2: charger state change ─────────────────────────────────────
    // When showing a MAX BATTERY alarm the subtitle says "unplug your charger".
    // This receiver watches for that event so the screen closes automatically
    // even if the service's isMaxAlarmPlaying flag was already cleared (e.g.
    // the user had previously tapped "Stop Alarm" on the notification, which
    // silences audio but leaves the full-screen activity open).
    private val batteryStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            if (currentAlarmType != BatteryService.ALARM_TYPE_MAX) return

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            if (!isCharging) {
                // Charger was unplugged — dismiss the max-charge screen.
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Turn screen on and show over lock screen ──────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // Keep screen on while alarm is showing
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // ── Acquire wake lock to keep CPU awake ───────────────────────────────
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoltHalt::AlarmWakeLock"
        ).apply { acquire(10 * 60 * 1000L) /* max 10 min */ }

        // ── Register for "alarm stopped" broadcast ────────────────────────────
        val alarmFilter = IntentFilter(BatteryService.ACTION_ALARM_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmStoppedReceiver, alarmFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alarmStoppedReceiver, alarmFilter)
        }

        // ── Register for battery state changes ────────────────────────────────
        // ACTION_BATTERY_CHANGED is a sticky broadcast, so registering immediately
        // delivers the current state — no need for a separate initial check.
        registerReceiver(batteryStateReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val alarmType = intent?.getStringExtra(BatteryService.EXTRA_ALARM_TYPE)
            ?: BatteryService.ALARM_TYPE_MAX

        showAlarmUi(alarmType)
    }

    // Called when activity is already running and a new alarm intent arrives
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val alarmType = intent?.getStringExtra(BatteryService.EXTRA_ALARM_TYPE)
            ?: BatteryService.ALARM_TYPE_MAX
        showAlarmUi(alarmType)
    }

    private fun showAlarmUi(alarmType: String) {
        currentAlarmType = alarmType
        setContent {
            VoltHaltTheme(darkTheme = true) {
                AlarmScreen(
                    alarmType = alarmType,
                    onStopAlarm = {
                        stopAlarmInService()
                        finish()
                    }
                )
            }
        }
    }

    private fun stopAlarmInService() {
        val stopIntent = Intent(this, BatteryService::class.java).apply {
            action = BatteryService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
    }

    // Prevent back button from dismissing the alarm screen
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — user must press the Stop button or unplug the charger
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(alarmStoppedReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(batteryStateReceiver) } catch (_: Exception) {}
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}

// ── Full-screen Compose UI ───────────────────────────────────────────────────

@Composable
private fun AlarmScreen(
    alarmType: String,
    onStopAlarm: () -> Unit
) {
    val isMaxBattery = alarmType == BatteryService.ALARM_TYPE_MAX

    val backgroundGradient = if (isMaxBattery) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF1A3A1A), Color(0xFF0D1F0D), Color(0xFF050E05))
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFF3A1A1A), Color(0xFF1F0D0D), Color(0xFF0E0505))
        )
    }

    val accentColor = if (isMaxBattery) Color(0xFF4ADE80) else Color(0xFFFB923C)
    val title       = if (isMaxBattery) "Max Battery Reached!" else "Low Battery Warning!"
    val subtitle    = if (isMaxBattery)
        "Your battery has hit the target level.\nUnplug your charger or press Stop to dismiss."
    else
        "Your battery is critically low.\nPlease plug in your charger."

    // ── Pulsing animation ──────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue  = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue  = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Pulsing icon
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(scale)
                        .background(
                            color = accentColor.copy(alpha = ringAlpha),
                            shape = CircleShape
                        )
                )
                // Inner circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMaxBattery) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint   = accentColor,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(scale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text       = title,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text      = subtitle,
                fontSize  = 15.sp,
                color     = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Stop button
            Button(
                onClick = onStopAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor   = Color(0xFF050505)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text       = "STOP ALARM",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
