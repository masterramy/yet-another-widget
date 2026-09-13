#!/usr/bin/env python3
from pathlib import Path

FILES = [
    Path('app/src/main/java/com/tommasoberlose/anotherwidget/ui/widgets/StandardWidget.kt'),
    Path('app/src/main/java/com/tommasoberlose/anotherwidget/ui/widgets/AlignedWidget.kt'),
]

for path in FILES:
    text = path.read_text()

    # Target-31+ PendingIntent mutability contract: every widget token in these
    # Q1 renderers is immutable. Preserve UPDATE_CURRENT semantics where present.
    update_count = text.count('PendingIntent.FLAG_UPDATE_CURRENT')
    if update_count < 2:
        raise SystemExit(f'{path}: unexpectedly few FLAG_UPDATE_CURRENT sites ({update_count}); fail closed')
    text = text.replace(
        'PendingIntent.FLAG_UPDATE_CURRENT',
        'PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE'
    )

    weather_old = 'PendingIntent.getBroadcast(context, widgetID, i, 0)'
    if text.count(weather_old) != 1:
        raise SystemExit(f'{path}: expected exactly one weather token; fail closed')
    text = text.replace(
        weather_old,
        'PendingIntent.getBroadcast(context, widgetID, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)'
    )

    refresh_old = '''val refreshIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                IntentHelper.getWidgetUpdateIntent(context),'''
    refresh_new = '''val refreshIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                IntentHelper.getWidgetUpdateIntent(context),'''
    if text.count(refresh_old) != 1:
        raise SystemExit(f'{path}: expected exactly one background widget-update token; fail closed')
    text = text.replace(refresh_old, refresh_new)

    path.write_text(text)
    print(f'{path}: patched {update_count} UPDATE_CURRENT sites + weather + widget-update broadcast type')
