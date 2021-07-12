package jp.co.optim.video_recording_sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.co.optim.video_recording_sample.databinding.ActivityMainBinding
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.read.MicAudioReader
import jp.co.optim.video_recording_sample.record.MediaRecordManager
import jp.co.optim.video_recording_sample.record.entity.AudioData
import jp.co.optim.video_recording_sample.record.entity.RecordData

class MainActivity : AppCompatActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private lateinit var binding: ActivityMainBinding

    private var micAudioReader: MicAudioReader? = null

    private var recordManager: MediaRecordManager? = null

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            for (entry in it.entries) {
                if (!entry.value) {
                    logI("Runtime permissions are denied: ${entry.key}")
                    finish()
                    return@registerForActivityResult
                }
            }
            logI("All runtime permissions are granted.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStart.setOnClickListener {
            logI("Clicked start button.")
            binding.buttonStart.isEnabled = false
            binding.buttonStop.isEnabled = true
            start()
        }
        binding.buttonStop.setOnClickListener {
            logI("Clicked stop button.")
            binding.buttonStart.isEnabled = true
            binding.buttonStop.isEnabled = false
            stop()
        }

        requestMultiplePermissions.launch(runtimePermissions)
    }

    private fun start() {
        if (micAudioReader != null) {
            logI("Already started.")
            return
        }
        val dir = getExternalFilesDir(null)
            ?: throw IllegalArgumentException("Cannot get parent dir.")
        val recordData = RecordData.newAudioRecordData(dir, AudioData())
        micAudioReader = MicAudioReader(recordData.audioData) {
            recordManager?.inputAudioBytes(it)
        }
        recordManager = MediaRecordManager()

        logI("Start recording.")
        micAudioReader?.start()
        recordManager?.prepare(recordData)
        recordManager?.start()
    }

    private fun stop() {
        if (micAudioReader == null) {
            logI("Already stopped.")
            return
        }
        logI("Stop recording.")
        recordManager?.stop()
        recordManager = null
        micAudioReader?.stop()
        micAudioReader = null
    }
}