package com.ble_finder.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClassicBluetoothScanner(private val context: Context) {
    private val TAG = "ClassicBluetoothScanner"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanning = false

    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val currentList = _scanResults.value.toMutableList()
                        if (!currentList.any { existingDevice -> existingDevice.address == device.address }) {
                            currentList.add(device)
                            _scanResults.value = currentList
                            Log.d(TAG, "Found classic device: ${device.name ?: "Unknown"} (${device.address})")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    scanning = true
                    Log.d(TAG, "Classic Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    Log.d(TAG, "Classic Bluetooth discovery finished")
                    // Restart discovery if we're still supposed to be scanning
                    if (scanning) {
                        startScan()
                    }
                }
            }
        }
    }

    init {
        // Register for broadcasts when a device is discovered
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
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

        try {
            // Clear previous results
            _scanResults.value = emptyList()
            
            // Start discovery
            bluetoothAdapter?.startDiscovery()
            scanning = true
            Log.d(TAG, "Started Classic Bluetooth scan")
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
                bluetoothAdapter?.cancelDiscovery()
                Log.d(TAG, "Stopped Classic Bluetooth scan")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    fun isScanning(): Boolean = scanning

    fun getDeviceCount(): Int = _scanResults.value.size

    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}