package jp.co.optim.video_recording_sample.read

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.record.entity.AudioData
import kotlin.concurrent.thread
import kotlin.math.max

class MicAudioReader(
    audioData: AudioData,
    private val onReadBytesListener: (bytes: ByteArray) -> Unit?
) {

    private val bufferSize = max(
        audioData.samplingRate / 10,
        AudioRecord.getMinBufferSize(
            audioData.samplingRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    ) * audioData.bytesPerSample

    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        audioData.samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    private var isRecording = false

    fun start() {
        isRecording = true
        audioRecord.startRecording()
        // スレッド開始
        thread { read() }
    }

    fun stop() {
        isRecording = false
    }

    @WorkerThread
    private fun read() {
        logI("Start reading.")
        while (isRecording) {
            val bytes = ByteArray(bufferSize)
            audioRecord.read(bytes, 0, bufferSize)
            onReadBytesListener(bytes)
        }
        logI("End reading.")
        audioRecord.release()
    }
}