package com.ramybaheeg.yetanotherwidget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.db.EventRepository
import com.ramybaheeg.yetanotherwidget.global.Actions
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper
import com.ramybaheeg.yetanotherwidget.helpers.BatteryHelper
import com.ramybaheeg.yetanotherwidget.helpers.GlanceProviderHelper
import com.ramybaheeg.yetanotherwidget.helpers.MediaPlayerHelper
import com.ramybaheeg.yetanotherwidget.models.Event
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5GlanceProviderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context
    private val listenerComponent
        get() = "${target.packageName}/com.ramybaheeg.yetanotherwidget.receivers.NotificationListener"

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun waitUntil(timeoutMs: Long = 8_000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(150)
        }
        return predicate()
    }

    @Before
    fun prepare() {
        Kotpref.init(target)
        Preferences.showNotifications = false
        Preferences.showMusic = false
        Preferences.appNotificationsFilter = ""
        Preferences.musicPlayersFilter = ""
        Preferences.lastNotificationId = -1
        Preferences.lastNotificationTitle = ""
        Preferences.lastNotificationPackage = ""
        Preferences.lastNotificationIcon = 0
        Preferences.mediaPlayerTitle = ""
        Preferences.mediaPlayerArtist = ""
        Preferences.mediaPlayerAlbum = ""
        Preferences.mediaPlayerPackage = ""
        shell("cmd notification allow_listener $listenerComponent")
        assertTrue(waitUntil { ActiveNotificationsHelper.checkNotificationAccess(target) })

        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                testContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @After
    fun cleanUp() {
        try {
            testContext.getSystemService(NotificationManager::class.java).cancelAll()
        } catch (_: Throwable) {}
        shell("dumpsys battery reset")
    }

    private fun postNotification(id: Int, title: String) {
        val manager = testContext.getSystemService(NotificationManager::class.java)
        val channelId = "q5-provider"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Q5 provider", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(testContext, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(testContext)
        }
        builder
            .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_dialog_info))
            .setContentTitle(title)
            .setContentText("Q5 provider notification")
        manager.notify(id, builder.build())
    }

    @Test
    fun realNotificationIsConsumedAndDismissalClearsIt() {
        Preferences.showNotifications = true
        Preferences.hideNotificationAfter = Constants.GlanceNotificationTimer.HALF_MINUTE.rawValue

        postNotification(4242, "Q5 notification")

        assertTrue(waitUntil { Preferences.lastNotificationTitle == "Q5 notification" })
        assertEquals(testContext.packageName, Preferences.lastNotificationPackage)
        assertTrue(ActiveNotificationsHelper.showLastNotification())

        val alarms = shell("dumpsys alarm")
        assertTrue(alarms.contains(Actions.ACTION_CLEAR_NOTIFICATION))

        testContext.getSystemService(NotificationManager::class.java).cancel(4242)
        assertTrue(waitUntil { !ActiveNotificationsHelper.showLastNotification() })
    }

    @Test
    fun mediaSessionIsConsumedAndFilterCanSuppressIt() {
        Preferences.showMusic = true
        val session = MediaSession(testContext, "Q5 media")
        try {
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Q5 Song")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Q5 Artist")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "Q5 Album")
                    .build()
            )
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            session.isActive = true

            assertTrue(waitUntil {
                MediaPlayerHelper.updatePlayingMediaInfo(target)
                Preferences.mediaPlayerTitle == "Q5 Song"
            })
            assertEquals("Q5 Artist", Preferences.mediaPlayerArtist)
            assertEquals("Q5 Album", Preferences.mediaPlayerAlbum)

            Preferences.musicPlayersFilter = "not.${testContext.packageName}"
            MediaPlayerHelper.updatePlayingMediaInfo(target)
            assertEquals("", Preferences.mediaPlayerTitle)

            Preferences.musicPlayersFilter = ""
            assertTrue(waitUntil {
                MediaPlayerHelper.updatePlayingMediaInfo(target)
                Preferences.mediaPlayerTitle == "Q5 Song"
            })
        } finally {
            session.release()
        }

        MediaPlayerHelper.updatePlayingMediaInfo(target)
        assertTrue(waitUntil { Preferences.mediaPlayerTitle == "" })
    }

    @Test
    fun notificationAccessRevocationClearsMediaState() {
        Preferences.showMusic = true
        Preferences.mediaPlayerTitle = "stale"
        shell("cmd notification disallow_listener $listenerComponent")
        assertTrue(waitUntil { !ActiveNotificationsHelper.checkNotificationAccess(target) })
        MediaPlayerHelper.updatePlayingMediaInfo(target)
        assertEquals("", Preferences.mediaPlayerTitle)
    }

    @Test
    fun emulatorBatteryStatesDriveLowAndChargingGlanceState() {
        Preferences.showBatteryCharging = true

        shell("dumpsys battery unplug")
        shell("dumpsys battery set level 10")
        shell("dumpsys battery set status 3")
        BatteryHelper.updateBatteryInfo(target)
        assertTrue(Preferences.isBatteryLevelLow)
        assertFalse(Preferences.isCharging)

        shell("dumpsys battery set level 80")
        shell("dumpsys battery set status 2")
        BatteryHelper.updateBatteryInfo(target)
        assertFalse(Preferences.isBatteryLevelLow)
        assertTrue(Preferences.isCharging)
    }

    @Test
    fun providerOrderingRoundTripsAndEventProviderCanActivate() {
        val reversed = Constants.GlanceProviderId.values().toList().reversed()
        GlanceProviderHelper.saveGlanceProviderOrder(reversed)
        assertEquals(reversed, GlanceProviderHelper.getGlanceProviders(target))

        instrumentation.uiAutomation.grantRuntimePermission(
            target.packageName,
            Manifest.permission.READ_CALENDAR
        )
        Preferences.showEvents = true
        Preferences.showEventsAsGlanceProvider = true

        val repo = EventRepository(target)
        try {
            repo.clearEvents()
            repo.saveEvents(
                listOf(
                    Event(
                        id = 9001,
                        eventID = 99001,
                        title = "Q5 glance event",
                        startDate = System.currentTimeMillis() + 15 * 60_000L,
                        endDate = System.currentTimeMillis() + 45 * 60_000L,
                        calendarID = 1,
                        allDay = false,
                        selfAttendeeStatus = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
                        availability = android.provider.CalendarContract.EventsEntity.AVAILABILITY_BUSY
                    )
                )
            )
            assertTrue(GlanceProviderHelper.showGlanceProviders(target))
        } finally {
            repo.clearEvents()
            repo.close()
        }
    }
}
