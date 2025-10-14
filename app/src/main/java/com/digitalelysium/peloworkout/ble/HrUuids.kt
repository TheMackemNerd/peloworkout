package com.digitalelysium.peloworkout.ble
import java.util.UUID

object HrUuids {
    val HRS_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HR_MEASUREMENT: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    val CCC_DESC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

enum class DeviceKind { Bike, HeartRate, Unknown }
