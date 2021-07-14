package jp.co.optim.video_recording_sample.read

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import jp.co.optim.video_recording_sample.entity.VideoData
import jp.co.optim.video_recording_sample.extensions.logD
import jp.co.optim.video_recording_sample.extensions.logI
import kotlin.concurrent.thread

class CameraCaptureRenderer(private val context: Context) {

    private var textureView: TextureView? = null

    private var cameraDevice: CameraDevice? = null

    private var cameraCaptureSession: CameraCaptureSession? = null

    private var captureRequest: CaptureRequest? = null

    private var frameSize = Size(0, 0)

    private var isOpened = false

    private var isRendering = false

    fun openCamera(view: TextureView, size: Size) {
        textureView = view
        frameSize = size

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalArgumentException("Camera permission is denied.")
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.openCamera(decideCameraId(cameraManager), deviceStateCallback, null)
    }

    fun closeCamera() {
        isOpened = false

        cameraCaptureSession?.close()
        cameraDevice?.close()

        textureView = null
        cameraCaptureSession = null
        cameraDevice = null
        captureRequest = null
    }

    fun startRendering(
        videoData: VideoData,
        listener: (bitmap: Bitmap) -> Unit?
    ) {
        isRendering = true
        thread { render(videoData.frameSize, videoData.frameRate, listener) }
    }

    fun stopRendering() {
        isRendering = false
    }

    private fun decideCameraId(cameraManager: CameraManager): String {
        val cameraIds = cameraManager.cameraIdList
        var frontCameraId: String? = null
        var backCameraId: String? = null
        for (cameraId in cameraIds) {
            when (cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = frontCameraId ?: cameraId
                CameraCharacteristics.LENS_FACING_BACK -> backCameraId = backCameraId ?: cameraId
            }
        }
        return backCameraId ?: frontCameraId
        ?: throw IllegalArgumentException("There are no cameras.")
    }

    private fun createCameraCaptureSession() {
        logD("createCameraCaptureSession")
        val texture = textureView?.surfaceTexture
            ?: throw IllegalArgumentException("Cannot get surfaceTexture.")
        texture.setDefaultBufferSize(frameSize.width, frameSize.height)

        val surface = Surface(texture)

        captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
            addTarget(surface)
        }?.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cameraDevice?.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(surface).map { OutputConfiguration(it) },
                    context.mainExecutor,
                    sessionStateCallback
                )
            )
        } else {
            cameraDevice?.createCaptureSession(
                listOf(surface), sessionStateCallback, null)
        }
    }

    @WorkerThread
    private fun render(
        size: Size,
        frameRate: Int,
        listener: (bitmap: Bitmap) -> Unit?
    ) {
        val intervalMillis = 1000L / frameRate
        logI("Start rendering. timeInterval: $intervalMillis")
        var currentBitmap: Bitmap? = null
        while (isRendering) {
            val newBitmap = if (isOpened) textureView?.getBitmap(size.width, size.height) else null
            if (newBitmap != null) {
                logI("New Bitmap")
                listener(newBitmap)
                if (currentBitmap != null && !currentBitmap.isRecycled) {
                    currentBitmap.recycle()
                }
                currentBitmap = newBitmap
            } else {
                logI("Current Bitmap")
                if (currentBitmap != null && !currentBitmap.isRecycled) {
                    listener(currentBitmap)
                }
            }
            try {
                Thread.sleep(intervalMillis)
            } catch (e: InterruptedException) {
                // Ignored.
            }
        }
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            currentBitmap.recycle()
        }
        logI("End rendering.")
    }

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createCameraCaptureSession()
        }
        override fun onDisconnected(device: CameraDevice) {
            device.close()
            cameraDevice = null
            isOpened = false
        }
        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            cameraDevice = null
            isOpened = false
        }
    }

    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSession = session
            cameraCaptureSession?.setRepeatingRequest(captureRequest!!, null, null)
            isOpened = true
        }
        override fun onConfigureFailed(session: CameraCaptureSession) {
            isOpened = false
        }
    }
}