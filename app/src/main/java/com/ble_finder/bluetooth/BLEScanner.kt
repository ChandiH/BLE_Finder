package com.ble_finder.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BLEScanner(private val context: Context) {
    private val TAG = "BLEScanner"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // Scan continuously
    private val SCAN_PERIOD: Long = 0 // 0 means continuous scanning

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Highest power, fastest scanning
        .setReportDelay(0) // Report results immediately
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // Get all advertisements
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // More aggressive matching
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // Report all matches
        .build()

    // No filters to see all devices
    private val scanFilters = listOf<ScanFilter>()

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "Device found: ${result.device.name ?: "Unknown"} (${result.device.address})")
            
            val currentList = _scanResults.value.toMutableList()
            val existingDeviceIndex = currentList.indexOfFirst { it.device.address == result.device.address }
            
            if (existingDeviceIndex >= 0) {
                // Update existing device
                currentList[existingDeviceIndex] = result
            } else {
                // Add new device
                currentList.add(result)
                Log.d(TAG, "New device found: ${result.device.name ?: "Unknown"} (${result.device.address})")
            }
            
            _scanResults.value = currentList
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.d(TAG, "Batch scan results: ${results.size} devices")
            val currentList = _scanResults.value.toMutableList()
            
            results.forEach { result ->
                val existingDeviceIndex = currentList.indexOfFirst { it.device.address == result.device.address }
                if (existingDeviceIndex >= 0) {
                    currentList[existingDeviceIndex] = result
                } else {
                    currentList.add(result)
                }
            }
            
            _scanResults.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            scanning = false
            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> Log.e(TAG, "Scan already started")
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "Application registration failed")
                SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "BLE scanning not supported")
                SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal error")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) {
            Log.d(TAG, "Scan already in progress, stopping previous scan")
            stopScan()
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        try {
            // Clear previous results
            _scanResults.value = emptyList()
            
            // Start scan with settings and filters
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
            scanning = true
            Log.d(TAG, "Started BLE scan")

            if (SCAN_PERIOD > 0) {
                // Stop scanning after SCAN_PERIOD
                handler.postDelayed({
                    if (scanning) {
                        Log.d(TAG, "Scan timeout reached")
                        stopScan()
                    }
                }, SCAN_PERIOD)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}")
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            if (scanning && bluetoothAdapter?.isEnabled == true) {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Log.d(TAG, "Stopped BLE scan")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    fun isScanning(): Boolean = scanning

    fun getDeviceCount(): Int = _scanResults.value.size
} 