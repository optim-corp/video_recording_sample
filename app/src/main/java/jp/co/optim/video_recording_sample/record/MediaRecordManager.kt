package jp.co.optim.video_recording_sample.record

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.*
import jp.co.optim.video_recording_sample.record.encode.AudioEncoder
import jp.co.optim.video_recording_sample.record.encode.MediaEncoder
import jp.co.optim.video_recording_sample.record.encode.VideoEncoder
import jp.co.optim.video_recording_sample.record.entity.MediaType
import jp.co.optim.video_recording_sample.record.entity.RecordData
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * レコード処理するためのマネージャークラス
 * 音声録音や録画することが可能でファイルに出力する.
 */
class MediaRecordManager : MediaEncoder.Callback {

    /**
     * ステータス通知ためのコールバック
     */
    interface Callback {
        /**
         * レコード処理開始.
         */
        @MainThread
        fun onStarted()

        /**
         * レコード処理終了.
         * @param t 異常終了した場合の原因、正常終了の場合は null
         */
        @MainThread
        fun onFinished(t: Throwable?)
    }

    private var callback: Callback? = null
    private var mainHandler = Handler(Looper.getMainLooper())

    private var mediaMuxer: MediaMuxer? = null
    private var audioEncoder: AudioEncoder? = null
    private var videoEncoder: VideoEncoder? = null

    private var isPrepared = false
    private var isStarted = false
    private var isRecording = false

    // エンコードが利用可能か.
    private val isEncodeAvailable: Boolean get() =
        and(audioEncoder?.isFormatChanged, videoEncoder?.isFormatChanged)

    // エンコード中か.
    private val isEncodeRunning: Boolean get() =
        or(audioEncoder?.isFormatChanged, videoEncoder?.isFormatChanged)

    // Muxer非同期処理用のオブジェクト.
    private val lockMuxer = ReentrantLock()

    /**
     * データを元にパラメータの設定準備を行う.
     * @param recordData レコード情報に関するデータ
     * @param callback ステータス通知ためのコールバック
     */
    @WorkerThread
    fun prepare(recordData: RecordData, callback: Callback?) {
        try {
            // Muxer定義
            mediaMuxer = MediaMuxer(
                recordData.recFile.canonicalPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            // AudioEncoder定義
            if (recordData.audioData.isAvailable()) {
                audioEncoder = AudioEncoder(recordData.audioData, this)
            }
            // VideoEncoder定義
            if (recordData.videoData.isAvailable()) {
                videoEncoder = VideoEncoder(recordData.videoData, this)
            }
            isPrepared = true
            this.callback = callback
        } catch (e: Exception) {
            logE("Failed to prepare recording.")
        }
    }

    /**
     * レコード開始する.
     */
    fun start() {
        if (!isPrepared) {
            logW("Recording is not prepared.")
            return
        }
        if (isStarted || isRecording) {
            logW("Recording is already started.")
            return
        }
        logI("Start recording.")
        isStarted = true
        isRecording = true
        audioEncoder?.start()
        videoEncoder?.start()
    }

    /**
     * レコード停止する.
     */
    fun stop() {
        if (!isRecording) {
            logW("Recording is already stopped.")
            return
        }
        logI("Stop recording.")
        isRecording = false
        audioEncoder?.stop()
        videoEncoder?.stop()
    }

    /**
     * 音声バッファーを入力する.
     * @param bytes 音声バッファーの情報が含まれたバイト配列
     */
    @WorkerThread
    fun inputAudioBytes(bytes: ByteArray) {
        if (isRecording) {
            audioEncoder?.enqueueAudioBytes(bytes)
        }
    }

    /**
     * 録画用の画像を入力する.
     * @param bitmap 画像
     */
    @WorkerThread
    fun inputVideoBitmap(bitmap: Bitmap) {
        if (isRecording) {
            videoEncoder?.enqueueVideoBitmap(bitmap)
        }
    }

    override fun onStarted(mediaType: MediaType, mediaFormat: MediaFormat) {
        lockMuxer.withLock {
            when (mediaType) {
                MediaType.AUDIO -> {
                    audioEncoder?.trackId = mediaMuxer?.addTrack(mediaFormat) ?: -1
                    logI("Audio trackId: ${audioEncoder?.trackId}")
                }
                MediaType.VIDEO -> {
                    videoEncoder?.trackId = mediaMuxer?.addTrack(mediaFormat) ?: -1
                    logI("Video trackId: ${videoEncoder?.trackId}")
                }
            }
            // フォーマットをトラックに追加し終わったら Muxer を開始できる.
            if (isEncodeAvailable) {
                logI("Start mediaMuxer.")
                try {
                    mediaMuxer?.start()
                    callback?.let {
                        mainHandler.post { it.onStarted() }
                    }
                } catch (e: IllegalStateException) {
                    logE("Failed to start mediaMuxer.")
                }
            }
        }
    }

    override fun onEncodedBuffer(
        trackId: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        lockMuxer.withLock {
            if (isEncodeAvailable) {
                try {
                    logI("Write sample data. trackId: $trackId")
                    mediaMuxer?.writeSampleData(trackId, buffer, bufferInfo)
                } catch (e: Exception) {
                    logE("Failed to write sample data.")
                }
            }
        }
    }

    override fun onFinished(t: Throwable?) {
        lockMuxer.withLock {
            // Audio もしくは Video のエンコード処理が終わっていないかチェック.
            if (isEncodeRunning) {
                logI("Encoder is still running.")
                return
            }
            // Muxer がリリース済かチェック.
            if (mediaMuxer == null) {
                logI("Muxer is already released.")
                return
            }

            // Muxer を停止＆リリースする.
            logI("Stop mediaMuxer.")
            try {
                mediaMuxer?.stop()
            } catch (e: IllegalStateException) {
                logE("Failed to stop mediaMuxer.")
            } finally {
                mediaMuxer?.release()

                // リセット.
                mediaMuxer = null
                audioEncoder = null
                videoEncoder = null
                isPrepared = false
                isStarted = false
                isRecording = false

                logI("Recording is completed!")
                callback?.let {
                    mainHandler.post { it.onFinished(t) }
                }
                callback = null
            }
        }
    }
}