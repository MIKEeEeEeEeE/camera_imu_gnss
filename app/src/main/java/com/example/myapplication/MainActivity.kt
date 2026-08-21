package com.example.myapplication

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var logger: GnssImuCameraLogger

    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (locationGranted && cameraGranted) {
                try {
                    logger.start()
                }
                catch (e: SecurityException) {
                    Log.e("MainActivity", "Permission revoked", e)
                }
            }
            else {
                AlertDialog.Builder(this)
                    .setTitle("Camera and Location Permissions Required")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.CAMERA
                            )
                        )
                    }
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = GnssImuCameraLogger(this, this)
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.stop()
    }
}