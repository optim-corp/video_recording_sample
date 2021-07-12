package jp.co.optim.video_recording_sample.record.encode

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.record.entity.AudioData
import jp.co.optim.video_recording_sample.record.entity.MediaType
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

    @WorkerThread
    fun enqueueAudioBytes(bytes: ByteArray) {
        // エンコード処理中でなければ何もしない.
        if (!isEncoding) return

        // バイト配列からバッファー変換してキューに詰める.
        enqueueBuffer(ByteBuffer.wrap(bytes), reqTimeStampMicros)

        // バッファーから算出された時間をタイムスタンプに追加.
        val sample = bytes.size / audioData.bytesPerSample
        val timeIntervalMicros = 1000L * 1000L * sample / audioData.samplingRate
        reqTimeStampMicros += timeIntervalMicros
    }
}