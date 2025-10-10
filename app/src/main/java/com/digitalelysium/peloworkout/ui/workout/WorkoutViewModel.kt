package com.digitalelysium.peloworkout.ui.workout

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalelysium.peloworkout.ble.parseIndoorBikeDataGrupetto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

class WorkoutViewModel(
    // keep FTMS specifics out of UI; manager supplies this
    private val resistancePercent: (Double) -> Double?
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkoutUiState())
    val ui: StateFlow<WorkoutUiState> = _ui

    // ephemeral last-knowns for calculations
    private var lastPowerW: Double? = null
    private var lastSpeedKph: Double? = null
    private var lastCadenceRpm: Double? = null
    private var lastPowerAt = 0L
    private var lastSpeedAt = 0L
    private var lastCadenceAt = 0L

    private var timerJob: Job? = null
    private val cadenceStaleMs = 2500L
    private val valueStaleMs = 5000L

    fun setConnectedDeviceName(name: String?) {
        _ui.update { it.copy(connectedDeviceName = name) }
    }

    fun setScanning(scanning: Boolean) {
        _ui.update { it.copy(scanning = scanning) }
    }

    fun start() {
        _ui.update {
            it.copy(
                running = true,
                paused = false,
                elapsed = 0,
                distanceKm = 0.0,
                totalKJ = 0.0,
                topPower = 0.0,
                topSpeed = 0.0,
                powerHistory = emptyList(),
                speedHistory = emptyList(),
                cadenceHistory = emptyList(),
                sessionStartEpochMs = System.currentTimeMillis()
            )
        }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            // local histories to avoid rebuilding lists every tick
            val powerHist = mutableListOf<Double>()
            val speedHist = mutableListOf<Double>()
            val cadenceHist = mutableListOf<Double>()
            while (_ui.value.running && !_ui.value.paused) {
                delay(1000)

                val now = SystemClock.elapsedRealtime()

                // stale handling
                val cadence = when {
                    lastCadenceAt == 0L -> _ui.value.cadence
                    now - lastCadenceAt > cadenceStaleMs -> 0.0
                    else -> lastCadenceRpm ?: _ui.value.cadence
                }

                var distance = _ui.value.distanceKm
                var totalKJ = _ui.value.totalKJ
                var topPower = _ui.value.topPower
                var topSpeed = _ui.value.topSpeed
                var power = lastPowerW
                var speed = lastSpeedKph

                if (lastPowerAt != 0L && now - lastPowerAt > valueStaleMs) power = null
                if (lastSpeedAt != 0L && now - lastSpeedAt > valueStaleMs) speed = null

                // accumulate histories and totals
                val pVal = (power ?: 0.0).also { totalKJ += it / 1000.0 }
                val sVal = (speed ?: 0.0).also { distance += it / 3600.0 }
                powerHist += pVal
                speedHist += sVal
                cadenceHist += (cadence ?: 0.0)

                topPower = max(topPower, power ?: 0.0)
                topSpeed = max(topSpeed, speed ?: 0.0)

                _ui.update {
                    it.copy(
                        elapsed = it.elapsed + 1,
                        power = power,
                        speed = speed,
                        cadence = cadence,
                        distanceKm = distance,
                        totalKJ = totalKJ,
                        topPower = topPower,
                        topSpeed = topSpeed,
                        powerHistory = powerHist.toList(),
                        speedHistory = speedHist.toList(),
                        cadenceHistory = cadenceHist.toList()
                    )
                }
            }
        }
    }

    fun pause() { _ui.update { it.copy(paused = true) } }
    fun resume() { _ui.update { it.copy(paused = false) }; start() /* restart ticking */ }

    fun stop() {
        timerJob?.cancel()
        _ui.update { it.copy(running = false, paused = false) }
    }

    // Called by BLE subscription
    fun onBike(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val p = parseIndoorBikeDataGrupetto(bytes) // keep your existing parser in ble package

        p.instPowerW?.let { w ->
            lastPowerW = w; lastPowerAt = now
            _ui.update { it.copy(power = w, topPower = max(it.topPower, w)) }
        }
        p.instSpeedKph?.let { v ->
            lastSpeedKph = v; lastSpeedAt = now
            _ui.update { it.copy(speed = v, topSpeed = max(it.topSpeed, v)) }
        }
        p.instCadenceRpm?.let { c ->
            lastCadenceRpm = c; lastCadenceAt = now
            _ui.update { it.copy(cadence = c) }
        }
        p.resistanceLevel?.let { lvl ->
            _ui.update {
                it.copy(
                    resistanceRaw = lvl,
                    resistancePct = resistancePercent(lvl)
                )
            }
        }
    }
}
