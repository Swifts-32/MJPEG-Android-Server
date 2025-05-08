package com.example.sunbeamserver

import android.util.Log
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class MJPEGServer(
    private val cameraController: CameraController,
    private val port: Int = 8080
) {
    private var serverSocket: ServerSocket = ServerSocket(port)
    private var isRunning = false

    private val executor = Executors.newCachedThreadPool()

    fun start() {
        isRunning = true
        serverSocket.soTimeout = 5000

        executor.execute {
            Log.d("MJPEGServer", "MJPEG server started on port $port")

            while (isRunning) {
                try {
                    val client = serverSocket.accept()
                    Log.d("MJPEGServer", "Client connected: $client")

                    executor.execute {
                        handleClient(client)
                    }
                } catch (e: Exception) {
                    Log.e("MJPEGServer", "Error accepting client connection: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket.close()
    }

    private fun handleClient(client: Socket) {
            val output: OutputStream = BufferedOutputStream(client.getOutputStream())

            try {
                Log.d("MJPEGServer", "Client connected: ${client.inetAddress}")

                val resolution = cameraController.getResolution()

                output.write("HTTP/1.0 200 OK\r\n".toByteArray())
                output.write("Content-Type: multipart/x-mixed-replace; boundary=frame\r\n".toByteArray())
                output.write("X-Resolution: ${resolution.width}x${resolution.height}\r\n".toByteArray())
                output.write("\r\n".toByteArray()) // end of headers
                output.flush()


                var lastFrameHash = 0

                while (isRunning && !client.isClosed) {
                    val frame = cameraController.getLatestFrame()

                    if (frame != null) {
                        val hash = frame.contentHashCode();

                        if (hash != lastFrameHash) {
                            lastFrameHash = hash

                            try {
                                output.write("--frame\r\n".toByteArray())
                                output.write("Content-Type: image/jpeg\r\n".toByteArray())
                                output.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                                output.write(frame)
                                output.write("\r\n".toByteArray())
                                output.flush()
                            } catch (e: Exception) {
                                Log.e("MJPEGServer", "Frame write error: ${e.message}")
                                break
                            }
                        }
                    }

                    Thread.sleep(1)
                }
            } catch (e: Exception) {
                Log.e("MJPEGServer", "Streaming error: ${e.message}")
            } finally {
                try {
                    client.close()
                } catch (e: Exception) {
                    Log.e("MJPEGServer", "Error closing client socket: ${e.message}")
                }
            }
    }
}
