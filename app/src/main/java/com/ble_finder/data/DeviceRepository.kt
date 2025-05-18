package com.ble_finder.data

import android.bluetooth.le.ScanResult
import com.ble_finder.utils.DistanceCalculator
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val savedDeviceDao: SavedDeviceDao) {
    val allSavedDevices: Flow<List<SavedDevice>> = savedDeviceDao.getAllDevices()

    suspend fun saveDevice(scanResult: ScanResult) {
        val device = SavedDevice(
            macAddress = scanResult.device.address,
            name = scanResult.device.name ?: "Unknown Device",
            lastKnownRssi = scanResult.rssi,
            lastSeenTimestamp = System.currentTimeMillis(),
            isInRange = true
        )
        savedDeviceDao.insertDevice(device)
    }

    suspend fun deleteDevice(device: SavedDevice) {
        savedDeviceDao.deleteDevice(device)
    }

    suspend fun updateDeviceStatus(scanResult: ScanResult) {
        val distance = DistanceCalculator.calculateDistance(scanResult.rssi, scanResult.txPower)
        val device = savedDeviceDao.getDeviceByMacAddress(scanResult.device.address)
        
        device?.let {
            val isInRange = distance <= it.notificationThresholdDistance
            savedDeviceDao.updateDeviceStatus(
                macAddress = scanResult.device.address,
                rssi = scanResult.rssi,
                timestamp = System.currentTimeMillis(),
                isInRange = isInRange
            )
        }
    }

    suspend fun toggleNotifications(device: SavedDevice, enabled: Boolean) {
        savedDeviceDao.updateNotificationSetting(device.macAddress, enabled)
    }

    suspend fun updateNotificationThreshold(device: SavedDevice, distance: Double) {
        savedDeviceDao.updateNotificationThreshold(device.macAddress, distance)
    }

    suspend fun getDeviceByMacAddress(macAddress: String): SavedDevice? {
        return savedDeviceDao.getDeviceByMacAddress(macAddress)
    }
} 