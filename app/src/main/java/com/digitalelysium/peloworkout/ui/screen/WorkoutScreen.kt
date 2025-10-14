package com.digitalelysium.peloworkout.ui.screen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.digitalelysium.peloworkout.ble.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import com.digitalelysium.peloworkout.data.Gender
import com.digitalelysium.peloworkout.data.UserPrefs
import com.digitalelysium.peloworkout.data.UserProfile
import kotlinx.coroutines.launch

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
    connectHr: (BluetoothDevice, (() -> Unit)?) -> Unit,
    subscribeHr: (((Int) -> Unit)?) -> Unit,
    vm: WorkoutViewModel = viewModel(factory = SimpleVmFactory { WorkoutViewModel(resistancePercent) })
) {
    val activity = LocalContext.current as MainActivity
    val ctx = LocalContext.current
    val view = LocalView.current

    val ui by vm.ui.collectAsState()

    val profile by remember(ctx) {
        UserPrefs.profileFlow(ctx)
    }.collectAsState(initial = UserProfile(null, null, Gender.Unspecified))

    var showProfile by remember { mutableStateOf(false) }

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
                            cadenceRpm = s.cadenceHistory,
                            heartBpm = s.heartHistory
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

                        DropdownMenuItem(
                            text = { Text("User profile") },
                            onClick = { showProfile = true; menuOpen = false }
                        )

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
                                val kcal = CalorieCalculation(uiSnap.totalKJ, uiSnap.heartHistory, profile)

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
                                    cadenceHistory = uiSnap.cadenceHistory,
                                    heartHistory = uiSnap.heartHistory
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
                                val kcal = CalorieCalculation(uiSnap.totalKJ, uiSnap.heartHistory, profile)

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
                                    cadenceHistory = uiSnap.cadenceHistory,
                                    heartHistory = uiSnap.heartHistory
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
// Timer + Heart Rate row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatElapsed(ui.elapsed), style = MaterialTheme.typography.headlineMedium)

                val hr = ui.heartRate
                val pulse = rememberInfiniteTransition()
                val scale by pulse.animateFloat(
                    initialValue = 1f, targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Heart",
                        modifier = Modifier.scale(if (hr != null) scale else 1f),
                        tint = if (hr != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = hr?.toString() ?: "--", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (ui.connectedDeviceName == null) {
                var devices by remember { mutableStateOf(listOf<ScanResult>()) }
                var scanning by remember { mutableStateOf(false) }
                val selected = remember { mutableStateListOf<String>() } // MACs

                // top row: Find Devices / Stop
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            devices = emptyList()
                            selected.clear()
                            requestPerms()
                            scanning = true
                            scan { res ->
                                val mac = res.device.address
                                // only keep devices we care about
                                val kind = classifyDeviceKind(res)
                                if (kind != DeviceKind.Unknown && devices.none { it.device.address == mac }) {
                                    devices = devices + res
                                }
                            }
                        },
                        enabled = !scanning
                    ) {
                        Text("Find Devices")
                        if (scanning) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    OutlinedButton(onClick = { stopScan(); scanning = false }) { Text("Stop") }
                }

                Spacer(Modifier.height(12.dp))

                if (scanning) {
                    // Info box
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Pick one fitness machine and optionally one heart rate monitor, then tap Connect to Devices.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // devices list with checkboxes
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(items = devices, key = { it.device.address }) { r ->
                        val kind = classifyDeviceKind(r)
                        if (kind == DeviceKind.Unknown) return@items
                        val mac = r.device.address
                        val name = r.device.name ?: mac
                        val typeLabel = if (kind == DeviceKind.Bike) "Bike" else "Heart Rate"
                        val checked = mac in selected

                        ListItem(
                            leadingContent = {
                                if (kind == DeviceKind.Bike)
                                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null)
                                else
                                    Icon(Icons.Filled.Favorite, contentDescription = null)
                            },
                            headlineContent = { Text("$name ($typeLabel)") },
                            supportingContent = { Text(mac) },
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        if (on) {
                                            // if ticking a Bike and another Bike already ticked, untick previous
                                            if (kind == DeviceKind.Bike) {
                                                val firstBikeMac = devices.firstOrNull {
                                                    classifyDeviceKind(it) == DeviceKind.Bike && it.device.address in selected
                                                }?.device?.address
                                                if (firstBikeMac != null) selected.remove(firstBikeMac)
                                            }
                                            selected.add(mac)
                                        } else {
                                            selected.remove(mac)
                                        }
                                    }
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }

                // Connect button
                val selectedBike = devices.firstOrNull { classifyDeviceKind(it) == DeviceKind.Bike && it.device.address in selected }?.device
                val selectedHr = devices.firstOrNull { classifyDeviceKind(it) == DeviceKind.HeartRate && it.device.address in selected }?.device
                val canConnect = selectedBike != null

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        stopScan(); scanning = false
                        // connect bike (existing route)
                        connect(selectedBike!!) {
                            vm.setConnectedDeviceName(selectedBike.name ?: selectedBike.address)
                        }
                        // optional HR
                        selectedHr?.let { d ->
                            connectHr(d) {}      // <-- you’ll add this param from MainActivity
                            subscribeHr { bpm -> // <-- and this
                                vm.setHeartRateInstant(bpm)
                            }
                        }
                    },
                    enabled = canConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to Devices")
                }
            }
            else {
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
                    calories = CalorieCalculation(ui.totalKJ, ui.heartHistory, profile)
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

    if (showProfile) {
        val scope = rememberCoroutineScope()
        UserProfileDialog(
            initial = profile,
            onDismiss = { showProfile = false },
            onSave = { updated ->
                scope.launch {
                    UserPrefs.save(ctx, updated)
                }
                showProfile = false
            }
        )
    }
}
