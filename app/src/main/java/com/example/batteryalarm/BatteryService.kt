package com.example.batteryalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BatteryService : Service() {

    companion object {
        const val ACTION_STOP_SERVICE             = "STOP_SERVICE"
        const val ACTION_STOP_ALARM               = "STOP_ALARM"
        const val ACTION_STOP_MAX_ALARM_FROM_TILE = "STOP_MAX_ALARM_FROM_TILE"
        const val ACTION_ALARM_STOPPED            = "com.example.batteryalarm.ALARM_STOPPED"
        const val EXTRA_ALARM_TYPE                = "alarm_type"
        const val ALARM_TYPE_MAX                  = "max_battery"
        const val ALARM_TYPE_LOW                  = "low_battery"

        private const val SERVICE_CHANNEL_ID = "BatteryServiceChannel"
        private const val SERVICE_NOTIF_ID   = 1
        private const val ALARM_CHANNEL_ID   = "BatteryAlarmChannel"
        private const val ALARM_NOTIF_ID     = 2
    }

    // IO dispatcher for alarm playback; Main.immediate for all other fast work.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var alarmPlayer: AlarmPlayer

    // ── Alarm playback state ──────────────────────────────────────────────────
    // Accessed from the main thread only (batteryReceiver + onStartCommand both
    // run on the main thread), so no synchronisation needed.
    private var isMaxAlarmPlaying = false
    private var isLowAlarmPlaying = false

    // ── Preferences cached in memory ─────────────────────────────────────────
    // Each field is updated by a lightweight collector that starts in onCreate.
    // checkBatteryLevel() then reads plain variables — zero IO, zero allocation,
    // zero coroutine overhead — on every ACTION_BATTERY_CHANGED event.
    private var maxEnabled = false
    private var maxTarget  = 80
    private var lowEnabled = false
    private var lowTarget  = 20

    // ── Battery broadcast receiver ────────────────────────────────────────────
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level == -1 || scale == -1) return

            val pct        = (level * 100) / scale
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            // Pure in-memory check — no coroutine, no DataStore access.
            checkBatteryLevel(pct, isCharging)
        }
    }

    // ── onCreate ──────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        alarmPlayer        = AlarmPlayer(applicationContext)
        createNotificationChannels()

        // Start four lightweight collectors that keep the in-memory preference
        // cache in sync whenever the user changes a setting. Each collector is
        // a single suspended collect() — negligible CPU, no disk reads after
        // the first emission (DataStore itself caches the file in memory).
        serviceScope.launch {
            preferencesManager.alarmEnabledFlow.collect { maxEnabled = it }
        }
        serviceScope.launch {
            preferencesManager.targetPercentageFlow.collect { maxTarget = it }
        }
        serviceScope.launch {
            preferencesManager.lowAlarmEnabledFlow.collect { lowEnabled = it }
        }
        serviceScope.launch {
            preferencesManager.lowTargetPercentageFlow.collect { lowTarget = it }
        }

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    // ── onStartCommand ────────────────────────────────────────────────────────
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            // Hard stop: disable both alarms and kill the service.
            ACTION_STOP_SERVICE -> {
                serviceScope.launch {
                    preferencesManager.setAlarmEnabled(false)
                    preferencesManager.setLowAlarmEnabled(false)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_NOTIF_ID)
                alarmPlayer.stop()
                broadcastAlarmStopped()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            // Quick-tile disabled the max-battery alarm.
            // Smart: stays alive if the low-battery alarm is still enabled.
            ACTION_STOP_MAX_ALARM_FROM_TILE -> {
                if (isMaxAlarmPlaying) {
                    isMaxAlarmPlaying = false
                    if (!isLowAlarmPlaying) {
                        alarmPlayer.stop()
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(ALARM_NOTIF_ID)
                    }
                    broadcastAlarmStopped()
                }
                // Use the in-memory cache — no DataStore round-trip needed.
                if (!lowEnabled) {
                    stopForegroundCompat()
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            // Silence audio but keep monitoring.
            ACTION_STOP_ALARM -> {
                stopAllAlarms()
                return START_NOT_STICKY
            }
        }

        // Normal start — begin foreground monitoring.
        startForeground(SERVICE_NOTIF_ID, createServiceNotification("Monitoring battery level…"))
        return START_STICKY
    }

    // ── Battery level check ───────────────────────────────────────────────────
    // Called from the battery broadcast receiver (main thread).
    // Reads only in-memory fields — no coroutine, no IO, no allocation.
    private fun checkBatteryLevel(currentLevel: Int, isCharging: Boolean) {
        // Max Battery Alarm — fires when charging and level >= target
        if (maxEnabled && isCharging && currentLevel >= maxTarget) {
            if (!isMaxAlarmPlaying) {
                isMaxAlarmPlaying = true
                // Launch on IO only to read ringtone/volume prefs + prepare MediaPlayer.
                // This branch executes at most once per alarm event.
                serviceScope.launch(Dispatchers.IO) { startAlarm(ALARM_TYPE_MAX) }
            }
        } else if (isMaxAlarmPlaying) {
            stopMaxAlarm()
        }

        // Low Battery Alarm — fires when NOT charging and level <= target
        if (lowEnabled && !isCharging && currentLevel <= lowTarget) {
            if (!isLowAlarmPlaying) {
                isLowAlarmPlaying = true
                serviceScope.launch(Dispatchers.IO) { startAlarm(ALARM_TYPE_LOW) }
            }
        } else if (isLowAlarmPlaying) {
            stopLowAlarm()
        }
    }

    // ── Alarm start (runs on IO — DataStore reads + MediaPlayer.prepare) ──────
    private suspend fun startAlarm(type: String) {
        val ringtoneUri: String?
        val vibration: Boolean
        val volume: Int

        if (type == ALARM_TYPE_MAX) {
            ringtoneUri = preferencesManager.ringtoneUriFlow.first()
            vibration   = preferencesManager.vibrationEnabledFlow.first()
            volume      = preferencesManager.alarmVolumeFlow.first()
        } else {
            ringtoneUri = preferencesManager.lowRingtoneUriFlow.first()
            vibration   = preferencesManager.lowVibrationEnabledFlow.first()
            volume      = preferencesManager.lowAlarmVolumeFlow.first()
        }

        alarmPlayer.play(ringtoneUri, vibration, volume)
        showAlarmNotification(type)
    }

    // ── Full-screen alarm notification ────────────────────────────────────────
    private fun showAlarmNotification(type: String) {
        val alarmActivityIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 3, alarmActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAlarmPending = PendingIntent.getService(
            this, 4,
            Intent(this, BatteryService::class.java).apply { action = ACTION_STOP_ALARM },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val titleText = if (type == ALARM_TYPE_MAX) "⚡ Max Battery Reached!" else "🪫 Low Battery Warning!"
        val bodyText  = if (type == ALARM_TYPE_MAX)
            "Battery has hit your target. Unplug now."
        else
            "Battery is critically low. Please charge."

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Stop Alarm", stopAlarmPending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALARM_NOTIF_ID, notification)
    }

    // ── Alarm stop helpers ────────────────────────────────────────────────────
    private fun stopMaxAlarm() {
        if (!isMaxAlarmPlaying) return
        isMaxAlarmPlaying = false
        if (!isLowAlarmPlaying) {
            alarmPlayer.stop()
            cancelAlarmNotification()
        }
        broadcastAlarmStopped()
    }

    private fun stopLowAlarm() {
        if (!isLowAlarmPlaying) return
        isLowAlarmPlaying = false
        if (!isMaxAlarmPlaying) {
            alarmPlayer.stop()
            cancelAlarmNotification()
        }
        broadcastAlarmStopped()
    }

    fun stopAllAlarms() {
        isMaxAlarmPlaying = false
        isLowAlarmPlaying = false
        alarmPlayer.stop()
        cancelAlarmNotification()
        broadcastAlarmStopped()
    }

    private fun cancelAlarmNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALARM_NOTIF_ID)
        nm.notify(SERVICE_NOTIF_ID, createServiceNotification("Monitoring battery level…"))
    }

    private fun broadcastAlarmStopped() {
        sendBroadcast(Intent(ACTION_ALARM_STOPPED))
    }

    // ── Service (foreground) notification ─────────────────────────────────────
    private fun createServiceNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopServicePending = PendingIntent.getService(
            this, 2,
            Intent(this, BatteryService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("VoltHalt")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(openIntent)
            .addAction(0, "Stop Monitoring", stopServicePending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Notification channels ─────────────────────────────────────────────────
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID, "Battery Monitoring", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while VoltHalt is monitoring battery."
            }
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID, "Battery Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description          = "Shown when a battery alarm fires."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alarmChannel)
        }
    }

    // ── Compat helper ─────────────────────────────────────────────────────────
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        alarmPlayer.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
