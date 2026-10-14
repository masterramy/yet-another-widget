#!/usr/bin/env python3
"""Fail release build on context-free cached private-notification eligibility."""
from pathlib import Path
root=Path("app/src/main/java/com/tommasoberlose/anotherwidget")
for name in ["helpers/GlanceProviderHelper.kt","ui/widgets/StandardWidget.kt","ui/widgets/AlignedWidget.kt"]:
    content=(root/name).read_text(encoding="utf-8")
    assert "ActiveNotificationsHelper.showLastNotification()" not in content, name
    assert "ActiveNotificationsHelper.showLastNotification(context)" in content, name
helper=(root/"helpers/ActiveNotificationsHelper.kt").read_text(encoding="utf-8")
listener=(root/"receivers/NotificationListener.kt").read_text(encoding="utf-8")
assert "fun showLastNotification(context: Context)" in helper
assert "if (!checkNotificationAccess(context))" in helper
assert "override fun onListenerDisconnected()" in listener
assert "ActiveNotificationsHelper.clearLastNotification(this)" in listener
print("F7 privacy contract PASS: permission-gated notification state and revocation invalidation")
