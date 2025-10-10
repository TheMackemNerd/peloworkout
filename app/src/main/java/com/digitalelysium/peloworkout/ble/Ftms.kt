package com.digitalelysium.peloworkout.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FtmsFields(
    val flagsBits: Int,
    val instSpeedKph: Double? = null,
    val instCadenceRpm: Double? = null,
    val resistanceLevel: Double? = null,
    val instPowerW: Double? = null
)
private fun ByteBuffer.u16() = (short.toInt() and 0xFFFF)
private fun ByteBuffer.s16() = short.toInt()

internal fun parseIndoorBikeDataGrupetto(bytes: ByteArray): FtmsFields {
    if (bytes.size < 10) return FtmsFields(0)
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val flags = bb.short.toInt() and 0xFFFF
    val speedRaw = bb.u16()
    val cadenceHalf = bb.u16()
    val resistance = bb.s16().toDouble()
    val power = bb.s16().toDouble()
    return FtmsFields(
        flagsBits = flags,
        instSpeedKph = speedRaw / 100.0,
        instCadenceRpm = cadenceHalf / 2.0,
        resistanceLevel = resistance,
        instPowerW = power
    )
}