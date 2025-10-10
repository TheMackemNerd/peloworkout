package com.digitalelysium.peloworkout

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.digitalelysium.peloworkout.ui.theme.PeloworkoutTheme
import com.digitalelysium.peloworkout.ui.theme.ThemeOption
import com.digitalelysium.peloworkout.data.themeFlow
import com.digitalelysium.peloworkout.data.setTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.digitalelysium.peloworkout.ble.BleConnectionManager
import com.digitalelysium.peloworkout.ble.BleScanner
import com.digitalelysium.peloworkout.strava.StravaClient
import com.digitalelysium.peloworkout.ui.screen.WorkoutScreen

class MainActivity : ComponentActivity() {

    lateinit var strava: StravaClient
        private set

    private lateinit var ble: BleConnectionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val btMgr = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = BleScanner(btMgr.adapter.bluetoothLeScanner)
        ble = BleConnectionManager(this, scanner)
        strava = StravaClient(this)
        intent?.data?.let { strava.handleRedirect(it) }

        setContent {
            // read saved theme option (System/Light/Dark), default to System
            val themeOpt = themeFlow(this).collectAsState(initial = ThemeOption.System).value

            PeloworkoutTheme(option = themeOpt) {
                WorkoutScreen(
                    requestPerms = { requestPerms() },
                    scan = { cb -> if (!ble.startScan(cb)) requestPerms() },
                    stopScan = { ble.stopScan() },
                    connect = { device, onOk -> if (!ble.connect(device, onOk)) requestPerms() },
                    subscribe = { cb -> ble.subscribeIndoorBikeData(cb) },
                    resistancePercent = { lvl -> ble.resistancePercent(lvl) },
                    currentThemeOption = themeOpt,
                    // new: let UI change the theme, persist via DataStore
                    onChangeTheme = { opt ->
                        lifecycleScope.launch { setTheme(this@MainActivity, opt) }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { strava.handleRedirect(it) }
    }
}
