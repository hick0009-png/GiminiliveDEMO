package com.example.geminimultimodalliveapi.utils

import android.util.Log
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import com.example.geminimultimodalliveapi.error.AppError
import com.example.geminimultimodalliveapi.session.SessionStateHolder

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val isHoliday: Boolean = false
)

class GoogleCalendarServiceHelper(private val calendarService: Calendar) {

    /**
     * Inserts an event in the user's primary Google Calendar with a pop-up reminder.
     * @param title Title of the event
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     * @param description Event description
     * @param reminderMinutes Minutes before the event to trigger a pop-up reminder. If -1, no reminder is set.
     * @return Boolean indicating success
     */
    suspend fun insertEvent(
        title: String,
        startTime: Long,
        endTime: Long,
        description: String,
        reminderMinutes: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val event = Event().apply {
                summary = title
                this.description = description
                
                val startDateTime = DateTime(startTime)
                start = EventDateTime().setDateTime(startDateTime)
                
                val endDateTime = DateTime(endTime)
                end = EventDateTime().setDateTime(endDateTime)
                
                if (reminderMinutes >= 0) {
                    val reminder = EventReminder().apply {
                        method = "popup"
                        minutes = reminderMinutes
                    }
                    reminders = Event.Reminders().apply {
                        useDefault = false
                        overrides = listOf(reminder)
                    }
                }
            }

            val createdEvent = calendarService.events().insert("primary", event).execute()
            Log.i("CalendarHelper", "Successfully created calendar event: ${createdEvent.id}")
            true
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Failed to insert event to Google Calendar", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            false
        }
    }

    /**
     * Deletes an event from the user's primary Google Calendar by event ID.
     */
    suspend fun deleteEvent(eventId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            calendarService.events().delete("primary", eventId).execute()
            Log.i("CalendarHelper", "Successfully deleted calendar event: $eventId")
            true
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Failed to delete event $eventId from Google Calendar", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            false
        }
    }

    /**
     * Updates an existing event in the user's primary Google Calendar.
     */
    suspend fun updateEvent(
        eventId: String,
        title: String,
        startTime: Long,
        endTime: Long,
        description: String,
        reminderMinutes: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val event = calendarService.events().get("primary", eventId).execute()
            event.summary = title
            event.description = description
            
            val startDateTime = DateTime(startTime)
            event.start = EventDateTime().setDateTime(startDateTime)
            
            val endDateTime = DateTime(endTime)
            event.end = EventDateTime().setDateTime(endDateTime)
            
            if (reminderMinutes >= 0) {
                val reminder = EventReminder().apply {
                    method = "popup"
                    minutes = reminderMinutes
                }
                event.reminders = Event.Reminders().apply {
                    useDefault = false
                    overrides = listOf(reminder)
                }
            } else {
                event.reminders = Event.Reminders().apply {
                    useDefault = true
                    overrides = null
                }
            }
            
            calendarService.events().update("primary", eventId, event).execute()
            Log.i("CalendarHelper", "Successfully updated calendar event: $eventId")
            true
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Failed to update event $eventId in Google Calendar", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            false
        }
    }

    /**
     * Fetches user's events from the primary calendar within a time range.
     */
    suspend fun fetchEventsForRange(startTime: Long, endTime: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val eventsResult = calendarService.events().list("primary")
                .setTimeMin(DateTime(startTime))
                .setTimeMax(DateTime(endTime))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()

            val items = eventsResult.items ?: emptyList()
            Log.i("CalendarHelper", "Fetched ${items.size} events from primary calendar")
            
            items.map { event ->
                val startMs = event.start?.dateTime?.value ?: event.start?.date?.value ?: startTime
                val endMs = event.end?.dateTime?.value ?: event.end?.date?.value ?: endTime
                CalendarEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "(ไม่มีชื่อเรื่อง)",
                    description = event.description,
                    startTime = startMs,
                    endTime = endMs,
                    isHoliday = false
                )
            }
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Failed to fetch user events", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            emptyList()
        }
    }

    /**
     * Fetches Thai official government holidays from Google's public holiday calendar.
     */
    suspend fun fetchThaiHolidays(startTime: Long, endTime: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val holidaysCalendarId = "th.th#holiday@group.v.calendar.google.com"
            val eventsResult = calendarService.events().list(holidaysCalendarId)
                .setTimeMin(DateTime(startTime))
                .setTimeMax(DateTime(endTime))
                .setSingleEvents(true)
                .execute()

            val items = eventsResult.items ?: emptyList()
            Log.i("CalendarHelper", "Fetched ${items.size} Thai holidays")
            
            items.map { event ->
                val startMs = event.start?.dateTime?.value ?: event.start?.date?.value ?: startTime
                val endMs = event.end?.dateTime?.value ?: event.end?.date?.value ?: endTime
                CalendarEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "วันหยุดราชการ",
                    description = event.description ?: "วันสำคัญทางราชการไทย",
                    startTime = startMs,
                    endTime = endMs,
                    isHoliday = true
                )
            }
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Failed to fetch Thai holidays. This is normal if Calendar API is not fully configured.", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            emptyList()
        }
    }
}
