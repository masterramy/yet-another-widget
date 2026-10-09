#!/usr/bin/env python3
"""Fail CI if Calendar/Clock/event tappable bitmap uses Activity for refresh."""
from pathlib import Path
import re
ROOT=Path("app/src/main/java/com/tommasoberlose/anotherwidget/ui/widgets")
FILES=[ROOT/"StandardWidget.kt",ROOT/"AlignedWidget.kt",ROOT/"ClockWidget.kt"]
for path in FILES:
    s=path.read_text(encoding="utf-8")
    wrong=re.search(r"PendingIntent\.getActivity\s*\(\s*context\s*,\s*widgetID\s*,"
                    r"\s*IntentHelper\.get(?:Clock|Calendar|Event)Intent\s*\(",s)
    if wrong:raise AssertionError(f"{path}: unsafe activity PendingIntent for configurable tap")
    if "IntentHelper.getWidgetTapPendingIntent(" not in s:
        raise AssertionError(f"{path}: no typed widget-tap dispatch")
print("YAW H9 widget tap dispatch PASS: Standard, Aligned and Clock")
