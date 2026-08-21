package com.example.myapplication


import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventCallback
import android.location.GnssMeasurementRequest
import android.location.LocationManager
import android.location.GnssMeasurementsEvent
import android.hardware.SensorManager
import android.location.GnssStatus
import androidx.annotation.RequiresPermission
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.io.File

class GnssImuCameraLogger(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onSatellitesUpdated: (String) -> Unit
) {
    private lateinit var imageCapture: ImageCapture

    private val logger = CSVLogger(
        File(
            context.getExternalFilesDir(null),
            "${System.currentTimeMillis()}.csv"
        ).absolutePath
    )

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private  val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /* Only uncalibrated sensors */
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)

    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)

    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)

    private val sensorThread = HandlerThread("SensorThread").apply { start() }

    private val sensorHandler = Handler(sensorThread.looper)

    private val sensorExecutor = Executor { command -> sensorHandler.post(command) }

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val acclCallback = object : SensorEventCallback() {
        override fun onSensorChanged(event: SensorEvent) {
            val timestampAcc = event.timestamp
            val accX = event.values[0] // Measured acceleration along X axis [m/s2]
            val accY = event.values[1] // Measured acceleration along Y axis [m/s2]
            val accZ = event.values[2] // Measured acceleration along Z axis [m/s2]
            logger.log("ACCL",timestampAcc,accX,accY,accZ)
        }
    }

    private val gyroCallback = object : SensorEventCallback() {
        override fun onSensorChanged(event: SensorEvent) {
            val timestampGyro = event.timestamp
            val rateX = event.values[0] // Rate of rotation around X axis [rad/s]
            val rateY = event.values[1] // Rate of rotation around Y axis [rad/s]
            val rateZ = event.values[2] // Rate of rotation around Z axis [rad/s]
            logger.log("GYRO",timestampGyro,rateX,rateY,rateZ)
        }
    }

    private val magnCallback = object : SensorEventCallback() {
        override fun onSensorChanged(event: SensorEvent) {
            val timestampMag = event.timestamp
            val gX = event.values[0] // Geomagnetic field strength along X axis [μT]
            val gY = event.values[1] // Geomagnetic field strength along Y axis [μT]
            val gZ = event.values[2] // Geomagnetic field strength along Z axis [μT]
            logger.log("MAGN",timestampMag,gX,gY,gZ)
        }
    }

    private val gnssMeasurementsCallback =
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                logger.log("GNSS", event.toString())
            }
        }

    private val cameraCallback =
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                takePhoto()
            }
        }

    private val uiCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            onSatellitesUpdated("Спутников: ${status.satelliteCount}")
        }
    }

    private val GNSS_INTERVAL_MS = 1000

    private val IMU_SAMPLING_PERIOD_US = 1_000_000

    private fun startCameraSync() {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
    }

    private fun takePhoto() {
        val photoFile = File(
            context.getExternalFilesDir(null),
            "IMG_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "${exception.message}", exception)
                }
            }
        )
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onCreate() {
        startCameraSync()
        locationManager.registerGnssMeasurementsCallback(
            GnssMeasurementRequest.Builder()
                .setFullTracking(true)
                .setIntervalMillis(GNSS_INTERVAL_MS)
                .build(),
            sensorExecutor,
            gnssMeasurementsCallback
        )
        locationManager.registerGnssStatusCallback(context.mainExecutor, uiCallback)
        locationManager.registerGnssMeasurementsCallback(sensorExecutor, cameraCallback)
        sensorManager.registerListener(acclCallback, accelerometer, IMU_SAMPLING_PERIOD_US,sensorHandler)
        sensorManager.registerListener(gyroCallback, gyroscope, IMU_SAMPLING_PERIOD_US, sensorHandler)
        sensorManager.registerListener(magnCallback, magnetometer, IMU_SAMPLING_PERIOD_US, sensorHandler)
    }

    fun onDestroy() {
        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
        locationManager.unregisterGnssStatusCallback(uiCallback)
        locationManager.unregisterGnssMeasurementsCallback(cameraCallback)
        sensorManager.unregisterListener(acclCallback)
        sensorManager.unregisterListener(gyroCallback)
        sensorManager.unregisterListener(magnCallback)
        logger.close()
        sensorThread.quitSafely()
        cameraExecutor.shutdown()
    }
}