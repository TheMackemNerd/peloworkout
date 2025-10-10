package com.digitalelysium.peloworkout.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import java.util.UUID

/** Common FTMS UUIDs you can share across the app */
object FtmsUuids {
    val FTMS_SERVICE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val INDOOR_BIKE_DATA: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_RESISTANCE_RANGE: UUID = UUID.fromString("00002ad6-0000-1000-8000-00805f9b34fb")
    val CCC_DESC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

object BleScanConfig {
    fun ftmsFilters(): List<ScanFilter> =
        listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(FtmsUuids.FTMS_SERVICE))
                .build()
        )

    fun lowLatencySettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
}

class BleScanner(private val scanner: BluetoothLeScanner?) {
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onDevice: (ScanResult) -> Unit) {
        stop() // be safe
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDevice(result)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(onDevice)
            }
        }
        callback = cb
        scanner?.startScan(BleScanConfig.ftmsFilters(), BleScanConfig.lowLatencySettings(), cb)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { cb ->
            try { scanner?.stopScan(cb) } catch (_: SecurityException) {}
        }
        callback = null
    }
}
