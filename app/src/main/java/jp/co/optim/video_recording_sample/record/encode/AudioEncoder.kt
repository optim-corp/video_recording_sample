package jp.co.optim.video_recording_sample.record.encode

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.entity.AudioData
import jp.co.optim.video_recording_sample.entity.MediaType
import java.nio.ByteBuffer

class AudioEncoder(
    private val audioData: AudioData,
    callback: Callback
): MediaEncoder(callback) {

    override val mediaType: MediaType = MediaType.AUDIO

    override val mediaCodec = let {
        val format = MediaFormat.createAudioFormat(
                it.mediaType.mimeType, it.audioData.samplingRate, 1
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
                setInteger(MediaFormat.KEY_BIT_RATE, it.audioData.bitRate)
            }
        val codec = MediaCodec.createEncoderByType(it.mediaType.mimeType).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        codec
    }

    private var reqTimeStampUs = 0L

    override fun release() {
        reqTimeStampUs = 0L
        super.release()
    }

    override fun enqueueEndStream() {
        synchronized(syncEnqueue) {
            val index = dequeueInputBuffer(true)
            mediaCodec.queueInputBuffer(
                index!!, 0, 0, reqTimeStampUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            isCalledEndStream = true
        }
    }

    @WorkerThread
    fun enqueueAudioBytes(bytes: ByteArray) {
        synchronized(syncEnqueue) {
            // エンドストリームが呼び出されたか、エンコード処理中でなければ何もしない.
            if (isCalledEndStream || !isEncoding) return

            // バッファーから算出された時間をタイムスタンプに追加.
            val sample = bytes.size / audioData.bytesPerSample
            val timeIntervalMicros = 1000L * 1000L * sample / audioData.samplingRate
            reqTimeStampUs += timeIntervalMicros

            // バイト配列からバッファー変換してキューに詰める.
            val buffer = ByteBuffer.wrap(bytes)
            val index = dequeueInputBuffer() ?: return
            val inputBuffer = mediaCodec.getInputBuffer(index) ?: return
            inputBuffer.put(buffer)
            mediaCodec.queueInputBuffer(index, 0, buffer.capacity(), reqTimeStampUs, 0)
        }
    }

    @WorkerThread
    private fun dequeueInputBuffer(neverGiveUp: Boolean = false): Int? {
        var retryCount = 0
        while (retryCount < ENCODE_TRY_TIMES) {
            val index = mediaCodec.dequeueInputBuffer(CODEC_DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                when (index) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!neverGiveUp) retryCount++
                    }
                    else -> {}
                }
            } else {
                return index
            }
        }
        return null
    }
}