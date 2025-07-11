package com.ble_finder.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ble_finder.bluetooth.BLEScanner
import com.ble_finder.data.AppDatabase
import com.ble_finder.data.DeviceRepository
import com.ble_finder.data.SavedDevice
import com.ble_finder.utils.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Location

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bleScanner = BLEScanner(application.applicationContext)
    private val repository = DeviceRepository(AppDatabase.getDatabase(application).savedDeviceDao())
    private val notificationHelper = NotificationHelper(application)
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private val _currentActivity = MutableStateFlow("Unknown")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

    val scanResults: StateFlow<List<ScanResult>> = bleScanner.scanResults
    val savedDevices: StateFlow<List<SavedDevice>> = repository.allSavedDevices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    sealed class SaveResult {
        data class Success(val deviceName: String) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    fun setCurrentActivity(activity: String) {
        _currentActivity.value = activity
    }

    fun startScan() {
        bleScanner.startScan()
        // Monitor saved devices during scan
        viewModelScope.launch {
            scanResults.collect { results ->
                val isTraveling = _currentActivity.value in listOf("Running", "Walking", "Driving")
                results.forEach { result ->
                    val savedDevice = repository.getDeviceByMacAddress(result.device.address)
                    if (savedDevice != null) {
                        updateDeviceStatusWithLocation(result)
                        if (isTraveling) {
                            checkDeviceRange(savedDevice)
                        }
                    }
                }
            }
        }
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun isScanning(): Boolean = bleScanner.isScanning()

    fun saveDevice(scanResult: ScanResult) {
        viewModelScope.launch {
            try {
                repository.saveDevice(scanResult)
                _saveResult.value = SaveResult.Success(scanResult.device.name ?: "Unknown Device")
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Failed to save device")
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
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

    private fun updateDeviceStatusWithLocation(scanResult: ScanResult) {
        // Get current location and update device status with lat/lng
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            viewModelScope.launch {
                val device = repository.getDeviceByMacAddress(scanResult.device.address)
                if (device != null && location != null) {
                    repository.updateDeviceStatusWithLocation(
                        scanResult,
                        location.latitude,
                        location.longitude
                    )
                } else if (device != null) {
                    // fallback: update without location
                    repository.updateDeviceStatus(scanResult)
                }
            }
        }.addOnFailureListener {
            // fallback: update without location
            viewModelScope.launch {
                repository.updateDeviceStatus(scanResult)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
    }
} 