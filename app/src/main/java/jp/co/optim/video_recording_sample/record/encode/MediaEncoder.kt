package jp.co.optim.video_recording_sample.record.encode

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.entity.MediaType
import jp.co.optim.video_recording_sample.extensions.logE
import jp.co.optim.video_recording_sample.extensions.logI
import java.nio.ByteBuffer
import kotlin.concurrent.thread

abstract class MediaEncoder(private val callback: Callback) {

    interface Callback {

        fun onStarted(mediaType: MediaType, mediaFormat: MediaFormat)

        fun onEncodedBuffer(
            trackId: Int,
            buffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo
        )

        fun onFinished()
    }

    companion object {
        const val CODEC_DEQUEUE_TIMEOUT_US = 10 * 1000L
        const val ENCODE_TRY_TIMES = 10
    }

    abstract val mediaType: MediaType

    abstract val mediaCodec: MediaCodec

    var trackId: Int = -1

    private var resTimeStampUs = -1L

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
    protected val syncEnqueue = Any()

    fun start() {
        mediaCodec.start()
        // デコード用のスレッドを開始
        thread { dequeueBuffer() }
    }

    fun stop() {
        // 別スレッドから終了を投げる.
        thread { enqueueEndStream() }
    }

    protected open fun release() {
        mediaCodec.stop()
        mediaCodec.release()

        trackId = -1
        resTimeStampUs = -1L
        firstTimeStampUs = -1L
        isFormatChanged = false
        isEncoding = false
        isCalledEndStream = false

        callback.onFinished()
    }

    @WorkerThread
    protected abstract fun enqueueEndStream()

    @WorkerThread
    private fun dequeueBuffer() {
        logI("Start dequeue.")
        isEncoding = true
        val bufferInfo = MediaCodec.BufferInfo()

        while (isEncoding) {
            val indexOrStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US)

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
        logI("End dequeue. resTimeStampUs: $resTimeStampUs")
        release()
    }

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