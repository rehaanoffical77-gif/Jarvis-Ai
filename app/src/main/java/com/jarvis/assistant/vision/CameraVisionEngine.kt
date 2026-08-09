package com.jarvis.assistant.vision

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.io.ByteArrayOutputStream

/**
 * Live Camera Vision engine using Android Camera2 API + ImageReader.
 * Captures live frames from Front or Back camera at ~1 FPS, compresses to JPEG,
 * and passes byte arrays to Gemini Live for real-time vision processing.
 * Renders a live camera preview onto a target TextureView.
 */
class CameraVisionEngine(
    private val context: Context,
    private val onFrameCaptured: (ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "CameraVisionEngine"
        private const val CAPTURE_INTERVAL_MS = 1000L // 1 FPS
        private const val TARGET_WIDTH = 480
        private const val TARGET_HEIGHT = 640
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isFrontCamera = false
    private var isStreaming = false
    private var lastFrameTimeMs = 0L

    private var previewTextureView: TextureView? = null

    fun setPreviewTextureView(textureView: TextureView?) {
        this.previewTextureView = textureView
        if (isStreaming && cameraDevice != null) {
            startCaptureSession()
        }
    }

    fun isFrontLens(): Boolean = isFrontCamera
    fun isCameraStreaming(): Boolean = isStreaming

    @SuppressLint("MissingPermission")
    fun startCamera(useFront: Boolean = false) {
        if (isStreaming) {
            if (isFrontCamera == useFront) return
            stopCamera()
        }

        isFrontCamera = useFront
        isStreaming = true

        startBackgroundThread()

        val cameraId = getCameraId(useFront)
        if (cameraId == null) {
            Log.e(TAG, "No suitable camera found for useFront=$useFront")
            isStreaming = false
            return
        }

        try {
            imageReader = ImageReader.newInstance(
                TARGET_WIDTH, TARGET_HEIGHT,
                ImageFormat.YUV_420_888, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    processImage(reader)
                }, backgroundHandler)
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    isStreaming = false
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    isStreaming = false
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            isStreaming = false
        }
    }

    fun switchCamera() {
        startCamera(!isFrontCamera)
    }

    fun stopCamera() {
        if (!isStreaming) return
        isStreaming = false

        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }

        stopBackgroundThread()
    }

    private fun getCameraId(useFront: Boolean): String? {
        val targetFacing = if (useFront) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == targetFacing) return id
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun startCaptureSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return

        val surfaces = mutableListOf<Surface>(reader.surface)

        val previewSurface = if (previewTextureView?.isAvailable == true) {
            val texture = previewTextureView!!.surfaceTexture
            texture?.setDefaultBufferSize(TARGET_WIDTH, TARGET_HEIGHT)
            if (texture != null) Surface(texture) else null
        } else null

        if (previewSurface != null) {
            surfaces.add(previewSurface)
        }

        try {
            @Suppress("DEPRECATION")
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                        if (previewSurface != null) addTarget(previewSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera session")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture session", e)
        }
    }

    private fun processImage(reader: ImageReader) {
        val image: Image? = try {
            reader.acquireLatestImage() ?: reader.acquireNextImage()
        } catch (e: Exception) {
            null
        }

        if (image == null) return

        val now = System.currentTimeMillis()
        if (now - lastFrameTimeMs < CAPTURE_INTERVAL_MS) {
            image.close()
            return
        }
        lastFrameTimeMs = now

        try {
            val jpegBytes = yuv420ToJpeg(image)
            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                onFrameCaptured(jpegBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image conversion error", e)
        } finally {
            image.close()
        }
    }

    private fun yuv420ToJpeg(image: Image): ByteArray? {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val numPixels = width * height
        val nv21 = ByteArray(numPixels + (numPixels / 2))

        var id = 0

        // Y plane
        val yRowStride = yPlane.rowStride
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, numPixels)
            id = numPixels
        } else {
            var yOffset = 0
            for (i in 0 until height) {
                yBuffer.position(yOffset)
                yBuffer.get(nv21, id, width)
                id += width
                yOffset += yRowStride
            }
        }

        // UV planes
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val uvWidth = width / 2
        val uvHeight = height / 2

        val uRaw = ByteArray(uBuffer.remaining())
        val vRaw = ByteArray(vBuffer.remaining())
        uBuffer.get(uRaw)
        vBuffer.get(vRaw)

        var uvPos = numPixels
        for (row in 0 until uvHeight) {
            val rowStart = row * uvRowStride
            for (col in 0 until uvWidth) {
                val index = rowStart + col * uvPixelStride
                nv21[uvPos++] = vRaw.getOrNull(index) ?: 0
                nv21[uvPos++] = uRaw.getOrNull(index) ?: 0
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 75, out)

        val rawJpeg = out.toByteArray()
        if (!isFrontCamera) return rawJpeg

        // Mirror front camera horizontally for correct natural orientation
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawJpeg, 0, rawJpeg.size) ?: return rawJpeg
            val matrix = Matrix().apply { postScale(-1f, 1f) }
            val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val mirroredOut = ByteArrayOutputStream()
            mirrored.compress(Bitmap.CompressFormat.JPEG, 75, mirroredOut)
            mirroredOut.toByteArray()
        } catch (e: Exception) {
            rawJpeg
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraVisionThread").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }
}
