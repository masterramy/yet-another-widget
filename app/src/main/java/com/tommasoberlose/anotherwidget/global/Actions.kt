package com.ramybaheeg.yetanotherwidget.global

import com.ramybaheeg.yetanotherwidget.BuildConfig

object Actions {
    const val ACTION_EXTRA_OPEN_WEATHER_PROVIDER = "ACTION_EXTRA_OPEN_WEATHER_PROVIDER"

    private val actionPrefix = "${BuildConfig.APPLICATION_ID}.action."

    val ACTION_TIME_UPDATE = actionPrefix + "TIME_UPDATE"
    val ACTION_ALARM_UPDATE = actionPrefix + "ALARM_UPDATE"
    val ACTION_CALENDAR_UPDATE = actionPrefix + "CALENDAR_UPDATE"
    val ACTION_WEATHER_UPDATE = actionPrefix + "WEATHER_UPDATE"
    val ACTION_OPEN_WEATHER_INTENT = actionPrefix + "OPEN_WEATHER_INTENT"
    val ACTION_GO_TO_NEXT_EVENT = actionPrefix + "GO_TO_NEXT_EVENT"
    val ACTION_GO_TO_PREVIOUS_EVENT = actionPrefix + "GO_TO_PREVIOUS_EVENT"
    val ACTION_CLEAR_NOTIFICATION = actionPrefix + "CLEAR_NOTIFICATION"
    val ACTION_UPDATE_GREETINGS = actionPrefix + "UPDATE_GREETINGS"
    val ACTION_REFRESH = actionPrefix + "REFRESH"
}