package jp.co.optim.video_recording_sample

import android.Manifest
import android.os.Bundle
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import jp.co.optim.video_recording_sample.databinding.ActivityMainBinding
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.read.CameraCaptureRenderer
import jp.co.optim.video_recording_sample.read.MicAudioReader
import jp.co.optim.video_recording_sample.record.MediaRecordManager
import jp.co.optim.video_recording_sample.record.entity.AudioData
import jp.co.optim.video_recording_sample.record.entity.RecordData
import jp.co.optim.video_recording_sample.record.entity.VideoData

class MainActivity : AppCompatActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private lateinit var binding: ActivityMainBinding

    private val recordManager = MediaRecordManager()

    private val audioReader = MicAudioReader()

    private val captureRenderer = CameraCaptureRenderer(this)

    private var frameSize = Size(480, 720)

    private var isCheckedPermissions = false

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            isCheckedPermissions = true
            for (entry in it.entries) {
                if (!entry.value) {
                    logI("Runtime permissions are denied: ${entry.key}")
                    finish()
                    return@registerForActivityResult
                }
            }
            logI("All runtime permissions are granted. Open camera.")
            captureRenderer.openCamera(binding.textureView, frameSize)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStartAudio.setOnClickListener {
            logI("Clicked start audio button.")
            binding.buttonStartAudio.isEnabled = false
            binding.buttonStartVideo.isEnabled = false
            binding.buttonStop.isEnabled = true
            startAudio()
        }
        binding.buttonStartVideo.setOnClickListener {
            logI("Clicked start video button.")
            binding.buttonStartAudio.isEnabled = false
            binding.buttonStartVideo.isEnabled = false
            binding.buttonStop.isEnabled = true
            startVideo()
        }
        binding.buttonStop.setOnClickListener {
            logI("Clicked stop button.")
            binding.buttonStartAudio.isEnabled = true
            binding.buttonStartVideo.isEnabled = true
            binding.buttonStop.isEnabled = false
            stopAll()
        }
    }

    override fun onResume() {
        super.onResume()

        if (isCheckedPermissions) {
            logI("Open camera.")
            captureRenderer.openCamera(binding.textureView, frameSize)
        } else {
            logI("Check runtime permissions.")
            requestMultiplePermissions.launch(runtimePermissions)
        }
    }

    override fun onPause() {
        super.onPause()

        logI("Close camera.")
        captureRenderer.closeCamera()
    }

    private fun startAudio() {
        val dir = getExternalFilesDir(null)
            ?: throw IllegalArgumentException("Cannot get parent dir.")
        val recordData = RecordData.newAudioRecordData(dir, AudioData())

        logI("Start audio recording.")
        recordManager.prepare(recordData)
        recordManager.start()

        audioReader.startReading(recordData.audioData) {
            recordManager.inputAudioBytes(it)
        }
    }

    private fun startVideo() {
        val dir = getExternalFilesDir(null)
            ?: throw IllegalArgumentException("Cannot get parent dir.")
        val recordData = RecordData.newVideoRecordData(dir, AudioData(), VideoData(frameSize))

        logI("Start video recording.")
        recordManager.prepare(recordData)
        recordManager.start()

        audioReader.startReading(recordData.audioData) {
            recordManager.inputAudioBytes(it)
        }
        captureRenderer.startRendering(recordData.videoData) {
            recordManager.inputVideoBitmap(it)
        }
    }

    private fun stopAll() {
        logI("Stop recording.")
        recordManager.stop()
        audioReader.stopReading()
        captureRenderer.stopRendering()
    }
}