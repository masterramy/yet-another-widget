package com.ramybaheeg.yetanotherwidget.helpers

import android.util.Log
import com.ramybaheeg.yetanotherwidget.BuildConfig

object RuntimeLog {
    fun caught(exception: Exception) {
        Log.e(BuildConfig.APPLICATION_ID, "Caught widget/runtime exception", exception)
    }
}