package com.example.batteryalarm

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for VoltHalt.
 *
 * Design decisions:
 *  - The tile acts as the master toggle for the Max Battery Alarm.
 *  - Tile state reflects both alarm flows combined (active if either alarm is enabled),
 *    so the tile accurately shows when ANY monitoring is happening.
 *  - On tap-to-enable:  sets maxAlarm = true  + starts BatteryService.
 *  - On tap-to-disable: sends ACTION_STOP_MAX_ALARM_FROM_TILE, which stops the service
 *    if low battery is also disabled, or just updates the notification if it's still needed.
 *  - Tile UI is updated IMMEDIATELY on click (no DataStore round-trip wait) so the tile
 *    feels instant.  The flow collector keeps it in sync if the state changes elsewhere
 *    (e.g. from the app UI or a "Stop Monitoring" notification button).
 */
@RequiresApi(Build.VERSION_CODES.N)
class AlarmTileService : TileService() {

    // Use Main.immediate so updateTile() can be called safely from the collector
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesManager: PreferencesManager
    private var listeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
    }

    // ── Tile visibility window ───────────────────────────────────────────────
    override fun onStartListening() {
        super.onStartListening()
        // Combine both alarm flows: tile shows ACTIVE whenever either alarm is enabled.
        // This keeps the tile in sync if the user changes settings inside the app.
        listeningJob = serviceScope.launch {
            combine(
                preferencesManager.alarmEnabledFlow,
                preferencesManager.lowAlarmEnabledFlow
            ) { maxEnabled, lowEnabled -> maxEnabled || lowEnabled }
            .collect { anyEnabled -> applyTileState(anyEnabled) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    // ── User tap ─────────────────────────────────────────────────────────────
    override fun onClick() {
        super.onClick()

        val isCurrentlyActive = qsTile?.state == Tile.STATE_ACTIVE
        val newActive = !isCurrentlyActive

        // Step 1: Update the tile UI IMMEDIATELY — no waiting for DataStore.
        //         This makes the tile feel instant to the user.
        applyTileState(newActive)

        if (newActive) {
            // ── Enable max-battery alarm + ensure service is running ──────────
            serviceScope.launch {
                preferencesManager.setAlarmEnabled(true)
            }
            try {
                val intent = Intent(applicationContext, BatteryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            } catch (_: Exception) {
                // Background-start restriction — silently ignore
            }
        } else {
            // ── Disable max-battery alarm + stop service if appropriate ───────
            // Save the preference, then tell the service to re-evaluate.
            // ACTION_STOP_MAX_ALARM_FROM_TILE lets the service decide whether to
            // keep running (low battery alarm still on) or shut down completely.
            serviceScope.launch {
                preferencesManager.setAlarmEnabled(false)
            }
            try {
                val stopIntent = Intent(applicationContext, BatteryService::class.java).apply {
                    action = BatteryService.ACTION_STOP_MAX_ALARM_FROM_TILE
                }
                applicationContext.startService(stopIntent)
            } catch (_: Exception) {}
        }
    }

    // ── Tile UI helper ───────────────────────────────────────────────────────
    private fun applyTileState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Alarm On" else "Alarm Off"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Monitoring" else "Disabled"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
