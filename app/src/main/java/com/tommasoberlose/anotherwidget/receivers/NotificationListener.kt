package com.ramybaheeg.yetanotherwidget.receivers

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.ramybaheeg.yetanotherwidget.global.Actions
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper
import com.ramybaheeg.yetanotherwidget.helpers.MediaPlayerHelper
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import java.lang.Exception
import java.util.*


class NotificationListener : NotificationListenerService() {

    companion object {
        private const val NOTIFICATION_TIMEOUT_REQUEST_CODE = 28943
    }
    override fun onListenerConnected() {
        MediaPlayerHelper.updatePlayingMediaInfo(this)
        MainWidget.updateWidget(this)
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.notification?.extras?.let { bundle ->
            bundle.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)?.let {
                MediaPlayerHelper.updatePlayingMediaInfo(this)
            } ?: run {
                val isGroupHeader = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                val isOngoing = sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0

                if (bundle.containsKey(Notification.EXTRA_TITLE) && !isGroupHeader && !isOngoing && ActiveNotificationsHelper.isAppAccepted(sbn.packageName) && !sbn.packageName.contains("com.android.systemui")) {
                    Preferences.lastNotificationId = sbn.id
                    Preferences.lastNotificationTitle = bundle.getString(Notification.EXTRA_TITLE) ?: ""
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Preferences.lastNotificationIcon = sbn.notification.smallIcon.resId
                        } else {
                            @Suppress("DEPRECATION")
                            Preferences.lastNotificationIcon = sbn.notification.icon
                        }
                    } catch (ex: Exception) {
                        Preferences.lastNotificationIcon = 0
                    }
                    Preferences.lastNotificationPackage = sbn.packageName
                    MainWidget.updateWidget(this)
                    setTimeout(this)
                }
            }
        }

        super.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        MediaPlayerHelper.updatePlayingMediaInfo(this)

        sbn?.let {
            if (sbn.id == Preferences.lastNotificationId && sbn.packageName == Preferences.lastNotificationPackage) {
                ActiveNotificationsHelper.clearLastNotification(this)
            }
        }

        MainWidget.updateWidget(this)
        super.onNotificationRemoved(sbn)
    }

    private fun setTimeout(context: Context) {
        with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
            val intent = Intent(context, UpdatesReceiver::class.java).apply {
                action = Actions.ACTION_CLEAR_NOTIFICATION
            }
            val timeoutIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_TIMEOUT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            cancel(timeoutIntent)
            val timeoutPref = Constants.GlanceNotificationTimer.fromInt(Preferences.hideNotificationAfter)
            if (timeoutPref != Constants.GlanceNotificationTimer.WHEN_DISMISSED) {
                set(
                    AlarmManager.RTC,
                    Calendar.getInstance().timeInMillis + when (timeoutPref) {
                        Constants.GlanceNotificationTimer.HALF_MINUTE -> 30 * 1000
                        Constants.GlanceNotificationTimer.ONE_MINUTE -> 60 * 1000
                        Constants.GlanceNotificationTimer.FIVE_MINUTES -> 5 * 60 * 1000
                        Constants.GlanceNotificationTimer.TEN_MINUTES -> 10 * 60 * 1000
                        Constants.GlanceNotificationTimer.FIFTEEN_MINUTES -> 15 * 60 * 1000
                        Constants.GlanceNotificationTimer.WHEN_DISMISSED -> 0
                    },
                    timeoutIntent
                )
            }
        }
    }
}