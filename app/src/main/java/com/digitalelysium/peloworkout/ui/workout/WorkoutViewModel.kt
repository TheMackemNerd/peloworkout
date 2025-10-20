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
import kotlinx.coroutines.isActive

import kotlinx.coroutines.flow.Flow

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
    private val hrStaleMs = 5000L

    // heart rate
    private var lastHr: Int? = null
    private var lastHrAt = 0L
    private val hrHoldMs = 3_000L   // was 5_000, this is the “anti-flicker” window

    fun setConnectedDeviceName(name: String?) {
        _ui.update { it.copy(connectedDeviceName = name) }
    }

    fun setHeartRateInstant(bpm: Int) {
        lastHr = bpm
        lastHrAt = android.os.SystemClock.elapsedRealtime()
        // do NOT push ui.heartRate here; let the tick own it
    }

    fun clearHeartRateInstant() {
        lastHr = null
        lastHrAt = 0L
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
                heartHistory = emptyList(),
                sessionStartEpochMs = System.currentTimeMillis()
            )
        }
        startTicker()
    }

    fun pause() {
        _ui.update { it.copy(paused = true) }
        // keep timerJob running; it skips ticks while paused
        // or, if you prefer: timerJob?.cancel() and restartTicker() in resume()
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _ui.update { it.copy(running = false, paused = false) }
    }

    fun resume() {
        if (!_ui.value.running) return  // guard: resume only if a session exists
        _ui.update { it.copy(paused = false) }
        if (timerJob == null || !timerJob!!.isActive) startTicker()
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

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            // take current histories so we append, not restart
            val powerHist = _ui.value.powerHistory.toMutableList()
            val speedHist = _ui.value.speedHistory.toMutableList()
            val cadenceHist = _ui.value.cadenceHistory.toMutableList()
            val hrHist = _ui.value.heartHistory.toMutableList()

            while (isActive) {
                delay(1000)

                val state = _ui.value
                if (!state.running || state.paused) continue

                val now = android.os.SystemClock.elapsedRealtime()

                // cadence staleness (your existing logic)
                val cadence = when {
                    lastCadenceAt == 0L -> state.cadence
                    now - lastCadenceAt > cadenceStaleMs -> 0.0
                    else -> lastCadenceRpm ?: state.cadence
                }

                var power = lastPowerW
                var speed = lastSpeedKph
                if (lastPowerAt != 0L && now - lastPowerAt > valueStaleMs) power = null
                if (lastSpeedAt != 0L && now - lastSpeedAt > valueStaleMs) speed = null

                // HR hold (anti-flicker)
                val hr: Int? = if (lastHrAt != 0L && now - lastHrAt <= hrHoldMs) lastHr else null

                var distance = state.distanceKm
                var totalKJ  = state.totalKJ
                var topPower = state.topPower
                var topSpeed = state.topSpeed
                var maxHr    = state.maxHr

                val pVal = (power ?: 0.0).also { totalKJ += it / 1000.0 }
                val sVal = (speed ?: 0.0).also { distance += it / 3600.0 }
                powerHist += pVal
                speedHist += sVal
                cadenceHist += (cadence ?: 0.0)
                hrHist += hr

                topPower = kotlin.math.max(topPower, power ?: 0.0)
                topSpeed = kotlin.math.max(topSpeed, speed ?: 0.0)
                if (hr != null) maxHr = kotlin.math.max(maxHr, hr)

                _ui.update {
                    it.copy(
                        elapsed = it.elapsed + 1,
                        power = power,
                        speed = speed,
                        cadence = cadence,
                        heartRate = hr,
                        maxHr = maxHr,
                        distanceKm = distance,
                        totalKJ = totalKJ,
                        topPower = topPower,
                        topSpeed = topSpeed,
                        powerHistory = powerHist.toList(),
                        speedHistory = speedHist.toList(),
                        cadenceHistory = cadenceHist.toList(),
                        heartHistory = hrHist.toList()
                    )
                }
            }
        }
    }

}
