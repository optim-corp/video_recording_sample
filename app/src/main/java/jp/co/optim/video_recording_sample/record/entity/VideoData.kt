package jp.co.optim.video_recording_sample.record.entity

import android.util.Size

/**
 * 録画に関連する情報を有したデータクラス
 * @param frameSize フレームサイズ
 * @param frameRate フレームレート
 * @param frameInterval Iフレームの間隔
 * @param bitRate ビットレート
 */
data class VideoData(
    val frameSize: Size = Size(0, 0),
    val frameRate: Int = 20,
    val frameInterval: Int = 10,
    val bitRate: Int = frameSize.width * frameSize.height * frameRate / 8,
) {
    /**
     * 有効チェック
     */
    fun isAvailable(): Boolean =
        (frameSize.width > 0 && frameSize.height > 0
                && frameRate > 0 && frameInterval > 0 && bitRate > 0)
}