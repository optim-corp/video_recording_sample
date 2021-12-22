package jp.co.optim.video_recording_sample.record.entity

import android.media.MediaFormat

/**
 * メディアタイプ
 * @param ext 拡張子
 * @param mimeType MIMEタイプ
 */
enum class MediaType(val ext: String, val mimeType: String) {
    /**
     * オーディオ
     */
    AUDIO("m4a", MediaFormat.MIMETYPE_AUDIO_AAC),
    /**
     * ビデオ
     */
    VIDEO("mp4", MediaFormat.MIMETYPE_VIDEO_AVC),
}