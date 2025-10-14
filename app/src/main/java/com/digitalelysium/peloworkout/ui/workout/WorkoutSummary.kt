package com.digitalelysium.peloworkout.ui.workout

data class WorkoutSummary(
    val elapsed: Int,
    val distanceKm: Double,
    val avgPowerW: Int,
    val topPowerW: Int,
    val topSpeedKph: Double,
    val totalKJ: Double,
    val estKcal: Int,
    val startEpochMs: Long,
    val powerHistory: List<Double>,
    val speedHistory: List<Double>,
    val cadenceHistory: List<Double>,
    val heartHistory: List<Int?>
)
