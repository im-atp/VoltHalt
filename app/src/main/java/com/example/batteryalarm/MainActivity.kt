package com.example.batteryalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.app.StatusBarManager
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.batteryalarm.ui.screens.MainScreen
import com.example.batteryalarm.ui.screens.SettingsScreen
import com.example.batteryalarm.ui.screens.SetupScreen
import com.example.batteryalarm.ui.theme.VoltHaltTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val isSetupCompleted = runBlocking { preferencesManager.setupCompletedFlow.first() }
        if (isSetupCompleted) {
            checkBatteryOptimization()
            if (isGranted) {
                startBatteryService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(applicationContext)

        val isSetupCompleted = runBlocking { preferencesManager.setupCompletedFlow.first() }
        if (isSetupCompleted) {
            checkPermissionsAndStartService()
        }

        setContent {
            VoltHaltTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryAlarmApp(
                        preferencesManager = preferencesManager,
                        startDestination = if (isSetupCompleted) "main" else "setup",
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestBattery = { checkBatteryOptimization() },
                        onRequestTile = { promptAddQuickTile() },
                        onRequestFullScreenIntent = { promptFullScreenIntent() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                checkBatteryOptimization()
                startBatteryService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            checkBatteryOptimization()
            startBatteryService()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
    
    private fun promptAddQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            statusBarManager.requestAddTileService(
                ComponentName(this, AlarmTileService::class.java),
                getString(R.string.app_name),
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile_icon),
                mainExecutor,
                { _ -> /* Handle result if needed */ }
            )
        }
    }

    private fun promptFullScreenIntent() {
        // Android 14+ requires the user to grant USE_FULL_SCREEN_INTENT explicitly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun startBatteryService() {
        lifecycleScope.launch {
            val isEnabled = preferencesManager.alarmEnabledFlow.first()
            if (isEnabled) {
                val serviceIntent = Intent(this@MainActivity, BatteryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }
}

@Composable
fun BatteryAlarmApp(
    preferencesManager: PreferencesManager,
    startDestination: String,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestTile: () -> Unit,
    onRequestFullScreenIntent: () -> Unit = {}
) {
    val navController = rememberNavController()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("setup") {
            SetupScreen(
                onFinish = {
                    coroutineScope.launch {
                        preferencesManager.setSetupCompleted(true)
                        navController.navigate("main") {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                },
                onRequestNotifications = onRequestNotifications,
                onRequestBattery = onRequestBattery,
                onRequestTile = onRequestTile,
                onRequestFullScreenIntent = onRequestFullScreenIntent
            )
        }
        composable("main") {
            MainScreen(
                preferencesManager = preferencesManager,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                preferencesManager = preferencesManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
