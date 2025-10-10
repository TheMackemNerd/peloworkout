package com.digitalelysium.peloworkout.ui.workout

data class WorkoutUiState(
    val connectedDeviceName: String? = null,
    val scanning: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    val elapsed: Int = 0,

    val cadence: Double? = null,
    val power: Double? = null,
    val speed: Double? = null,
    val distanceKm: Double = 0.0,

    val totalKJ: Double = 0.0,
    val topPower: Double = 0.0,
    val topSpeed: Double = 0.0,

    val resistanceRaw: Double? = null,
    val resistancePct: Double? = null,

    // for upload
    val powerHistory: List<Double> = emptyList(),
    val speedHistory: List<Double> = emptyList(),
    val cadenceHistory: List<Double> = emptyList(),
    val sessionStartEpochMs: Long = 0L
)
