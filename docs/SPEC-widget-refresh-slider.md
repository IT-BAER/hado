# Spec + Plan: Widget refresh interval as a 1-60 minute slider

Status: spec agreed, not implemented.

## 1. Problem

The widget refresh interval today is a 5-option radio list (15m / 30m / 1h / 2h / 4h) in the widget
configure screen. Two complaints:

- The granularity is coarse; there is no way to refresh faster than 15 minutes.
- The option is hard to find (configure activity only, reached at widget placement or via the
  launcher "reconfigure" affordance). `OpenSettingsAction` exists in `widget/TodoWidgetActions.kt`
  but is not referenced anywhere, so the widget itself has no gear icon.

## 2. Scope

In scope:

- Replace the radio list with a linear slider, 1-60 minutes, integer steps.
- Store the interval as minutes (Int) instead of an enum name.
- Schedule sub-15-minute intervals with a self-rescheduling one-time work chain, because
  WorkManager rejects periodic work below 15 minutes.
- Inline warning below the slider for values under 5 minutes.

Out of scope, decided against deliberately:

- Per-todo-list intervals inside one widget. One interval per widget instance stays.
- Push / websocket updates from Home Assistant.
- Refresh-on-screen-on or refresh-on-visible opportunistic triggers.
- An interval for the in-app Home screen; the Retrofit path is untouched.
- Discoverability fixes (widget gear icon, app Settings entry). Tracked separately, section 8.

## 3. Behavior

Widget configure screen (`WidgetSettingsActivity`), "Refresh Interval" section:

- A Material 3 `Slider`, range 1f..60f, `steps = 58` (59 discrete integer positions).
- Current value rendered as a label above the slider, e.g. "12 minutes" / "1 minute".
- Below 5: a caption in warning colour stating that frequent refreshes cost battery and that
  Android may delay refreshes while the screen is off.
- The existing `refresh_description` caption stays.

Scheduling, per widget instance, keyed by `appWidgetId` exactly as today:

- interval >= 15: `PeriodicWorkRequest` under the existing unique name
  `todo_widget_sync_<appWidgetId>`, `ExistingPeriodicWorkPolicy.UPDATE`. Unchanged from today.
- interval < 15: `OneTimeWorkRequest` with `setInitialDelay(interval, MINUTES)`, enqueued under the
  same unique name via `enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE, ...)`. At the end of a
  successful `doWork()` the worker re-enqueues itself with the same delay, reading the interval
  fresh from `WidgetSettingsManager` each time.
- Switching a widget between the two modes must cancel the other mode first: periodic and one-time
  work under the same unique name are distinct WorkManager entities.
- Widget removal (`cancelPeriodic`) must cancel both.
- `TodoWidgetReceiver.onUpdate` fires often and must never disturb a pending schedule: it enqueues
  with `KEEP`. Only an explicit settings save passes `reschedule = true`, which cancels and
  re-enqueues. Without this, a sub-15-minute chain has its initial delay restarted on every widget
  update and never fires at all.
- Known race, accepted: a chain worker already running when a new interval is saved can finish
  afterwards and re-enqueue its successor with the interval it read before the save, overwriting the
  freshly scheduled one. The schedule corrects itself on the following hop, so no locking is added.

Doze is accepted: sub-15-minute chains will be deferred while the device is idle. No exact alarms,
no battery-optimization exemption prompt.

## 4. Persistence and migration

`WidgetSettings.refreshInterval: RefreshInterval` (enum, stored by name) becomes
`refreshIntervalMinutes: Int = 30`.

The `RefreshInterval` enum and its five string resources (`refresh_15min` .. `refresh_4hours`)
across 11 locales are deleted from the UI and the settings model. The old enum names survive only
as the migration mapping below.

Legacy clamp (decided): a widget still holding an old enum name is mapped in the loader rather than
reset to the default, so nobody who chose a long interval to save battery is silently moved to 30m.
Mapping: `MIN_15` -> 15, `MIN_30` -> 30, `HOUR_1` / `HOUR_2` / `HOUR_4` -> 60 (the slider maximum).
The mapping lives in `WidgetSettingsManager.load`, reads the old string key, writes the new Int key
back, and removes the old key. The same mapping applies to the `LEGACY_GLOBAL_REFRESH_INTERVAL`
fallback value.

Two storage sites carry this value and both must change:

1. The `SharedPreferences` key `refresh_interval_<appWidgetId>` in `WidgetSettingsManager`
   (`putString` -> `putInt`, under a new key name `refresh_minutes_<appWidgetId>` so an old string
   value cannot throw `ClassCastException` on read).
2. The `WidgetSettings` object is Gson-serialized into Glance state
   (`TodoWidgetKeys.SETTINGS_JSON_KEY`) and deserialized in `TodoWidget.provideGlance`. A widget
   whose Glance state was written by the old version deserializes `refreshIntervalMinutes` as 0.
   The renderer does not read the field, so display is unaffected, but any code path that reads the
   interval from Glance state must treat 0 as unset and fall back to `WidgetSettingsManager`.

R8: `WidgetSettings` already needs a Gson keep rule; verify it covers the changed field and test a
release build, per the project's R8 landmine note.

## 5. Files touched

- `app/src/main/java/com/baer/hado/widget/WidgetSettings.kt` - drop the `RefreshInterval` enum, add
  `refreshIntervalMinutes: Int`, update `load` / `save` / `delete`.
- `app/src/main/java/com/baer/hado/widget/TodoWidgetWorker.kt` - branch `enqueuePeriodic` on the
  15-minute threshold, add the one-time chain, re-enqueue at the end of `doWork`, cancel both work
  types in `cancelPeriodic`.
- `app/src/main/java/com/baer/hado/widget/WidgetSettingsActivity.kt` - replace the radio column
  (around line 556) with the slider, value label, and conditional warning.
- `app/src/main/res/values/strings.xml` plus the 10 locale dirs - delete the five interval strings,
  add `refresh_minutes_value` (plural-capable) and `refresh_frequent_warning`.
- `app/proguard-rules.pro` - confirm the `WidgetSettings` keep rule.

## 6. Implementation order

1. `WidgetSettings.kt`: field swap, new pref key, legacy read path. Build.
2. `TodoWidgetWorker.kt`: threshold branch, chain enqueue, self re-enqueue, cancel both. Build.
3. `WidgetSettingsActivity.kt`: slider UI plus warning. Build.
4. Strings: `values/strings.xml` first, then all 10 locale files.
5. R8 check plus release build.

## 7. Verification

There is no test source set in this project, so every check is manual on a device.

1. Fresh widget, slider at 30 -> `adb shell dumpsys jobscheduler | grep hado` shows one periodic
   job; confirm the value survives reopening the configure screen.
2. Set 2 minutes -> confirm a one-time job is scheduled, wait through two cycles with the screen
   on, confirm the widget content updates and a new job is enqueued each time.
3. Move 2 -> 30 -> confirm the one-time chain is gone and exactly one periodic job exists, with no
   orphaned chain still firing.
4. Move 30 -> 2 -> confirm the periodic job is cancelled.
5. Remove the widget mid-chain -> confirm no job remains for that `appWidgetId`.
6. Two widgets with different intervals -> confirm both schedules coexist and neither overwrites
   the other's unique work name.
7. Upgrade path: install the current release build, set 4h, install the new build over it, confirm
   the widget still refreshes and the configure screen shows 60 minutes, clamped from 4h.
8. Release build (minified) of steps 1 and 7, to catch a Gson/R8 regression on the changed field.

## 8. Follow-up, not part of this change

Discoverability. Both routes are small and independent of the slider work:

- Render a gear in the widget top bar wired to the existing unused `OpenSettingsAction`. The top
  bar is hidden when `showTitle = false`, so this cannot be the only route.
- Add a "Widgets" section to `AppSettingsScreen` listing placed widgets via
  `GlanceAppWidgetManager`, each opening `WidgetSettingsActivity` for its `appWidgetId`.
