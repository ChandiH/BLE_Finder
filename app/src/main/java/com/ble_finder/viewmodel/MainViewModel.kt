package com.ble_finder.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ble_finder.bluetooth.BLEScanner
import com.ble_finder.bluetooth.ClassicBluetoothScanner
import com.ble_finder.data.AppDatabase
import com.ble_finder.data.DeviceRepository
import com.ble_finder.data.SavedDevice
import com.ble_finder.utils.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bleScanner = BLEScanner(application.applicationContext)
    private val classicScanner = ClassicBluetoothScanner(application.applicationContext)
    private val repository = DeviceRepository(AppDatabase.getDatabase(application).savedDeviceDao())
    private val notificationHelper = NotificationHelper(application)

    val scanResults: StateFlow<List<ScanResult>> = bleScanner.scanResults
    val classicScanResults: StateFlow<List<BluetoothDevice>> = classicScanner.scanResults
    val savedDevices: StateFlow<List<SavedDevice>> = repository.allSavedDevices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun startScan() {
        bleScanner.startScan()
        classicScanner.startScan()
        // Monitor saved devices during scan
        viewModelScope.launch {
            scanResults.collect { results ->
                results.forEach { result ->
                    val savedDevice = repository.getDeviceByMacAddress(result.device.address)
                    if (savedDevice != null) {
                        repository.updateDeviceStatus(result)
                        checkDeviceRange(savedDevice)
                    }
                }
            }
        }
    }

    fun stopScan() {
        bleScanner.stopScan()
        classicScanner.stopScan()
    }

    fun isScanning(): Boolean = bleScanner.isScanning() || classicScanner.isScanning()

    fun saveDevice(scanResult: ScanResult) {
        viewModelScope.launch {
            repository.saveDevice(scanResult)
        }
    }

    fun deleteDevice(device: SavedDevice) {
        viewModelScope.launch {
            repository.deleteDevice(device)
            notificationHelper.cancelNotification(device)
        }
    }

    fun toggleNotifications(device: SavedDevice, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleNotifications(device, enabled)
            if (!enabled) {
                notificationHelper.cancelNotification(device)
            }
        }
    }

    fun updateNotificationThreshold(device: SavedDevice, distance: Double) {
        viewModelScope.launch {
            repository.updateNotificationThreshold(device, distance)
        }
    }

    private fun checkDeviceRange(device: SavedDevice) {
        viewModelScope.launch {
            val updatedDevice = repository.getDeviceByMacAddress(device.macAddress)
            updatedDevice?.let {
                if (!it.isInRange && it.notificationEnabled) {
                    notificationHelper.showDeviceOutOfRangeNotification(it)
                } else if (it.isInRange) {
                    notificationHelper.cancelNotification(it)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
        classicScanner.cleanup()
    }
} 