package jp.co.optim.video_recording_sample.entity

import jp.co.optim.video_recording_sample.extensions.logI
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 録音録画に関連する情報を有したデータクラス
 * @param mediaType メディアタイプ
 * @param recFile 録音録画ファイル
 * @param audioData オーディオデータ
 * @param videoData ビデオデータ
 */
data class RecordData(
    val mediaType: MediaType,
    val recFile: File,
    val audioData: AudioData,
    val videoData: VideoData,
) {

    companion object {

        /**
         * 録音用のデータ作成.
         */
        fun newAudioRecordData(
            parentDir: File,
            audioData: AudioData
        ): RecordData =
            if (audioData.isAvailable()) {
                RecordData(
                    MediaType.AUDIO,
                    generateRecFile(MediaType.AUDIO, parentDir),
                    audioData,
                    VideoData()
                )
            } else {
                throw IllegalArgumentException("Invalid audio record data.")
            }

        /**
         * 録画用のデータ作成.
         */
        fun newVideoRecordData(
            parentDir: File,
            audioData: AudioData,
            videoData: VideoData
        ): RecordData =
            if (audioData.isAvailable() && videoData.isAvailable()) {
                RecordData(
                    MediaType.VIDEO,
                    generateRecFile(MediaType.VIDEO, parentDir),
                    audioData,
                    videoData
                )
            } else {
                throw IllegalArgumentException("Invalid video record data.")
            }

        /**
         * ファイル生成.
         * {MediaType}-{DATE}-{UUID}.{ext}
         */
        private fun generateRecFile(mediaType: MediaType, parentDir: File): File {
            if (!parentDir.isDirectory) {
                throw IOException("Parent dir is not directory.")
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            val fileName = String.format(Locale.US, "%s-%s.%s",
                mediaType.toString(), dateFormat.format(Date()), mediaType.ext)
            val recFile = File(parentDir, fileName)
            if (!recFile.createNewFile()) {
                throw IOException("Failed to create new file.")
            }
            logI("filePath: ${recFile.canonicalPath}")
            return recFile
        }
    }
}