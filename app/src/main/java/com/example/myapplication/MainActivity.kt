package com.example.myapplication

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var logger: GnssImuCameraLogger
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private var isRunning = false
    private lateinit var uisatellites: TextView

    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (!locationGranted || !cameraGranted) {
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
        setupUI()
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        )
        logger = GnssImuCameraLogger(this, this) { satelliteDataText ->
            runOnUiThread {
                uisatellites.text = satelliteDataText
            }
        }
        btnStart.setOnClickListener {
            if (!isRunning) {
                try {
                    logger.onCreate()
                    isRunning = true
                    btnStart.isEnabled = false
                    btnStop.isEnabled = true
                }
                catch (e: SecurityException) {
                    Log.e("MainActivity", "Permission revoked", e)
                }
            }
        }
        btnStop.setOnClickListener {
            if (isRunning) {
                logger.onDestroy()
                isRunning = false
                btnStart.isEnabled = true
                btnStop.isEnabled = false
            }
        }
    }

    private fun setupUI() {
        btnStart = Button(this).apply { text = "Start" }
        btnStop = Button(this).apply { text = "Stop"; isEnabled = false }

        uisatellites = TextView(this).apply {
            text = "Спутники: 0"
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }

        val buttons = LinearLayout(this).apply {
            addView(btnStart, LinearLayout.LayoutParams(0, -2, 1f))
            addView(btnStop, LinearLayout.LayoutParams(0, -2, 1f))
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            addView(uisatellites, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(buttons)
        })

    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            logger.onDestroy()
            isRunning = false
        }
    }
}