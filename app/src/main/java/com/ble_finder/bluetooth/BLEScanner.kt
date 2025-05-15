package com.ble_finder.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BLEScanner(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // Stops scanning after 10 seconds
    private val SCAN_PERIOD: Long = 10000

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val currentList = _scanResults.value.toMutableList()
            val existingDevice = currentList.find { it.device.address == result.device.address }
            if (existingDevice != null) {
                currentList[currentList.indexOf(existingDevice)] = result
            } else {
                currentList.add(result)
            }
            _scanResults.value = currentList
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!scanning && bluetoothAdapter?.isEnabled == true) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

            // Stop scanning after SCAN_PERIOD
            handler.postDelayed({
                stopScan()
            }, SCAN_PERIOD)

            scanning = true
            _scanResults.value = emptyList()
            bluetoothLeScanner?.startScan(leScanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanning && bluetoothAdapter?.isEnabled == true) {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    fun isScanning(): Boolean = scanning
} 