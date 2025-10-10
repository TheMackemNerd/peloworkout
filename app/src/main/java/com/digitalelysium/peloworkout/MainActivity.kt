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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                WorkoutScreen(
                    requestPerms = { requestPerms() },
                    scan = { cb ->
                        val ok = ble.startScan(cb)
                        if (!ok) requestPerms()
                    },
                    stopScan = { ble.stopScan() },
                    connect = { device, onOk ->
                        val ok = ble.connect(device, onOk)
                        if (!ok) requestPerms()
                    },
                    subscribe = { cb -> ble.subscribeIndoorBikeData(cb) },
                    resistancePercent = { lvl -> ble.resistancePercent(lvl) }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { strava.handleRedirect(it) }
    }
}
