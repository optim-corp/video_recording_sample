package jp.co.optim.video_recording_sample

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.co.optim.video_recording_sample.databinding.ActivityMainBinding
import jp.co.optim.video_recording_sample.entity.AudioData
import jp.co.optim.video_recording_sample.entity.RecordData
import jp.co.optim.video_recording_sample.entity.ScreenResolution
import jp.co.optim.video_recording_sample.entity.VideoData
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.extensions.logW
import jp.co.optim.video_recording_sample.read.CameraCaptureRenderer
import jp.co.optim.video_recording_sample.read.MicAudioReader
import jp.co.optim.video_recording_sample.record.MediaRecordManager


class MainActivity : AppCompatActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private lateinit var binding: ActivityMainBinding

    private val recordManager = MediaRecordManager()

    private val audioReader = MicAudioReader()

    private val captureRenderer = CameraCaptureRenderer(this)

    private var resolution: ScreenResolution = ScreenResolution.UNKNOWN

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
            logI("All runtime permissions are granted. Show dialog.")
            showSelectDialog()
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
            openCamera()
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

    private fun openCamera() {
        if (resolution == ScreenResolution.UNKNOWN) {
            logW("Resolution is unknown. Cannot open camera.")
            return
        }
        logI("Open camera. resolution: $resolution")
        captureRenderer.openCamera(binding.textureView, resolution.frameSize)
        binding.textViewResolution.text = "Screen Resolution: $resolution"
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
        val recordData = RecordData.newVideoRecordData(dir, AudioData(), VideoData(resolution.frameSize))

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

    private fun showSelectDialog() {
        val nameArray = ScreenResolution.values()
            .filter { it != ScreenResolution.UNKNOWN }
            .map { it.toString() }
            .toTypedArray()

        AlertDialog.Builder(this).apply {
            setTitle("Select Screen Resolution")
            setSingleChoiceItems(nameArray, 0) { dialog, item ->
                resolution = ScreenResolution.convertFromString(nameArray[item])
            }
            setPositiveButton("Select") { dialog, id ->
                if (resolution == ScreenResolution.UNKNOWN) resolution = ScreenResolution.SD
                openCamera()
            }
            setNegativeButton("Cancel") { dialog, id ->
                finish()
            }
            setCancelable(false)
        }.show()
    }
}