package com.ramybaheeg.yetanotherwidget.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ramybaheeg.yetanotherwidget.helpers.WeatherHelper

class UpdateWeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            WeatherHelper.updateWeather(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "updateWeather",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateWeatherWorker>().build()
            )
        }
    }
}
