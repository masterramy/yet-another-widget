package com.tommasoberlose.anotherwidget.services

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.network.WeatherNetworkApi
import com.tommasoberlose.anotherwidget.ui.activities.MainActivity
import com.tommasoberlose.anotherwidget.ui.fragments.MainFragment
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.greenrobot.eventbus.EventBus

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            LOCATION_ACCESS_NOTIFICATION_ID,
            getLocationAccessNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        job?.cancel()
        job = serviceScope.launch {
            if (ActivityCompat.checkSelfPermission(
                    this@LocationService,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val location = suspendCancellableCoroutine<android.location.Location?> { continuation ->
                    LocationServices.getFusedLocationProviderClient(this@LocationService)
                        .lastLocation
                        .addOnCompleteListener { task ->
                            if (!continuation.isActive) return@addOnCompleteListener
                            continuation.resume(if (task.isSuccessful) task.result else null)
                        }
                }

                location?.let {
                    Preferences.customLocationLat = it.latitude.toString()
                    Preferences.customLocationLon = it.longitude.toString()
                }

                WeatherNetworkApi(this@LocationService).updateWeather()
                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                stopSelf(startId)
            } else {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val LOCATION_ACCESS_NOTIFICATION_ID = 28465

        @JvmStatic
        fun requestNewLocation(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getLocationAccessNotification(): Notification {
        with(NotificationManagerCompat.from(this)) {
            // Create channel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                createNotificationChannel(
                    NotificationChannel(
                        getString(R.string.location_access_notification_channel_id),
                        getString(R.string.location_access_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.location_access_notification_channel_description)
                    }
                )
            }

            val builder = NotificationCompat.Builder(this@LocationService, getString(R.string.location_access_notification_channel_id))
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(getString(R.string.location_access_notification_title))
                .setOngoing(true)
                .setColor(ContextCompat.getColor(this@LocationService, R.color.colorAccent))

            // Main intent that open the activity
            builder.setContentIntent(PendingIntent.getActivity(this@LocationService, 0, Intent(this@LocationService, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            return builder.build()
        }
    }
}
