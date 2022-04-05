package jp.co.optim.video_recording_sample.read

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.logD
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.record.entity.AudioData
import kotlin.concurrent.thread
import kotlin.math.max

class MicAudioReader {

    companion object {
        // バッファーサイズの閾値.
        // バッファーの読み込みサイズが閾値以上だった場合、分割してからコールバックを呼び出す.
        // AudioRecord 読み込み時のバッファーサイズが大きすぎると
        // AudioEncoder 書き込み時に BufferOverflowException が発生するため.
        private const val BUFFER_SIZE_LIMIT = 1024
    }

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
        inBufferSize: Int,
        audioRecord: AudioRecord,
        listener: (bytes: ByteArray) -> Unit?
    ) {
        val outBufferSize = estimateBufferSize(inBufferSize)
        val inBytes = ByteArray(inBufferSize)
        val outBytes = ByteArray(outBufferSize)
        logI("Start reading.")
        logD("inBufferSize: $inBufferSize, outBufferSize: $outBufferSize")
        while (isReading) {
            audioRecord.read(inBytes, 0, inBufferSize)
            for (index in 0 until inBufferSize step outBufferSize) {
                inBytes.copyInto(outBytes, 0, index, index + outBufferSize)
                listener(outBytes)
            }
        }
        logI("End reading.")
        audioRecord.release()
    }

    private fun estimateBufferSize(orgBufferSize: Int): Int =
        if (orgBufferSize <= BUFFER_SIZE_LIMIT) orgBufferSize
        else estimateBufferSize(orgBufferSize / 2)
}