package jp.co.optim.video_recording_sample

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.snackbar.Snackbar
import jp.co.optim.video_recording_sample.databinding.ActivityMainBinding
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.read.CameraCaptureRenderer
import jp.co.optim.video_recording_sample.read.MicAudioReader
import jp.co.optim.video_recording_sample.record.MediaRecordManager
import jp.co.optim.video_recording_sample.record.entity.AudioData
import jp.co.optim.video_recording_sample.record.entity.RecordData
import jp.co.optim.video_recording_sample.record.entity.VideoData
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private lateinit var binding: ActivityMainBinding

    private val recordManager = MediaRecordManager()

    private val audioReader = MicAudioReader()

    private val captureRenderer = CameraCaptureRenderer(this)

    private var currentResolution: ScreenResolution = ScreenResolution.MIC_ONLY

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
            logI("All runtime permissions are granted.")
            openCamera(currentResolution)
        }

    private val recordCallback = object : MediaRecordManager.Callback {
        override fun onStarted() {
            binding.mtbRecord.isEnabled = true
            binding.mtbRecord.icon = AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_stop_circle)
        }
        override fun onFinished(t: Throwable?) {
            binding.mtbRecord.isEnabled = true
            binding.mtbRecord.icon = AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_circle)
            binding.fabSettings.show()

            Snackbar.make(
                binding.root,
                if (t == null) R.string.finished_record_normally_toast_message
                else R.string.finished_record_abnormally_toast_message,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mtbRecord.setOnClickListener {
            binding.mtbRecord.isEnabled = false
            binding.fabSettings.hide()

            if (!recordManager.isRecording) {
                // Start recording.
                if (currentResolution == ScreenResolution.MIC_ONLY) {
                    startAudio()
                } else {
                    startVideo()
                }
            } else {
                // Stop recording.
                stopAll()
            }
        }
        binding.fabSettings.setOnClickListener {
            if (binding.radioGroupFabMenu.visibility == View.VISIBLE) {
                binding.mtbRecord.isEnabled = true
                binding.radioGroupFabMenu.visibility = View.GONE
                val resolution = when (binding.radioGroupFabMenu.checkedRadioButtonId) {
                    R.id.mrb_sd -> ScreenResolution.SD
                    R.id.mrb_hd -> ScreenResolution.HD
                    R.id.mrb_full_hd -> ScreenResolution.FULL_HD
                    else -> ScreenResolution.MIC_ONLY
                }
                if (currentResolution == resolution) return@setOnClickListener

                currentResolution = resolution
                val resId = when (resolution) {
                    ScreenResolution.SD -> R.string.label_sd
                    ScreenResolution.HD -> R.string.label_hd
                    ScreenResolution.FULL_HD -> R.string.label_full_hd
                    else -> R.string.label_mic_only
                }
                binding.textViewResolution.setText(resId)
                closeCamera()
                openCamera(resolution)
            } else {
                binding.mtbRecord.isEnabled = false
                binding.radioGroupFabMenu.visibility = View.VISIBLE
            }
        }
        binding.radioGroupFabMenu.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        if (isCheckedPermissions) {
            openCamera(currentResolution)
        } else {
            logI("Check runtime permissions.")
            requestMultiplePermissions.launch(runtimePermissions)
        }
    }

    override fun onPause() {
        super.onPause()

        closeCamera()
    }

    private fun openCamera(resolution: ScreenResolution) {
        if (resolution == ScreenResolution.MIC_ONLY) {
            logI("No needed to open camera.")
            return
        }

        logI("Open camera. ScreenResolution: $resolution")
        binding.textureView.visibility = View.VISIBLE
        captureRenderer.openCamera(binding.textureView, resolution.frameSize)
    }

    private fun closeCamera() {
        logI("Close camera.")
        binding.textureView.visibility = View.GONE
        captureRenderer.closeCamera()
    }

    private fun startAudio() {
        val dir = getExternalFilesDir(null)
            ?: throw IllegalArgumentException("Cannot get parent dir.")
        val recordData = RecordData.newAudioRecordData(dir, AudioData())

        logI("Start audio recording.")
        thread {
            recordManager.prepare(recordData, recordCallback)
            recordManager.start()
        }
        audioReader.startReading(
            recordData.audioData.samplingRate,
            recordData.audioData.bytesPerSample
        ) {
            recordManager.inputAudioBytes(it)
        }
    }

    private fun startVideo() {
        val dir = getExternalFilesDir(null)
            ?: throw IllegalArgumentException("Cannot get parent dir.")
        val recordData = RecordData.newVideoRecordData(dir, AudioData(), VideoData(currentResolution.frameSize))

        logI("Start video recording.")
        thread {
            recordManager.prepare(recordData, recordCallback)
            recordManager.start()
        }
        audioReader.startReading(
            recordData.audioData.samplingRate,
            recordData.audioData.bytesPerSample
        ) {
            recordManager.inputAudioBytes(it)
        }
        captureRenderer.startRendering(recordData.videoData.frameRate) {
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