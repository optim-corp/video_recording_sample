package jp.co.optim.video_recording_sample.read

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.record.entity.AudioData
import kotlin.concurrent.thread
import kotlin.math.max

class MicAudioReader {

    private var isReading = false

    fun startReading(
        audioData: AudioData,
        listener: (bytes: ByteArray) -> Unit?
    ) {
        isReading = true

        val bufferSize = max(
            audioData.samplingRate / 10,
            AudioRecord.getMinBufferSize(
                audioData.samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        ) * audioData.bytesPerSample

        @SuppressLint("MissingPermission")
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            audioData.samplingRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord.startRecording()

        // スレッド開始
        thread { read(bufferSize, audioRecord, listener) }
    }

    fun stopReading() {
        isReading = false
    }

    @WorkerThread
    private fun read(
        bufferSize: Int,
        audioRecord: AudioRecord,
        listener: (bytes: ByteArray) -> Unit?
    ) {
        logI("Start reading.")
        while (isReading) {
            val bytes = ByteArray(bufferSize)
            audioRecord.read(bytes, 0, bufferSize)
            listener(bytes)
        }
        logI("End reading.")
        audioRecord.release()
    }
}