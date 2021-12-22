package jp.co.optim.video_recording_sample.record.entity

/**
 * 録音に関連する情報を有したデータクラス
 * @param samplingRate サンプリングレート
 * @param bitRate ビットレート
 * @param bytesPerSample １サンプルあたりのバイト数
 */
data class AudioData(
    val samplingRate: Int = 16000,
    val bitRate: Int = 32000,
    val bytesPerSample: Int = 2,
) {
    /**
     * 有効チェック
     */
    fun isAvailable(): Boolean =
        samplingRate > 0 && bytesPerSample > 0 && bitRate > 0
}