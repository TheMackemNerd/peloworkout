package com.digitalelysium.peloworkout.ble

fun parseHrMeasurement(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
    val flags = bytes[0].toInt() and 0xFF
    val sixteenBit = (flags and 0x01) != 0
    return if (sixteenBit) {
        val b1 = bytes.getOrNull(1)?.toInt() ?: return null
        val b2 = bytes.getOrNull(2)?.toInt() ?: return null
        ((b2 and 0xFF) shl 8) or (b1 and 0xFF)
    } else {
        (bytes.getOrNull(1)?.toInt() ?: return null) and 0xFF
    }
}
