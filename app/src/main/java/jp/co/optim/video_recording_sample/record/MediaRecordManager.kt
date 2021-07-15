package jp.co.optim.video_recording_sample.record

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.WorkerThread
import jp.co.optim.video_recording_sample.entity.MediaType
import jp.co.optim.video_recording_sample.entity.RecordData
import jp.co.optim.video_recording_sample.extensions.and
import jp.co.optim.video_recording_sample.extensions.logI
import jp.co.optim.video_recording_sample.extensions.logW
import jp.co.optim.video_recording_sample.record.encode.AudioEncoder
import jp.co.optim.video_recording_sample.record.encode.MediaEncoder
import jp.co.optim.video_recording_sample.record.encode.VideoEncoder
import java.nio.ByteBuffer

class MediaRecordManager : MediaEncoder.Callback {

    private var mediaMuxer: MediaMuxer? = null
    private var audioEncoder: AudioEncoder? = null
    private var videoEncoder: VideoEncoder? = null

    private var isPrepared = false
    private var isStarted = false
    private var isRecording = false

    private val isEncodeAvailable: Boolean get() =
        and(audioEncoder?.isFormatChanged, videoEncoder?.isFormatChanged)

    // Muxer非同期処理用のオブジェクト.
    private val syncMuxer = Any()

    fun prepare(recordData: RecordData) {
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
    }

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

    @WorkerThread
    fun inputAudioBytes(bytes: ByteArray) {
        if (isRecording) {
            audioEncoder?.enqueueAudioBytes(bytes)
        }
    }

    @WorkerThread
    fun inputVideoBitmap(bitmap: Bitmap) {
        if (isRecording) {
            videoEncoder?.enqueueVideoBitmap(bitmap)
        }
    }

    override fun onStarted(mediaType: MediaType, mediaFormat: MediaFormat) {
        synchronized(syncMuxer) {
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
                mediaMuxer?.start()
            }
        }
    }

    override fun onEncodedBuffer(
        trackId: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        synchronized(syncMuxer) {
            if (isEncodeAvailable) {
                logI("writeSampleData: $trackId")
                mediaMuxer?.writeSampleData(trackId, buffer, bufferInfo)
            }
        }
    }

    override fun onFinished() {
        synchronized(syncMuxer) {
            if (!isEncodeAvailable && mediaMuxer != null) {
                // AudioとVideoのエンコードが両方完了してからMuxerをリリースする.
                mediaMuxer?.release()

                // リセット.
                mediaMuxer = null
                audioEncoder = null
                videoEncoder = null
                isPrepared = false
                isStarted = false
                isRecording = false

                logI("Recording is completed!")
            }
        }
    }
}