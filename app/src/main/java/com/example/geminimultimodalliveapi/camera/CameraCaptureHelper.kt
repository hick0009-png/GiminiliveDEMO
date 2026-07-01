package com.example.geminimultimodalliveapi.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.*

class CameraCaptureHelper(
    private val context: Context,
    private val textureView: TextureView,
    private val onFrameCaptured: (ByteArray) -> Unit,
    var onJitterReported: ((Long) -> Unit)? = null
) {
    var onPreviewStarted: (() -> Unit)? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private lateinit var cameraId: String
    private lateinit var previewSize: Size

    private var captureJob: Job? = null
    var isCameraActive = false
        private set

    var sendIntervalMs: Long = 3000 // Default to medium

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        if (com.example.geminimultimodalliveapi.session.SessionStateHolder.state.value is com.example.geminimultimodalliveapi.session.SessionState.Active) {
            onFrameCaptured(bytes)
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
            Log.e("CameraCaptureHelper", "Camera error: $error")
        }
    }

    @SuppressLint("MissingPermission")
    fun startPreview(scope: CoroutineScope) {
        if (isCameraActive) return
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            previewSize = map.getOutputSizes(SurfaceTexture::class.java)[0]

            imageReader = ImageReader.newInstance(
                480, 480,
                ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener(imageAvailableListener, cameraHandler)
            }

            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            isCameraActive = true
            startPeriodicImageCapture(scope)
        } catch (e: Exception) {
            Log.e("CameraCaptureHelper", "Error opening camera", e)
        }
    }

    fun stopPreview() {
        isCameraActive = false
        stopPeriodicImageCapture()
        closeCamera()
    }

    fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (!::previewSize.isInitialized || viewWidth == 0 || viewHeight == 0) return
        
        val deviceRotation = (context as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val matrix = android.graphics.Matrix()
        val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        
        // Swap dimensions if portrait orientation
        val isPortrait = deviceRotation == Surface.ROTATION_0 || deviceRotation == Surface.ROTATION_180
        val previewWidth = if (isPortrait) previewSize.height.toFloat() else previewSize.width.toFloat()
        val previewHeight = if (isPortrait) previewSize.width.toFloat() else previewSize.height.toFloat()
        
        val bufferRect = android.graphics.RectF(0f, 0f, previewWidth, previewHeight)
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL)
        
        // Calculate centerCrop scale factor
        val scale = Math.max(
            viewHeight.toFloat() / previewHeight,
            viewWidth.toFloat() / previewWidth
        )
        matrix.postScale(scale, scale, centerX, centerY)
        
        if (deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270) {
            matrix.postRotate((90 * (deviceRotation - 2)).toFloat(), centerX, centerY)
        } else if (deviceRotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY)
        }
        
        // Apply transform to UI Thread
        Handler(Looper.getMainLooper()).post {
            textureView.setTransform(matrix)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val surfaceTexture = textureView.surfaceTexture?.apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            } ?: return
            
            configureTransform(textureView.width, textureView.height)
            val previewSurface = Surface(surfaceTexture)

            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(previewSurface)
            }

            cameraDevice?.createCaptureSession(
                listOf(previewSurface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        updatePreview()
                        onPreviewStarted?.invoke()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CameraCaptureHelper", "Configuration failed")
                    }
                }, cameraHandler
            )
        } catch (e: Exception) {
            Log.e("CameraCaptureHelper", "Error creating preview session", e)
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) return
        captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSession?.setRepeatingRequest(
                captureRequestBuilder?.build()!!,
                null, cameraHandler
            )
        } catch (e: Exception) {
            Log.e("CameraCaptureHelper", "Error starting preview repeat request", e)
        }
    }

    private fun startPeriodicImageCapture(scope: CoroutineScope) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            while (isCameraActive) {
                val currentInterval = sendIntervalMs
                val loopStart = System.currentTimeMillis()
                delay(currentInterval)
                
                val actualElapsed = System.currentTimeMillis() - loopStart
                val jitter = actualElapsed - currentInterval
                onJitterReported?.invoke(jitter)

                try {
                    val session = cameraCaptureSession
                    val device = cameraDevice
                    if (session != null && device != null && isCameraActive) {
                        val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(imageReader!!.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }
                        session.capture(captureBuilder.build(), null, cameraHandler)
                    }
                } catch (e: Exception) {
                    Log.e("CameraCaptureHelper", "Error capturing single frame", e)
                }
            }
        }
    }

    private fun stopPeriodicImageCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    fun captureSingleFrame() {
        val session = cameraCaptureSession
        val device = cameraDevice
        if (session != null && device != null && isCameraActive) {
            try {
                val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(imageReader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                }
                session.capture(captureBuilder.build(), null, cameraHandler)
            } catch (e: Exception) {
                Log.e("CameraCaptureHelper", "Error capturing explicit single frame", e)
            }
        }
    }

    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    fun onDestroy() {
        cameraThread.quitSafely()
    }
}
