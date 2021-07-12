package jp.co.optim.video_recording_sample.extensions

import android.util.Log

val Any.TAG: String get() = this::class.java.simpleName

fun Any.logV(message: String) = Log.v(TAG, message)

fun Any.logD(message: String) = Log.d(TAG, message)

fun Any.logI(message: String) = Log.i(TAG, message)

fun Any.logW(message: String) = Log.w(TAG, message)

fun Any.logE(message: String) = Log.e(TAG, message)
