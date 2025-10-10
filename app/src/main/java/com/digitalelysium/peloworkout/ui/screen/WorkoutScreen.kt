package com.digitalelysium.peloworkout.ui.screen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.digitalelysium.peloworkout.MainActivity
import com.digitalelysium.peloworkout.strava.buildTcx
import com.digitalelysium.peloworkout.ui.util.*
import com.digitalelysium.peloworkout.ui.screen.components.*
import com.digitalelysium.peloworkout.ui.theme.ThemeOption
import com.digitalelysium.peloworkout.ui.workout.WorkoutSummary
import com.digitalelysium.peloworkout.ui.workout.WorkoutViewModel

class SimpleVmFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutScreen(
    requestPerms: () -> Unit,
    scan: ((onDevice: (ScanResult) -> Unit) -> Unit),
    stopScan: () -> Unit,
    connect: (BluetoothDevice, (() -> Unit)?) -> Unit,
    subscribe: (((ByteArray) -> Unit)?) -> Unit,
    resistancePercent: (Double) -> Double?,
    currentThemeOption: ThemeOption,
    onChangeTheme: (ThemeOption) -> Unit,
    vm: WorkoutViewModel = viewModel(factory = SimpleVmFactory { WorkoutViewModel(resistancePercent) })
) {
    val activity = LocalContext.current as MainActivity
    val ctx = LocalContext.current
    val view = LocalView.current

    val ui by vm.ui.collectAsState()

    var showSummary by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<WorkoutSummary?>(null) }

    if (showSummary && summary != null) {
        SummaryScreen(
            data = summary!!,
            onClose = { showSummary = false },
            onUpload = { title ->
                val s = summary!!
                activity.strava.ensureToken(
                    onReady = { token ->
                        val tcx = buildTcx(
                            startEpochMs = s.startEpochMs,
                            power = s.powerHistory,
                            speedKph = s.speedHistory,
                            cadenceRpm = s.cadenceHistory
                        )
                        activity.strava.uploadTcx(token, tcx, title)
                        android.widget.Toast
                            .makeText(ctx, "Uploading to Strava…", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    },
                    onNeedLogin = {
                        android.widget.Toast
                            .makeText(ctx, "Connect Strava first", android.widget.Toast.LENGTH_SHORT)
                            .show()
                        activity.strava.startAuth()
                    }
                )
            }
        )
        return
    }


    // keep screen on while running
    DisposableEffect(ui.running) {
        view.keepScreenOn = ui.running
        onDispose { view.keepScreenOn = false }
    }

    // subscription lifecycle
    LaunchedEffect(ui.connectedDeviceName) {
        if (ui.connectedDeviceName != null) subscribe { bytes -> vm.onBike(bytes) }
        else subscribe(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ui.connectedDeviceName == null) "Select Bike" else "Workout") },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    var themeMenuOpen by remember { mutableStateOf(false) }

                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        // Strava connect status
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (activity.strava.hasToken())
                                        "Connected to Strava"
                                    else
                                        "Connect to Strava"
                                )
                            },
                            onClick = {
                                if (!activity.strava.hasToken()) {
                                    activity.strava.startAuth()
                                }
                                menuOpen = false
                            }
                        )

                        // Theme submenu
                        DropdownMenuItem(
                            text = { Text("Theme") },
                            onClick = {
                                // open nested theme menu
                                themeMenuOpen = true
                            }
                        )
                    }

                    // Nested theme submenu
                    DropdownMenu(
                        expanded = themeMenuOpen,
                        onDismissRequest = { themeMenuOpen = false }
                    ) {
                        val options = listOf(
                            ThemeOption.System to "System default",
                            ThemeOption.Light to "Light",
                            ThemeOption.Dark to "Dark"
                        )
                        options.forEach { (opt, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row {
                                        Text(label)
                                        if (currentThemeOption == opt) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onChangeTheme(opt)
                                    themeMenuOpen = false
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    !ui.running && !ui.paused -> {
                        Button(
                            onClick = { vm.start() },
                            enabled = ui.connectedDeviceName != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start") }
                    }
                    ui.running && !ui.paused -> {
                        Button(onClick = { vm.pause() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                        Button(
                            onClick = {
                                vm.stop()
                                val uiSnap = ui
                                val avgPower = if (uiSnap.elapsed > 0) (uiSnap.totalKJ * 1000.0 / uiSnap.elapsed) else 0.0
                                val kcal = (uiSnap.totalKJ / 0.24) / 4.184

                                summary = WorkoutSummary(
                                    elapsed = uiSnap.elapsed,
                                    distanceKm = uiSnap.distanceKm,
                                    avgPowerW = avgPower.toInt(),
                                    topPowerW = uiSnap.topPower.toInt(),
                                    topSpeedKph = uiSnap.topSpeed,
                                    totalKJ = uiSnap.totalKJ,
                                    estKcal = kcal.toInt(),
                                    startEpochMs = uiSnap.sessionStartEpochMs,
                                    powerHistory = uiSnap.powerHistory,
                                    speedHistory = uiSnap.speedHistory,
                                    cadenceHistory = uiSnap.cadenceHistory
                                )
                                showSummary = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop")
                        }
                    }
                    ui.running && ui.paused -> {
                        Button(onClick = { vm.resume() }, modifier = Modifier.weight(1f)) { Text("Start") }
                        Button(
                            onClick = {
                                vm.stop()
                                val uiSnap = ui
                                val avgPower = if (uiSnap.elapsed > 0) (uiSnap.totalKJ * 1000.0 / uiSnap.elapsed) else 0.0
                                val kcal = (uiSnap.totalKJ / 0.24) / 4.184

                                summary = WorkoutSummary(
                                    elapsed = uiSnap.elapsed,
                                    distanceKm = uiSnap.distanceKm,
                                    avgPowerW = avgPower.toInt(),
                                    topPowerW = uiSnap.topPower.toInt(),
                                    topSpeedKph = uiSnap.topSpeed,
                                    totalKJ = uiSnap.totalKJ,
                                    estKcal = kcal.toInt(),
                                    startEpochMs = uiSnap.sessionStartEpochMs,
                                    powerHistory = uiSnap.powerHistory,
                                    speedHistory = uiSnap.speedHistory,
                                    cadenceHistory = uiSnap.cadenceHistory
                                )
                                showSummary = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).fillMaxSize()) {
            Text(formatElapsed(ui.elapsed), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            var devices by remember { mutableStateOf(listOf<ScanResult>()) }
            var scanning by remember { mutableStateOf(false) }

            if (ui.connectedDeviceName == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        devices = emptyList()
                        requestPerms()
                        scanning = true
                        scan { res ->
                            if (devices.none { it.device.address == res.device.address }) {
                                devices = devices + res
                            }
                        }
                    }, enabled = !scanning) {
                        Text("Scan for Bike")
                        if (scanning) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    OutlinedButton(onClick = { stopScan(); scanning = false }) { Text("Stop scan") }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(items = devices, key = { it.device.address }) { r ->
                        ListItem(
                            headlineContent = { Text(r.device.name ?: r.device.address) },
                            supportingContent = { Text(r.device.address) },
                            trailingContent = {
                                TextButton(onClick = {
                                    stopScan(); scanning = false
                                    connect(r.device) {
                                        vm.setConnectedDeviceName(r.device.name ?: r.device.address)
                                    }
                                }) { Text("Connect") }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                NineTileGrid(
                    cadence = ui.cadence,
                    power = ui.power,
                    resistancePct = ui.resistancePct,
                    resistanceRaw = ui.resistanceRaw,
                    distance = ui.distanceKm,
                    speed = ui.speed,
                    totalKJ = ui.totalKJ,
                    peakPower = ui.topPower,
                    peakSpeed = ui.topSpeed,
                    calories = (ui.totalKJ / 0.24) / 4.184
                )
                Spacer(Modifier.height(20.dp))
                PowerGraph(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    points = ui.powerHistory,
                    maxPower = ui.topPower,
                    elapsed = ui.elapsed
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
