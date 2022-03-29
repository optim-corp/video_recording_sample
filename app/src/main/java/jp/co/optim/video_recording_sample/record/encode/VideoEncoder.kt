package jp.co.optim.video_recording_sample.record.encode

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.record.entity.MediaType
import jp.co.optim.video_recording_sample.record.entity.VideoData
import kotlin.concurrent.withLock

/**
 * 動画をエンコードするためのクラス
 * @param videoData ビデオデータ
 * @param callback コールバック
 */
class VideoEncoder(
    private val videoData: VideoData,
    callback: Callback
): MediaEncoder(callback) {

    override val mediaType: MediaType = MediaType.VIDEO

    override val mediaCodec = run {
        val format = MediaFormat.createVideoFormat(
            mediaType.mimeType, videoData.frameSize.width, videoData.frameSize.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, videoData.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, videoData.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, videoData.frameInterval)
        }
        val codec = MediaCodec.createEncoderByType(mediaType.mimeType).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        codec
    }

    // メディアコーディックに紐づく Surface
    private val surface = mediaCodec.createInputSurface()

    override fun release(t: Throwable?) {
        surface.release()
        super.release(t)
    }

    override fun enqueueEndStream() {
        lockEnqueue.withLock {
            logI("Enqueue end stream.")
            mediaCodec.signalEndOfInputStream()
        }
    }

    /**
     * 録画用の画像をエンキューする.
     * @param bitmap 画像
     */
    @WorkerThread
    fun enqueueVideoBitmap(bitmap: Bitmap) {
        lockEnqueue.withLock {
            // エンドストリームが呼び出されたか、エンコード処理中でなければ何もしない.
            if (isCalledEndStream || !isEncoding) return

            val canvas = surface.lockCanvas(Rect(0, 0, bitmap.width, bitmap.height))
            canvas.drawBitmap(bitmap, 0f, 0f, Paint())
            surface.unlockCanvasAndPost(canvas)
        }
    }
}