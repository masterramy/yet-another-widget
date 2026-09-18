# Interactive / Scheduled Worker Coordination Gate

This file records Ramy's standing concurrency rule for Yet Another Widget restoration.

Before an interactive YAW chat claims or mutates any technical slice:

1. Bootstrap the CURRENT canonical Google Drive protocol and YAW workspace authority.
2. Read/reconcile the latest scheduled-worker heartbeat/checkpoint/handoff that current protocol requires.
3. Inspect the live GitHub branch HEAD and relevant current CI state.
4. Compare the interactive chat's expected parent with the live branch immediately before mutation.
5. If the scheduled worker or another authorized actor advanced the branch or closed the intended slice, adopt that newer verified edge. Do **not** replay, recreate, or independently redo the work.
6. Claim only work that remains unowned/unclosed after reconciliation.
7. Use a fail-closed compare-and-update pattern for repository writes; if the branch moved, abort the stale write and reconcile again.
8. Preserve valid closed-gate evidence unless changed bytes actually invalidate it.

The scheduled worker remains enabled. Interactive work does not disable, pause, reschedule, or replace it.

Newer verified owning-workspace, repository, CI, or user authority outranks stale snapshots or this coordination note.
