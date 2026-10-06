package com.ramybaheeg.yetanotherwidget

import android.Manifest
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.db.EventRepository
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
    }

    @After
    fun cleanUp() {
        shell("dumpsys battery reset")
    }


    @Test
    fun storedNotificationStateDisplaysAndClearsDeterministically() {
        Preferences.showNotifications = true
        Preferences.lastNotificationId = 4242
        Preferences.lastNotificationTitle = "Q5 notification"
        Preferences.lastNotificationPackage = target.packageName
        Preferences.lastNotificationIcon = android.R.drawable.ic_dialog_info

        assertTrue(ActiveNotificationsHelper.showLastNotification())
        ActiveNotificationsHelper.clearLastNotification(target)
        assertFalse(ActiveNotificationsHelper.showLastNotification())
    }

    @Test
    fun mediaSessionIsConsumedAndFilterCanSuppressIt() {
        Preferences.showMusic = true
        val session = MediaSession(target, "Q5 media")
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

            Preferences.musicPlayersFilter = "q5.blocked.other"
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
    fun emulatorBatteryLevelTransitionsDriveLowBatteryGlanceState() {
        Preferences.showBatteryCharging = true

        shell("dumpsys battery unplug")
        shell("dumpsys battery set level 10")
        shell("dumpsys battery set status 3")
        assertTrue(waitUntil {
            BatteryHelper.updateBatteryInfo(target)
            Preferences.isBatteryLevelLow
        })

        shell("dumpsys battery set level 80")
        shell("dumpsys battery set status 3")
        assertTrue(waitUntil {
            BatteryHelper.updateBatteryInfo(target)
            !Preferences.isBatteryLevelLow
        })
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
