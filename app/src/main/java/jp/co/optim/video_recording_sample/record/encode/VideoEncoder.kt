package jp.co.optim.video_recording_sample.record.encode

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.record.entity.MediaType
import jp.co.optim.video_recording_sample.record.entity.VideoData
import java.nio.ByteBuffer

class VideoEncoder(
    private val videoData: VideoData,
    callback: Callback
): MediaEncoder(callback) {

    override val mediaType: MediaType = MediaType.VIDEO

    override val mediaCodec = let {
        val format = MediaFormat.createVideoFormat(
            it.mediaType.mimeType, it.videoData.width, it.videoData.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, it.videoData.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, it.videoData.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, it.videoData.frameInterval)
        }
        val codec = MediaCodec.createEncoderByType(it.mediaType.mimeType).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        codec
    }

    @WorkerThread
    fun enqueueVideoBitmap(bitmap: Bitmap) {
        // エンコード処理中でなければ何もしない.
        if (!isEncoding) return

        // ビットマップからバッファー変換してキューに詰める.
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        enqueueBuffer(buffer, reqTimeStampMicros)

        // フレームレートから算出された時間をタイムスタンプに追加.
        val timeIntervalMicros = 1000L * 1000L / videoData.frameRate
        reqTimeStampMicros += timeIntervalMicros
    }
}