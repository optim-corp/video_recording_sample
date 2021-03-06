package jp.co.optim.video_recording_sample.record.encode

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.ktx.logE
import jp.co.optim.video_recording_sample.ktx.logI
import jp.co.optim.video_recording_sample.record.entity.MediaType
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * 音声や動画などのメディアをエンコードするための抽象クラス
 * @param callback コールバック
 */
abstract class MediaEncoder(private val callback: Callback) {

    /**
     * エンコードのためのコールバック
     */
    interface Callback {
        /**
         * エンコード処理開始.
         * INFO_OUTPUT_FORMAT_CHANGED が呼ばれるとこのメソッドが呼び出される.
         * @param mediaType メディアタイプ
         * @param mediaFormat メディアフォーマット
         */
        @WorkerThread
        fun onStarted(mediaType: MediaType, mediaFormat: MediaFormat)

        /**
         * エンコードされたバッファーを書き出す.
         * @param trackId トラックID
         * @param buffer エンコード後のバッファー
         * @param bufferInfo バッファー情報
         */
        @WorkerThread
        fun onEncodedBuffer(
            trackId: Int,
            buffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo
        )

        /**
         * エンコード処理終了.
         * @param t 異常終了した場合の原因、正常終了の場合は null
         */
        @WorkerThread
        fun onFinished(t: Throwable?)
    }

    companion object {
        // デキューのタイムアウト時間 [us]
        private const val CODEC_DEQUEUE_TIMEOUT_US = 10 * 1000L
    }

    // メディアタイプ
    protected abstract val mediaType: MediaType

    // メディアコーデック
    protected abstract val mediaCodec: MediaCodec

    // トラックID
    var trackId: Int = -1

    // レスポンスタイムスタンプ [us]
    private var resTimeStampUs = -1L

    // 開始時間のタイムスタンプ [us]
    private var firstTimeStampUs = -1L

    // エンコードが利用可能か.
    // INFO_OUTPUT_FORMAT_CHANGED が呼ばれると true
    var isFormatChanged = false
    private set

    // エンコード処理中かどうか.
    // dequeueBuffer() のループが回っている間は true
    protected var isEncoding = false
    private set

    // エンドストリームが呼ばれたか.
    protected var isCalledEndStream = false

    // エンキュー非同期処理用のオブジェクト.
    protected val lockEnqueue = ReentrantLock()

    // デキュー排他制御用のオブジェクト.
    private val lockDequeue = ReentrantLock()

    /**
     * エンコードを開始する.
     */
    fun start() {
        if (isEncoding) {
            logI("Encode is already started.")
            return
        }
        logI("Start encoder.")
        try {
            // コーディック処理開始.
            mediaCodec.start()
            // デキュー処理開始. デキュー専用の別スレッドを立ち上げる.
            thread { dequeueBuffer() }
        } catch (e: Exception) {
            logE("Failed to start mediaCodec.")
            callback.onFinished(e)
        }
    }

    /**
     * エンコードを終了する.
     */
    fun stop() {
        if (!isEncoding) {
            logI("Encode is already stopped.")
            return
        }
        logI("Stop encoder.")
        enqueueEndStream()
    }

    /**
     * リリース処理.
     */
    protected open fun release(t: Throwable?) {
        logI("Release encoder.")
        try {
            mediaCodec.stop()
            mediaCodec.release()
        } catch (e: IllegalStateException) {
            logE("MediaCodec is already released.")
        } finally {
            trackId = -1
            resTimeStampUs = -1L
            firstTimeStampUs = -1L
            isFormatChanged = false
            isEncoding = false
            isCalledEndStream = false

            callback.onFinished(t)
        }
    }

    /**
     * エンドストリームをエンキューする.
     */
    @WorkerThread
    protected abstract fun enqueueEndStream()

    /**
     * バッファーをデキューする.
     */
    @WorkerThread
    private fun dequeueBuffer() {
        logI("Start dequeue.")
        isEncoding = true
        var t: Throwable? = null
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isEncoding) {
                lockDequeue.withLock {
                    val indexOrStatus =
                        mediaCodec.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US)

                    if (indexOrStatus < 0) {
                        // 0未満の場合はステータスチェック.
                        dequeueBufferStatus(indexOrStatus)
                    } else {
                        dequeueBufferIndex(indexOrStatus, bufferInfo)
                        // ストリーム終了フラグの場合は終了する.
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            logI("Flag is BUFFER_FLAG_END_OF_STREAM")
                            isEncoding = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logE("Failed to dequeue buffer.")
            t = e
        } finally {
            logI("End dequeue. resTimeStampUs: $resTimeStampUs")
            release(t)
        }
    }

    /**
     * dequeueOutputBuffer がステータスだった場合のインナー処理.
     */
    @WorkerThread
    private fun dequeueBufferStatus(status: Int) {
        when (status) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                logI("flag is INFO_OUTPUT_FORMAT_CHANGED")
                isFormatChanged = true
                callback.onStarted(mediaType, mediaCodec.outputFormat)
            }
            else -> {}
        }
    }

    /**
     * dequeueOutputBuffer がインデックスだった場合のインナー処理.
     */
    @WorkerThread
    private fun dequeueBufferIndex(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        logI("dequeueBufferIndex: $index")
        try {
            val buffer = mediaCodec.getOutputBuffer(index) ?: return
            if (bufferInfo.size < 0) {
                return
            }
            // コーデックの設定情報が出力されたときのフラグ. エンコード結果ではないためスキップする.
            // このフラグはエンコード開始時の最初の１回と途中で設定が変わった場合に出力される.
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                logI("flag is BUFFER_FLAG_CODEC_CONFIG")
                return
            }

            if (firstTimeStampUs < 0L) {
                // タイムスタンプ開始時間を記録する.
                firstTimeStampUs = bufferInfo.presentationTimeUs
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                // 終了のときは値がバグっている可能性があるので、最後のタイムスタンプ時間に +1 しておく.
                bufferInfo.presentationTimeUs = resTimeStampUs + 1L
            } else {
                // 開始時間を引いてタイムスタンプを再設定する.
                val timestamp = bufferInfo.presentationTimeUs - firstTimeStampUs
                if (timestamp < resTimeStampUs) {
                    // 前のタイムスタンプよりも進んでいないとおかしい.
                    logE("Invalid timestamp. before: $timestamp, after: $resTimeStampUs")
                    return
                }
                resTimeStampUs = bufferInfo.presentationTimeUs - firstTimeStampUs
                bufferInfo.presentationTimeUs = resTimeStampUs
            }
            logI("BufferSize: ${bufferInfo.size} TimeStamp: ${bufferInfo.presentationTimeUs}")

            // バッファーに書き込みを行う.
            val copyBuffer = ByteBuffer.allocate(buffer.capacity()).put(buffer)
            callback.onEncodedBuffer(trackId, copyBuffer, bufferInfo)
        } finally {
            mediaCodec.releaseOutputBuffer(index, false)
        }
    }
}