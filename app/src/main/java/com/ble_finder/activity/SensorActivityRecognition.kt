package com.ble_finder.activity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class SensorActivityRecognition(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _currentActivity = MutableStateFlow<String>("Unknown")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

    // Window size for activity recognition (2 seconds at 50Hz sampling rate)
    private val WINDOW_SIZE = 100
    private val accelerometerReadings = ArrayList<AccelerometerData>(WINDOW_SIZE)
    private val gyroscopeReadings = ArrayList<GyroscopeData>(WINDOW_SIZE)

    // Activity recognition thresholds
    private val STANDING_THRESHOLD = 0.5f
    private val WALKING_THRESHOLD = 1.2f
    private val RUNNING_THRESHOLD = 2.5f
    private val DRIVING_ROTATION_THRESHOLD = 0.1f

    private var lastActivityUpdate = 0L
    private val UPDATE_INTERVAL = 5000L // Update activity every 500ms

    data class AccelerometerData(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Float
    )

    data class GyroscopeData(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Float
    )

    fun start() {
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        sensorManager.registerListener(
            this,
            gyroscope,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        accelerometerReadings.clear()
        gyroscopeReadings.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometerData(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscopeData(event)
        }

        // Update activity recognition at specified intervals
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActivityUpdate >= UPDATE_INTERVAL) {
            recognizeActivity()
            lastActivityUpdate = currentTime
        }
    }

    private fun processAccelerometerData(event: SensorEvent) {
        val alpha = 0.8f
        val gravity = FloatArray(3) { 0f }
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
        val linearAccX = event.values[0] - gravity[0]
        val linearAccY = event.values[1] - gravity[1]
        val linearAccZ = event.values[2] - gravity[2]
        val magnitude = calculateMagnitude(linearAccX, linearAccY, linearAccZ)
        val data = AccelerometerData(
            event.timestamp,
            event.values[0],
            event.values[1],
            event.values[2],
            magnitude
        )

        if (accelerometerReadings.size >= WINDOW_SIZE) {
            accelerometerReadings.removeAt(0)
        }
        accelerometerReadings.add(data)
    }

    private fun processGyroscopeData(event: SensorEvent) {
        val magnitude = calculateMagnitude(event.values[0], event.values[1], event.values[2])
        val data = GyroscopeData(
            event.timestamp,
            event.values[0],
            event.values[1],
            event.values[2],
            magnitude
        )

        if (gyroscopeReadings.size >= WINDOW_SIZE) {
            gyroscopeReadings.removeAt(0)
        }
        gyroscopeReadings.add(data)
    }

    private fun calculateMagnitude(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }

    private fun recognizeActivity() {
        if (accelerometerReadings.size < WINDOW_SIZE || gyroscopeReadings.size < WINDOW_SIZE) {
            return
        }

        // Calculate average magnitudes
        val avgAccMagnitude = accelerometerReadings
            .map { it.magnitude }
            .average()
            .toFloat()

        val avgGyroMagnitude = gyroscopeReadings
            .map { it.magnitude }
            .average()
            .toFloat()

        // Calculate variance for accelerometer data
        val variance = calculateVariance(accelerometerReadings.map { it.magnitude })

        // Determine activity based on sensor data patterns
        val activity = when {
            // Standing still: very low acceleration variance and gyroscope movement
            variance < STANDING_THRESHOLD && avgGyroMagnitude < 0.1f -> "Standing Still"
            
            // Driving: moderate acceleration with consistent gyroscope readings
            avgGyroMagnitude < DRIVING_ROTATION_THRESHOLD && variance < WALKING_THRESHOLD -> "Driving"
            
            // Running: high acceleration variance
            variance > RUNNING_THRESHOLD -> "Running"
            
            // Walking: moderate acceleration variance
            variance > WALKING_THRESHOLD -> "Walking"
            
            // Default case
            else -> "Unknown"
        }

        _currentActivity.value = activity
    }

    private fun calculateVariance(values: List<Float>): Float {
        val mean = values.sum() / values.size
        return values.map { (it - mean) * (it - mean) }.sum() / values.size
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used in this implementation
    }
}