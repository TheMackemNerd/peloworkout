package com.digitalelysium.peloworkout.strava

import android.annotation.SuppressLint
import java.time.Instant
import java.util.Locale

@SuppressLint("NewApi")
fun buildTcx(
    startEpochMs: Long,
    power: List<Double>,
    speedKph: List<Double>,
    cadenceRpm: List<Double>,
    heartBpm: List<Int?>? = null
): ByteArray {
    val startIso = Instant.ofEpochMilli(startEpochMs).toString()
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """)
    sb.append("""xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """)
    sb.append("""xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">""")
    sb.append("""<Activities><Activity Sport="Biking"><Id>$startIso</Id><Lap StartTime="$startIso">""")

    val totalSeconds = power.size
    // Integrate distance from speed stream to keep TCX internally consistent
    var meters = 0.0
    val distPerSec = speedKph.map { it / 3.6 } // m/s
    distPerSec.forEach { meters += it }
    sb.append("<TotalTimeSeconds>$totalSeconds</TotalTimeSeconds>")
    sb.append("<DistanceMeters>${"%.1f".format(Locale.UK, meters)}</DistanceMeters>")
    sb.append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod>")
    sb.append("<Track>")

    var accMeters = 0.0
    for (i in 0 until totalSeconds) {
        val t = Instant.ofEpochMilli(startEpochMs + i * 1000L).toString()
        val sp = speedKph.getOrNull(i) ?: 0.0
        accMeters += sp / 3.6
        val cad = cadenceRpm.getOrNull(i)?.toInt() ?: 0
        val w = power.getOrNull(i)?.toInt() ?: 0
        val hr = heartBpm?.getOrNull(i)?.takeIf { it > 0 }

        sb.append("<Trackpoint><Time>$t</Time>")
        sb.append("<DistanceMeters>${"%.1f".format(Locale.UK, accMeters)}</DistanceMeters>")
        if (hr != null) {
            sb.appendLine("    <HeartRateBpm><Value>$hr</Value></HeartRateBpm>")
        }
        if (cad > 0) sb.append("<Cadence>$cad</Cadence>")
        sb.append("<Extensions><ns3:TPX><ns3:Watts>$w</ns3:Watts></ns3:TPX></Extensions>")
        sb.append("</Trackpoint>")
    }
    sb.append("</Track></Lap><Notes>Peloton via Peloworkout</Notes></Activity></Activities></TrainingCenterDatabase>")
    return sb.toString().toByteArray(Charsets.UTF_8)
}

