package jp.co.optim.video_recording_sample

import android.util.Size
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/**
 * 画像解像度
 */
enum class ScreenResolution(
    val frameSize: Size,
    @StringRes val stringResId: Int,
    @IdRes private val idResId: Int
) {
    /**
     * Mic Only
     */
    MIC_ONLY(
        Size(0, 0),
        R.string.label_mic_only,
        R.id.mrb_mic_only
    ),

    /**
     * SD
     */
    SD(
        Size(480, 720),
        R.string.label_sd,
        R.id.mrb_sd
    ),

    /**
     * HD
     */
    HD(
        Size(720, 1280),
        R.string.label_hd,
        R.id.mrb_hd
    ),

    /**
     * Full HD
     */
    FULL_HD(
        Size(1080, 1920),
        R.string.label_full_hd,
        R.id.mrb_full_hd
    ),
    ;

    companion object {
        fun convertFromResId(@IdRes idResId: Int): ScreenResolution =
            values().find { it.idResId == idResId } ?: MIC_ONLY
    }
}