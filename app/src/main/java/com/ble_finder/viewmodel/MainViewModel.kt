package com.ble_finder.viewmodel

import android.app.Application
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import com.ble_finder.bluetooth.BLEScanner
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bleScanner = BLEScanner(application.applicationContext)
    val scanResults: StateFlow<List<ScanResult>> = bleScanner.scanResults

    fun startScan() {
        bleScanner.startScan()
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun isScanning(): Boolean = bleScanner.isScanning()

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
    }
} 