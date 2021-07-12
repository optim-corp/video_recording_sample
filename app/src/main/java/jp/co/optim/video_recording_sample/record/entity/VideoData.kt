package jp.co.optim.video_recording_sample.record.entity

/**
 * 録画に関連する情報を有したデータクラス
 * @param width フレームの横サイズ
 * @param height フレームの縦サイズ
 * @param bitRate ビットレート
 * @param frameRate フレームレート
 * @param frameInterval Iフレームの間隔
 */
data class VideoData(
    val width: Int = 0,
    val height: Int = 0,
    val bitRate: Int = 480000,
    val frameRate: Int = 20,
    val frameInterval: Int = 10,
) {
    /**
     * 有効チェック
     */
    fun isAvailable(): Boolean =
        (width > 0 && height > 0 && bitRate > 0 && frameRate > 0 && frameInterval > 0)
}