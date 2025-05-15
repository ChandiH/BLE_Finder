package com.ble_finder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ble_finder.utils.DistanceCalculator
import com.ble_finder.viewmodel.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startBleScan()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startBleScan()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BLEFinderScreen()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BLEFinderScreen() {
        val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
        val isScanning by remember { mutableStateOf(viewModel.isScanning()) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("BLE Beacon Finder") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { checkPermissionsAndStartScan() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = if (isScanning) "Stop Scan" else "Start Scan"
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (scanResults.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            scanResults.sortedBy {
                                DistanceCalculator.calculateDistance(it.rssi, it.txPower)
                            }
                        ) { result ->
                            DeviceItem(result)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No beacons found\nTap the scan button to start searching",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceItem(result: android.bluetooth.le.ScanResult) {
        val distance = DistanceCalculator.calculateDistance(result.rssi, result.txPower)
        val readableDistance = DistanceCalculator.getReadableDistance(distance)

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = result.device.name ?: "Unknown Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.device.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SignalStrengthIndicator(result.rssi)
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Distance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "%.2f meters".format(distance),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = getDistanceLabel(distance),
                                style = MaterialTheme.typography.titleMedium,
                                color = getDistanceColor(distance),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailItem(label = "RSSI", value = "${result.rssi} dBm")
                    DetailItem(label = "Tx Power", value = "${result.txPower} dBm")
                }
            }
        }
    }

    @Composable
    fun SignalStrengthIndicator(rssi: Int) {
        val signalStrength = when {
            rssi > -60 -> Color(0xFF4CAF50) // Strong - Green
            rssi > -70 -> Color(0xFFFFC107) // Medium - Yellow
            else -> Color(0xFFF44336) // Weak - Red
        }
        
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Signal Strength",
            tint = signalStrength,
            modifier = Modifier.size(24.dp)
        )
    }

    @Composable
    fun DetailItem(label: String, value: String) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    private fun getDistanceLabel(distance: Double): String {
        return when {
            distance < 0 -> "Unknown"
            distance < 1 -> "Very Close"
            distance < 3 -> "Close"
            distance < 10 -> "Medium"
            else -> "Far"
        }
    }

    private fun getDistanceColor(distance: Double): Color {
        return when {
            distance < 0 -> Color.Gray
            distance < 1 -> Color(0xFF4CAF50) // Green
            distance < 3 -> Color(0xFF2196F3) // Blue
            distance < 10 -> Color(0xFFFFC107) // Yellow
            else -> Color(0xFFF44336) // Red
        }
    }

    private fun checkPermissionsAndStartScan() {
        if (hasRequiredPermissions()) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startBleScan() {
        if (bluetoothAdapter?.isEnabled == true) {
            viewModel.startScan()
        } else {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}