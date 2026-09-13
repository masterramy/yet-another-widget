package com.tommasoberlose.anotherwidget.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chibatching.kotpref.bulk
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.CalendarHelper.applyFilters
import com.tommasoberlose.anotherwidget.models.Event
import com.tommasoberlose.anotherwidget.receivers.UpdatesReceiver
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import java.util.Calendar

class EventRepository(private val context: Context) {
    private val db by lazy { EventDatabase.getDatabase(context.applicationContext) }

    fun saveEvents(eventList: List<Event>) {
        db.runInTransaction {
            db.dao().deleteAll()
            db.dao().insert(eventList)
        }
    }

    fun clearEvents() = db.dao().deleteAll()

    fun resetNextEventData() {
        Preferences.bulk {
            remove(Preferences::nextEventId)
            remove(Preferences::nextEventName)
            remove(Preferences::nextEventStartDate)
            remove(Preferences::nextEventAllDay)
            remove(Preferences::nextEventLocation)
            remove(Preferences::nextEventEndDate)
            remove(Preferences::nextEventCalendarId)
        }
    }

    fun saveNextEventData(event: Event) {
        Preferences.nextEventId = event.eventID
    }

    fun getNextEvent(): Event? {
        val now = Calendar.getInstance().timeInMillis
        val limit = Calendar.getInstance().apply {
            timeInMillis = now
            when (Preferences.showUntil) {
                0 -> add(Calendar.HOUR, 3)
                1 -> add(Calendar.HOUR, 6)
                2 -> add(Calendar.HOUR, 12)
                3 -> add(Calendar.DAY_OF_MONTH, 1)
                4 -> add(Calendar.DAY_OF_MONTH, 3)
                5 -> add(Calendar.DAY_OF_MONTH, 7)
                6 -> add(Calendar.MINUTE, 30)
                7 -> add(Calendar.HOUR, 1)
                else -> add(Calendar.HOUR, 6)
            }
        }
        val next = getEventByEventId(Preferences.nextEventId)
        return if (next != null && next.endDate > now && next.startDate < limit.timeInMillis) {
            next
        } else {
            val events = getEvents()
            if (events.isNotEmpty()) {
                events.first().also(::saveNextEventData)
            } else {
                resetNextEventData()
                null
            }
        }
    }

    fun getEventByEventId(id: Long): Event? = db.dao().findByEventId(id)
    fun getEventById(id: Long): Event? = db.dao().findById(id)

    fun goToNextEvent() {
        val events = getEvents()
        if (events.isEmpty()) {
            resetNextEventData()
        } else {
            val index = events.indexOfFirst { it.eventID == Preferences.nextEventId }
            saveNextEventData(if (index > -1 && index < events.lastIndex) events[index + 1] else events.first())
        }
        UpdatesReceiver.setUpdates(context)
        MainWidget.updateWidget(context)
    }

    fun goToPreviousEvent() {
        val events = getEvents()
        if (events.isEmpty()) {
            resetNextEventData()
        } else {
            val index = events.indexOfFirst { it.eventID == Preferences.nextEventId }
            saveNextEventData(if (index > 0) events[index - 1] else events.last())
        }
        UpdatesReceiver.setUpdates(context)
        MainWidget.updateWidget(context)
    }

    fun getFutureEvents(): List<Event> =
        db.dao().findFuture(Calendar.getInstance().timeInMillis).applyFilters()

    private fun getEvents(): List<Event> {
        val now = Calendar.getInstance().timeInMillis
        val limit = Calendar.getInstance().apply {
            timeInMillis = now
            when (Preferences.showUntil) {
                0 -> add(Calendar.HOUR, 3)
                1 -> add(Calendar.HOUR, 6)
                2 -> add(Calendar.HOUR, 12)
                3 -> add(Calendar.DAY_OF_MONTH, 1)
                4 -> add(Calendar.DAY_OF_MONTH, 3)
                5 -> add(Calendar.DAY_OF_MONTH, 7)
                6 -> add(Calendar.MINUTE, 30)
                7 -> add(Calendar.HOUR, 1)
                else -> add(Calendar.HOUR, 6)
            }
        }
        return db.dao().findRange(now, limit.timeInMillis).applyFilters()
    }

    fun getEventsCount(): Int = getEvents().size
    fun close() = Unit

    @Dao
    interface EventDao {
        @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
        fun findById(id: Long): Event?

        @Query("SELECT * FROM events WHERE eventID = :eventId ORDER BY startDate LIMIT 1")
        fun findByEventId(eventId: Long): Event?

        @Query("SELECT * FROM events WHERE endDate > :from ORDER BY startDate")
        fun findFuture(from: Long): List<Event>

        @Query("SELECT * FROM events WHERE endDate > :from AND startDate <= :to ORDER BY startDate")
        fun findRange(from: Long, to: Long): List<Event>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insert(events: List<Event>)

        @Query("DELETE FROM events")
        fun deleteAll()
    }

    @Database(entities = [Event::class], version = 1, exportSchema = false)
    abstract class EventDatabase : RoomDatabase() {
        abstract fun dao(): EventDao

        companion object {
            @Volatile private var instance: EventDatabase? = null

            fun getDatabase(context: Context): EventDatabase = instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context,
                    EventDatabase::class.java,
                    "events"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
