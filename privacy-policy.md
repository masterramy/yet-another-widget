# Privacy Policy — Yet Another Widget (working identity)

Last updated: September 18, 2026

This document describes the current pre-release restoration candidate. Before public distribution, the final app name, publisher identity, contact details, and stable public policy URL must be synchronized with the actual Google Play listing.

## Summary

The app does not contain advertising, analytics, Firebase Crashlytics, or an app-owned user-account/backend service. Most information used by the widget stays on the device. Data is sent off-device only when needed for a feature the user enables, such as requesting weather from a selected weather provider or opening an external link.

## Calendar information

If the user enables calendar events and grants calendar permission, the app reads upcoming calendar data through Android's CalendarContract APIs. Event data used by the widget, including titles, times, and locations, may be cached locally on the device. The app does not send calendar contents to an app-owned server.

## Location and weather

If the user chooses device geolocation for weather and grants location permission, the app requests **approximate (coarse) location only while an app Activity is visible**. The last latitude and longitude are stored locally so scheduled weather refreshes can reuse them without reacquiring device location while the app is closed or not in use.

Weather requests send the saved location to the weather provider selected by the user. The release candidate supports MET Norway Locationforecast for global weather and the U.S. National Weather Service (weather.gov) for U.S. locations. Neither path requires the user to create a weather-provider account or supply an API key. Those third parties process requests under their own terms and privacy practices.

When the user searches for a custom place, the app uses Android's Geocoder implementation. The implementation and any network processing behind it can vary by device and Android service provider.

## Notifications and media

If the user explicitly enables Android notification-listener access, the app can read notification metadata needed for the optional notification and current-media widget features. The app stores only the metadata needed to render those enabled features, such as a notification title/package or current media title/artist/album, and does not send that information to an app-owned server.

## Wallpaper preview

On Android 12L and earlier only, the optional in-app widget preview can request legacy storage access to show the current wallpaper behind the preview. This feature is disabled on Android 13 and later. Wallpaper content is used locally and is not uploaded by the app.

## Local storage and retention

Preferences, saved weather coordinates, widget configuration, cached calendar events, and enabled notification/media metadata are stored locally in the app's private data. Android backup is disabled for the application. Data remains until it is replaced, cleared by app behavior, removed through Android app-data controls, or deleted when the app is uninstalled.

## Internet access and external services

The app uses internet access for enabled weather providers and for user-initiated external links such as project feedback or provider documentation. The project does not operate its own analytics or telemetry endpoint.

Third-party services can receive ordinary network metadata such as IP address when the device communicates with them. Their handling of that information is governed by their own privacy policies.

## Sale of data

The app does not sell personal data and does not use personal or sensitive data for advertising.

## Changes and contact

This policy will be updated if the app's data practices or enabled third-party services change. Questions about the current restoration source can be raised through the issue tracker at:

https://github.com/masterramy/yet-another-widget/issues
