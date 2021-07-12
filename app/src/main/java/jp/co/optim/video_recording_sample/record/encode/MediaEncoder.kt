package jp.co.optim.video_recording_sample.record.encode

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.annotation.WorkerThread
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
        private const val CODEC_DEQUEUE_TIMEOUT_US = 10 * 1000L
        private const val ENCODE_TRY_TIMES = 10
    }

    abstract val mediaType: MediaType

    abstract val mediaCodec: MediaCodec

    protected var reqTimeStampMicros = 0L

    var trackId: Int = -1

    // エンコード処理中かどうか.
    var isEncoding = false
    private set

    fun start() {
        mediaCodec.start()
        // デコード用のスレッドを開始
        thread { dequeueBuffer() }
    }

    fun stop() {
        // 別スレッドから終了を投げる.
        thread { enqueueEndStream() }
    }

    @WorkerThread
    protected fun enqueueBuffer(buffer: ByteBuffer, timeStampMicros: Long) {
        val index = dequeueInputBuffer()
        if (index < 0) return
        val inputBuffer = mediaCodec.getInputBuffer(index) ?: return
        inputBuffer.put(buffer)
        mediaCodec.queueInputBuffer(index, 0, buffer.capacity(), timeStampMicros, 0)
    }

    @WorkerThread
    private fun enqueueEndStream() {
        val index = dequeueInputBuffer(true)
        mediaCodec.queueInputBuffer(
            index, 0, 0, reqTimeStampMicros, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    @WorkerThread
    private fun dequeueInputBuffer(neverGiveUp: Boolean = false): Int {
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
        return -1
    }

    @WorkerThread
    private fun dequeueBuffer() {
        logI("Start dequeue.")
        isEncoding = true
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val indexOrStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US)

            if (indexOrStatus < 0) {
                // 0未満の場合はステータスチェック.
                dequeueBufferStatus(indexOrStatus)
            } else {
                dequeueBufferIndex(indexOrStatus, bufferInfo)
                // ストリーム終了フラグの場合は終了する.
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }

        logI("End dequeue. $reqTimeStampMicros")
        mediaCodec.stop()
        mediaCodec.release()
        isEncoding = false
        callback.onFinished()
    }

    @WorkerThread
    private fun dequeueBufferStatus(status: Int) {
        when (status) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                callback.onStarted(mediaType, mediaCodec.outputFormat)
            }
            else -> {}
        }
    }

    @WorkerThread
    private fun dequeueBufferIndex(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        try {
            val buffer = mediaCodec.getOutputBuffer(index) ?: return
            if (bufferInfo.size < 0) {
                return
            }
            // コーデックの設定情報が出力されたときのフラグ. エンコード結果ではないためスキップする.
            // このフラグはエンコード開始時の最初の１回と途中で設定が変わった場合に出力される.
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                return
            }
            // バッファーに書き込みを行う.
            val copyBuffer = ByteBuffer.allocate(buffer.capacity()).put(buffer)
            callback.onEncodedBuffer(trackId, copyBuffer, bufferInfo)
        } finally {
            mediaCodec.releaseOutputBuffer(index, false)
        }
    }
}