package jp.co.optim.video_recording_sample.record.encode

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.extensions.logE
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.record.entity.MediaType
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

    private var resTimeStampMicros = 0L

    private var firstTimeStampMicros = -1L

    var trackId: Int = -1

    // エンコードが利用可能か.
    // INFO_OUTPUT_FORMAT_CHANGED が呼ばれると true
    var isAvailable = false
    private set

    // エンコード処理中かどうか.
    // dequeueBuffer() のループが回っている間は true
    protected var isEncoding = false
    private set

    // 停止処理が呼び出されたかどうか.
    protected var isStopRequested = false
    private set

    fun start() {
        mediaCodec.start()
        // デコード用のスレッドを開始
        thread { dequeueBuffer() }
    }

    fun stop() {
        isStopRequested = true
        // 別スレッドから終了を投げる.
        thread { enqueueEndStream() }
    }

    protected open fun release() {
        mediaCodec.stop()
        mediaCodec.release()

        isAvailable = false
        isEncoding = false
        isStopRequested = false
        firstTimeStampMicros = -1L

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
        logI("End dequeue. resTimeStampMicros: $resTimeStampMicros")
        release()
    }

    @WorkerThread
    private fun dequeueBufferStatus(status: Int) {
        when (status) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                logI("flag is INFO_OUTPUT_FORMAT_CHANGED")
                isAvailable = true
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

            if (firstTimeStampMicros < 0L) {
                // タイムスタンプ開始時間を記録する.
                firstTimeStampMicros = bufferInfo.presentationTimeUs
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                // 終了のときは値がバグっている可能性があるので、最後のタイムスタンプ時間に +1 しておく.
                bufferInfo.presentationTimeUs = resTimeStampMicros + 1L
            } else {
                val timestamp = bufferInfo.presentationTimeUs - firstTimeStampMicros
                if (timestamp < resTimeStampMicros) {
                    // 前のタイムスタンプよりも進んでいないとおかしい.
                    logE("Invalid timestamp. before: $timestamp, after: $resTimeStampMicros")
                    return
                }
                resTimeStampMicros = bufferInfo.presentationTimeUs - firstTimeStampMicros
                bufferInfo.presentationTimeUs = resTimeStampMicros
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