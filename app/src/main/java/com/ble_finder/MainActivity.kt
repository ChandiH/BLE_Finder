package com.ble_finder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ble_finder.utils.DistanceCalculator
import com.ble_finder.viewmodel.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ble_finder.data.SavedDevice
import com.ble_finder.activity.SensorActivityRecognition
import kotlin.math.absoluteValue
import android.bluetooth.BluetoothDevice

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private lateinit var activityRecognition: SensorActivityRecognition

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
        activityRecognition = SensorActivityRecognition(this)
        
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

        // Start sensor-based activity recognition
        activityRecognition.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BLEFinderScreen() {
        val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
        val classicScanResults by viewModel.classicScanResults.collectAsStateWithLifecycle()
        val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
        var isScanning by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(0) }
        val currentActivity by activityRecognition.currentActivity.collectAsStateWithLifecycle()
        var showBluetoothDialog by remember { mutableStateOf(false) }

        // Update scanning state
        LaunchedEffect(Unit) {
            snapshotFlow { viewModel.isScanning() }
                .collect { scanning ->
                    isScanning = scanning
                }
        }

        // Handle automatic scanning based on selected tab
        LaunchedEffect(selectedTab) {
            if (selectedTab == 0) {
                if (bluetoothAdapter?.isEnabled == true) {
                    checkPermissionsAndStartScan()
                } else {
                    showBluetoothDialog = true
                }
            } else {
                viewModel.stopScan()
            }
        }

        // Show dialog if Bluetooth is disabled
        if (showBluetoothDialog) {
            AlertDialog(
                onDismissRequest = { showBluetoothDialog = false },
                title = { Text("Bluetooth Required") },
                text = { Text("Please enable Bluetooth to scan for devices.") },
                confirmButton = {
                    TextButton(onClick = {
                        showBluetoothDialog = false
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBluetoothDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = { Text("BLE Beacon Finder") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    // Activity status bar
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Activity: $currentActivity",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    // Scanning status bar
                    if (isScanning) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scanning for devices... (${scanResults.size} found)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("BLE Beacons (${scanResults.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Classic Devices (${classicScanResults.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Saved (${savedDevices.size})") }
                    )
                }

                when (selectedTab) {
                    0 -> ScanResultsList(scanResults, savedDevices)
                    1 -> ClassicBluetoothList(classicScanResults)
                    2 -> SavedDevicesList(savedDevices)
                }
            }
        }
    }

    @Composable
    fun ScanResultsList(scanResults: List<ScanResult>, savedDevices: List<SavedDevice>) {
        if (scanResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!bluetoothAdapter?.isEnabled!!) {
                        Text(
                            text = "Bluetooth is disabled\nPlease enable Bluetooth to scan for devices",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Searching for BLE devices...\nMake sure devices are advertising",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    scanResults.sortedBy {
                        DistanceCalculator.calculateDistance(it.rssi, it.txPower ?: -59)
                    }
                ) { result ->
                    val isSaved = savedDevices.any { it.macAddress == result.device.address }
                    DeviceItem(result, isSaved)
                }
            }
        }
    }

    @Composable
    fun ClassicBluetoothList(devices: List<BluetoothDevice>) {
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!bluetoothAdapter?.isEnabled!!) {
                        Text(
                            text = "Bluetooth is disabled\nPlease enable Bluetooth to scan for devices",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Searching for Classic Bluetooth devices...\nMake sure devices are discoverable",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    ClassicDeviceItem(device)
                }
            }
        }
    }

    @Composable
    fun SavedDevicesList(savedDevices: List<SavedDevice>) {
        if (savedDevices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved devices\nScan and save devices to monitor them",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedDevices) { device ->
                    SavedDeviceItem(device)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceItem(result: ScanResult, isSaved: Boolean) {
        val distance = DistanceCalculator.calculateDistance(result.rssi, result.txPower)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
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
                    if (!isSaved) {
                        IconButton(onClick = { viewModel.saveDevice(result) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Save Device",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
                        SignalStrengthIndicator(result.rssi)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ClassicDeviceItem(device: BluetoothDevice) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
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
                            text = device.name ?: "Unknown Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { /* TODO: Add pairing functionality */ }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Pair Device",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        Text(
                            text = "Classic Bluetooth Device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SavedDeviceItem(device: SavedDevice) {
        var showDialog by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableStateOf(device.notificationThresholdDistance.toFloat()) }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Set Range Threshold") },
                text = {
                    Column {
                        Text("Set the distance threshold for out-of-range notifications")
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                        Text("${sliderPosition.toInt()} meters")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateNotificationThreshold(device, sliderPosition.toDouble())
                            showDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
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
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = device.macAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { showDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.deleteDevice(device) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = if (device.isInRange) "In Range" else "Out of Range",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (device.isInRange) 
                                    Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = "Threshold",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${device.notificationThresholdDistance.toInt()}m",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = device.notificationEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleNotifications(device, enabled)
                        }
                    )
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
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startBleScan() {
        if (bluetoothAdapter?.isEnabled == true) {
            viewModel.startScan()
        } else {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityRecognition.stop()
    }
}