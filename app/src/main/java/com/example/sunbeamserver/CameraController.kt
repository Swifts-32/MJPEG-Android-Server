package com.example.sunbeamserver

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    @Volatile
    private var latestJpegFrame: ByteArray? = null

    private val resolution = Size(640, 480) // Adjust for your needs
    private val jpegBuffer = ByteArrayOutputStream(1024 * 1024) // 1MB reusable buffer

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        latestJpegFrame = imageProxy.toJpegByteArray()
                    }
                    catch (e: Exception) {
                        Log.e("CameraController", "JPEG conversion failed")
                    }
                    finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)

                Log.i("CameraController", "CameraX started at resolution ${resolution.width}x${resolution.height}")

            } catch (e: Exception) {
                Log.e("CameraController", "CameraX initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getLatestFrame(): ByteArray? = latestJpegFrame

    fun getResolution(): Size = resolution

    fun shutdown() {
        executor.shutdown()
    }

    private fun ImageProxy.toJpegByteArray(): ByteArray? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            jpegBuffer.reset()

            yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, jpegBuffer)
            jpegBuffer.toByteArray()
        } catch (e: Exception) {
            Log.e("ImageProxyExt", "Failed to convert ImageProxy to JPEG: ${e.message}")
            null
        }
    }

}
