package com.example.batteryalarm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        // ── Max Battery Alarm ──────────────────────────────────────────────────
        private val ALARM_ENABLED          = booleanPreferencesKey("alarm_enabled")
        val TARGET_PERCENTAGE              = intPreferencesKey("target_percentage")
        val VIBRATION_ENABLED              = booleanPreferencesKey("vibration_enabled")
        private val RINGTONE_URI           = stringPreferencesKey("ringtone_uri")
        val ALARM_VOLUME                   = intPreferencesKey("alarm_volume")

        // ── Low Battery Alarm ──────────────────────────────────────────────────
        private val LOW_ALARM_ENABLED      = booleanPreferencesKey("low_alarm_enabled")
        val LOW_TARGET_PERCENTAGE          = intPreferencesKey("low_target_percentage")
        val LOW_VIBRATION_ENABLED          = booleanPreferencesKey("low_vibration_enabled")
        private val LOW_RINGTONE_URI       = stringPreferencesKey("low_ringtone_uri")
        val LOW_ALARM_VOLUME               = intPreferencesKey("low_alarm_volume")

        // ── App setup ──────────────────────────────────────────────────────────
        private val SETUP_COMPLETED        = booleanPreferencesKey("setup_completed")
    }

    // ── Max Battery Alarm flows ──────────────────────────────────────────────

    val alarmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ALARM_ENABLED] ?: false
    }

    val targetPercentageFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TARGET_PERCENTAGE] ?: 80
    }

    val vibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VIBRATION_ENABLED] ?: true
    }

    val ringtoneUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[RINGTONE_URI]
    }

    val alarmVolumeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ALARM_VOLUME] ?: 80
    }

    // ── Low Battery Alarm flows ──────────────────────────────────────────────

    val lowAlarmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOW_ALARM_ENABLED] ?: false
    }

    val lowTargetPercentageFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LOW_TARGET_PERCENTAGE] ?: 20
    }

    val lowVibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOW_VIBRATION_ENABLED] ?: true
    }

    val lowRingtoneUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LOW_RINGTONE_URI]
    }

    val lowAlarmVolumeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LOW_ALARM_VOLUME] ?: 80
    }

    // ── App setup flow ───────────────────────────────────────────────────────

    val setupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETED] ?: false
    }

    // ── Max Battery Alarm setters ────────────────────────────────────────────

    suspend fun setAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ALARM_ENABLED] = enabled }
    }

    suspend fun setTargetPercentage(percentage: Int) {
        context.dataStore.edit { it[TARGET_PERCENTAGE] = percentage }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATION_ENABLED] = enabled }
    }

    suspend fun setRingtoneUri(uriString: String) {
        context.dataStore.edit { it[RINGTONE_URI] = uriString }
    }

    suspend fun setAlarmVolume(volume: Int) {
        context.dataStore.edit { it[ALARM_VOLUME] = volume.coerceIn(0, 100) }
    }

    // ── Low Battery Alarm setters ────────────────────────────────────────────

    suspend fun setLowAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOW_ALARM_ENABLED] = enabled }
    }

    suspend fun setLowTargetPercentage(percentage: Int) {
        context.dataStore.edit { it[LOW_TARGET_PERCENTAGE] = percentage }
    }

    suspend fun setLowVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOW_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setLowRingtoneUri(uriString: String) {
        context.dataStore.edit { it[LOW_RINGTONE_URI] = uriString }
    }

    suspend fun setLowAlarmVolume(volume: Int) {
        context.dataStore.edit { it[LOW_ALARM_VOLUME] = volume.coerceIn(0, 100) }
    }

    // ── Setup setter ─────────────────────────────────────────────────────────

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[SETUP_COMPLETED] = completed }
    }
}
