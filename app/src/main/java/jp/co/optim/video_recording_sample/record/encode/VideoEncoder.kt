package jp.co.optim.video_recording_sample.record.encode

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.entity.MediaType
import jp.co.optim.video_recording_sample.entity.VideoData

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

    override val mediaCodec = let {
        val format = MediaFormat.createVideoFormat(
            it.mediaType.mimeType, it.videoData.frameSize.width, it.videoData.frameSize.height
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

    // メディアコーディックに紐づく Surface
    private val surface = mediaCodec.createInputSurface()

    override fun release() {
        surface.release()
        super.release()
    }

    override fun enqueueEndStream() {
        synchronized(syncEnqueue) {
            mediaCodec.signalEndOfInputStream()
        }
    }

    /**
     * 録画用の画像をエンキューする.
     * @param bitmap 画像
     */
    @WorkerThread
    fun enqueueVideoBitmap(bitmap: Bitmap) {
        synchronized(syncEnqueue) {
            // エンドストリームが呼び出されたか、エンコード処理中でなければ何もしない.
            if (isCalledEndStream || !isEncoding) return

            val canvas = surface.lockCanvas(Rect(0, 0, bitmap.width, bitmap.height))
            canvas.drawBitmap(bitmap, 0f, 0f, Paint())
            surface.unlockCanvasAndPost(canvas)
        }
    }
}