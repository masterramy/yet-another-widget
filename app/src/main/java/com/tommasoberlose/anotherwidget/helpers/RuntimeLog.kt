package com.tommasoberlose.anotherwidget.helpers

import android.util.Log
import com.tommasoberlose.anotherwidget.BuildConfig

object RuntimeLog {
    fun caught(exception: Exception) {
        Log.e(BuildConfig.APPLICATION_ID, "Caught widget/runtime exception", exception)
    }
}