package jp.co.optim.video_recording_sample.extensions

fun and(vararg values: Boolean?): Boolean {
    var ret: Boolean? = null
    values.forEach {
        ret = if (it != null) ret?.and(it) ?: it else ret
    }
    return ret ?: false
}