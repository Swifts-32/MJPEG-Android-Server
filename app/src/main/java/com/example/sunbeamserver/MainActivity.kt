package com.example.sunbeamserver

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: CameraController
    private lateinit var mjpegServer: MJPEGServer

    companion object {
        private const val REQUEST_CODE_PERMISSION = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_camera)

        // Request camera permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSION
            )
        } else {
            startSystem() // Start the camera
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startSystem()
        } else {
            Log.e("MainActivity", "Camera permission not granted")
        }
    }

    private fun startSystem() {
        cameraController = CameraController(this, this)

        mjpegServer = MJPEGServer(cameraController)
        mjpegServer.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraController.shutdown()
        mjpegServer.stop()
    }
}


