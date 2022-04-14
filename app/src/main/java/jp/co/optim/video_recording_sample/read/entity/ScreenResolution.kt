package jp.co.optim.video_recording_sample.read.entity

import android.util.Size

/**
 * 画像解像度
 */
enum class ScreenResolution(val frameSize: Size) {
    /**
     * Mic Only
     */
    MIC_ONLY(Size(0, 0)),

    /**
     * SD
     */
    SD(Size(480, 720)),

    /**
     * HD
     */
    HD(Size(720, 1280)),

    /**
     * Full HD
     */
    FULL_HD(Size(1080, 1920)),
    ;

    companion object {
        fun convertFromString(name: String): ScreenResolution =
            values().find { it.toString() == name } ?: MIC_ONLY
    }
}