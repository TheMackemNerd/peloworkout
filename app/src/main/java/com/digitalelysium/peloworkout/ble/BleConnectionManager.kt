package com.digitalelysium.peloworkout.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.annotation.RequiresApi
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.digitalelysium.peloworkout.util.hasScanPerm
import com.digitalelysium.peloworkout.util.hasConnectPerm

class BleConnectionManager(
    context: Context,
    private val bleScanner: BleScanner
) {
    private val appCtx = context.applicationContext

    private var gatt: BluetoothGatt? = null

    // resistance range cache
    var resMin: Int? = null; private set
    var resMax: Int? = null; private set
    var resStep: Int? = null; private set

    private var onBikeData: ((ByteArray) -> Unit)? = null

    fun startScan(onDevice: (ScanResult) -> Unit): Boolean {
        if (!appCtx.hasScanPerm()) return false
        bleScanner.start(onDevice)
        return true
    }

    fun stopScan() {
        bleScanner.stop()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, onConnected: (() -> Unit)? = null): Boolean {
        if (!appCtx.hasConnectPerm()) return false
        gatt = device.connectGatt(appCtx, false, callback)
        this.onConnected = onConnected
        return true
    }

    fun subscribeIndoorBikeData(cb: ((ByteArray) -> Unit)?) {
        onBikeData = cb
    }

    private var onConnected: (() -> Unit)? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try { g.close() } catch (_: Exception) {}
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(FtmsUuids.FTMS_SERVICE)
                ?.getCharacteristic(FtmsUuids.INDOOR_BIKE_DATA)
                ?: return

            try {
                g.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(FtmsUuids.CCC_DESC) ?: return
                val enableValue =
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, enableValue)
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = enableValue
                        g.writeDescriptor(cccd)
                    }
                }
            } catch (_: SecurityException) { /* missing BLUETOOTH_CONNECT */ }

            g.getService(FtmsUuids.FTMS_SERVICE)
                ?.getCharacteristic(FtmsUuids.SUPPORTED_RESISTANCE_RANGE)
                ?.let { g.readCharacteristic(it) }

            onConnected?.invoke()
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            if (ch.uuid == FtmsUuids.SUPPORTED_RESISTANCE_RANGE) {
                @Suppress("DEPRECATION")
                ch.value?.let { parseResistanceRange(it) }
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic
        ) {
            if (ch.uuid == FtmsUuids.INDOOR_BIKE_DATA) {
                @Suppress("DEPRECATION")
                ch.value?.let { onBikeData?.invoke(it) }
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            if (ch.uuid == FtmsUuids.SUPPORTED_RESISTANCE_RANGE) parseResistanceRange(value)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (ch.uuid == FtmsUuids.INDOOR_BIKE_DATA) onBikeData?.invoke(value)
        }
    }

    private fun parseResistanceRange(bytes: ByteArray) {
        if (bytes.size < 6) return
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        resMin = bb.short.toInt()
        resMax = bb.short.toInt()
        resStep = bb.short.toInt()
    }

    fun resistancePercent(level: Double): Double? {
        val min = resMin; val maxR = resMax
        return if (min != null && maxR != null && maxR > min) {
            (((level - min) / (maxR - min).toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else null
    }
}
