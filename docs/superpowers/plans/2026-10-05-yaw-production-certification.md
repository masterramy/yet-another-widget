# YAW Production Certification Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. TDD and systematic debugging govern every defect repair; Alfredverse Workspace Protocol/YAW authority remains superior to this methodology.

**Goal:** Exhaust the remaining autonomous Yet Another Widget production-readiness queue and leave only narrowly classified external/owner-controlled gates.

**Architecture:** Preserve the existing Q5 certification architecture and authoritative branch `q2-publication-hardening-api36`. Treat current CI failures as independent evidence channels: controls instrumentation, runtime-core, and locale/accessibility. Repair QA harness behavior first where evidence proves the product is not at fault; mutate shipping/customer bytes only after a reproducible product defect has a failing regression test.

**Tech Stack:** Android/Kotlin, Gradle, Espresso/androidTest, adb/emulator shell, GitHub Actions, Google Drive evidence/checkpoint documents.

**Spec:** Canonical `YAW_RELEASE_SURFACE_MANIFEST_CURRENT` (Drive ID `1P1HIGJX0V6z80AFEmXshHa20LwKV6dITVteT21a-cjc`) plus current Alfredverse Workspace Protocol v1.7 rev26 and YAW authority bundle.

## Global Constraints

- Master App Restoration retains business/product/publication authority; YAW owns delegated technical/publication-readiness work only.
- Do not broaden product scope or restart completed Q1-Q4 work.
- Current shipping/customer-byte candidate at plan start: `bf2ac34bac54939b01f74bd7818d04e4f3620e79`.
- Current QA head at plan start: `6b4eed329cab0a414cb745bd2a66a530fbcf1954`.
- Run184 / `37378446645` is the current full-gate RED baseline.
- No production-code mutation absent concrete product-defect evidence.
- Any production bugfix must follow RED -> minimal GREEN -> full-suite GREEN.
- Real rendered evidence must remain real; do not fabricate screenshots or UI state.
- Production signing, Play Console mutation/upload, paid listing activation, and go-live remain owner-controlled.

## Review Focus

- Harness navigation must distinguish keyboard dismissal from activity/fragment Back.
- Virtualized/scrolling controls must use condition-based reveal rather than fixed swipe counts.
- Framework restart/locale tests must wait for ActivityManager-resolvable app state, not merely PackageManager metadata.
- Q5 runtime scripts must assert the actual current screen before navigation rather than assuming task-stack state.
- Final GREEN must be reconciled against every remaining autonomous manifest row, not treated as sufficient by itself.

---

### Task 1: Stabilize controls instrumentation harness

**Files:**
- Modify: `app/src/androidTest/java/com/tommasoberlose/anotherwidget/Q5ControlsTest.kt`

**Evidence / RED baseline:**
- Run184 controls job fails six cases.
- `typographyMenusExposeSupportedBoundaries`: fixed swipe count does not reveal `40sp`.
- Clock/calendar/weather/gesture and secondary-surface failures show the test remained in child activities after Back/keyboarding assumptions.
- Glance rows are present in retained hierarchy but can appear asynchronously/offscreen.

- [ ] Replace fixed list-position assumptions with condition-based reveal helpers for text in scrollable/virtualized surfaces.
- [ ] Replace ambiguous `pressBack()` exits from search-focused child activities with deterministic toolbar/action Back where appropriate.
- [ ] Preserve assertions on actual customer-visible strings and controls.
- [ ] Run the exact controls instrumentation in the next full Q5 gate and require 0 failures.

### Task 2: Stabilize runtime-core manual-refresh state handling

**Files:**
- Modify: `tools/q5_surface_media.sh`

**Evidence / RED baseline:**
- Run184 reaches manual-refresh tranche with the Settings screen already resumed after HOME/re-entry.
- The harness then incorrectly searches that Settings hierarchy for main-screen `action_settings`.

- [ ] Make the manual-refresh tranche assert/detect current screen state instead of assuming re-entry returns to Main.
- [ ] Keep widget-bound before/after assertions and manual Refresh action evidence unchanged.
- [ ] Run exact runtime-core and require the tranche to pass without weakening assertions.

### Task 3: Stabilize locale/accessibility framework-restart readiness

**Files:**
- Modify: `tools/q5_locale_accessibility.sh`

**Evidence / RED baseline:**
- Run184 installs the exact APK and changes locale successfully.
- `dumpsys package` exposes MainActivity, yet immediate `am start -n` returns Error type 3 after framework restart.
- Current readiness check proves PackageManager metadata, not ActivityManager intent resolution.

- [ ] Add condition-based readiness on actual launcher/MainActivity resolution after each locale framework restart.
- [ ] Do not replace the locale matrix with static resource checks.
- [ ] Preserve every shipped locale, Arabic RTL fallback, font-scale, touch-target and focusability assertions.
- [ ] Run exact locale/accessibility job and require full pass.

### Task 4: Run and adjudicate a fresh exact-head full Q5 gate

**Files:**
- Modify only the existing final-trigger QA marker if needed.

- [ ] Trigger one exact-head `[q5-final]` run after Tasks 1-3.
- [ ] Verify build, controls, migration, API32 wallpaper, weather provider, runtime-core, and locale/accessibility individually.
- [ ] Download and inspect retained artifacts for any RED job.
- [ ] If a RED is harness/infrastructure, return to systematic debugging and repair only that layer.
- [ ] If a RED proves a product defect, write/verify a failing regression test before touching production code, then implement the minimal fix and rerun the full gate.

### Task 5: Close remaining autonomous manifest coverage

**Files:**
- Existing Q5 tests/harness only unless a proven product defect requires shipping code.
- Update canonical Drive manifest after evidence is adjudicated.

- [ ] Reconcile all `PENDING_Q5` rows against fresh exact-candidate evidence.
- [ ] Add only tests needed for still-autonomous, production-relevant gaps.
- [ ] Narrowly classify irreducible OEM/private credential/account/physical-device cases as EXTERNAL and unsupported/destructive cases as N/A.
- [ ] Do not convert confidence-depth permutations into release blockers without evidence.

### Task 6: Whole-branch code/evidence review

**Files:**
- Review diffs from shipping candidate through final QA head.

- [ ] Review production-code mutations independently from QA-only mutations.
- [ ] Check test intent against product behavior and guard against tests that merely test implementation details.
- [ ] Re-run/inspect fresh full verification after any Important/Critical review finding is fixed.
- [ ] Record Minor findings without expanding scope.

### Task 7: Durable closure and release handoff

**Files:**
- Update `YAW_RELEASE_SURFACE_MANIFEST_CURRENT`.
- Update current YAW checkpoint/handoff/restore surfaces under rev26.
- Reconcile canonical owner-ready Play package to exact candidate truth.
- Write Q8 residual package / final technical handoff.

- [ ] Record final shipping candidate, QA head, run IDs, artifact digests/hashes, and remaining external gates.
- [ ] Preserve privacy-route truth; do not claim it live without fresh verification.
- [ ] Confirm production remains unauthorized until owner-controlled signing/store/go-live gates are executed.
- [ ] Deliver normal YAW completion summary plus Superpowers pilot report.
