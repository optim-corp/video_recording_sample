package jp.co.optim.video_recording_sample.entity

import android.util.Size

/**
 * 画像解像度
 */
enum class ScreenResolution(val frameSize: Size) {
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

    /**
     * 不明
     */
    UNKNOWN(Size(0, 0)),
    ;

    companion object {
        fun convertFromString(name: String): ScreenResolution =
            values().find { it.toString() == name } ?: UNKNOWN
    }
}