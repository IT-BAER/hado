# Changelog

All notable changes to HAdo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-04-24

### Added
- New list icon picker with direct typed `mdi:*` search, live preview, inline suggestions, and native MDI defaults; compatible emoji and MDI choices can sync upstream while custom images remain local-only
- Pull-to-refresh on every list editor: swipe down to force a fresh fetch from Home Assistant (or local store in local mode)
- Scroll animation fix: items no longer appear to reorder or shuffle when scrolling fast through a list — enter animations now only play for truly new items

### Changed
- The inline add row now stays above active items, and newly added items are inserted at the top in both the app editor and widget editor
- Newly added Home Assistant items are now moved to the top immediately after creation, so the refreshed persisted order matches the optimistic UI order
- Rapid check/uncheck taps on the same item are now debounced (350 ms) and coalesced — only the settled state is sent to Home Assistant, eliminating out-of-order API races
- Toggling an item now reverts its optimistic state if the Home Assistant API call fails, instead of leaving the item in a wrong state
- The widget editor now shows the list icon and list name directly in the top app bar, freeing more vertical space for items and the add row
- List icons now use a shared fixed-size preview across the editor, home overview, app settings, and widget settings so emoji and custom image icons stay visually aligned

### Fixed
- Expired or revoked Home Assistant sessions now clear saved auth state and return cleanly to the login screen instead of bouncing between screens in a 401 loop
- Tapping a list chip in the home editor now moves the horizontal pager to that list again; chip taps and swipes stay in sync
- Newly added items now animate existing rows downward instead of snapping when the optimistic temporary row is replaced by the confirmed item
- The Local Mode button on the login screen now uses a readable high-contrast style instead of a barely visible outline treatment
- Custom list images are now center-cropped before being stored and render as proper circular icons instead of malformed stretched thumbnails
- Non-admin Home Assistant accounts now fall back cleanly to local-only custom icons and show a note instead of attempting admin-only entity icon updates
- Reopening the list icon picker now shows the active MDI or emoji selection again, inline icon suggestions no longer dismiss the keyboard, and restoring the default icon now returns to the native `mdi:clipboard-list` list icon

### Security
- Updated Glance widget library from 1.1.0 to 1.1.1 to address CVE-2024-7254 (protobuf vulnerability in transitive dependencies `glance-appwidget-proto` and `glance-appwidget-external-protobuf`)

## [1.0.5] - 2026-04-23

### Added
- The currently open list now refreshes in the background about every 5 seconds while the home screen is visible, so changes from other users show up much faster
- New opt-in widget setting “Focus add field on open”: tapping a list title on the widget can now automatically focus the new-item input and open the keyboard
- Main app header now includes a purple heart support action that opens an optional external support dialog for Buy Me a Coffee and PayPal
- Widget settings now end with a `"💜 Support"` section that links to the same optional external support pages

### Changed
- Home list loading now uses a per-list cache and deduplicated item requests instead of letting each embedded page fetch on its own

### Fixed
- Swiping between lists no longer triggers duplicate `todo.get_items` bursts that could hit Home Assistant 429 rate limits
- Short `Retry-After` rate-limit responses are now retried once while the existing cached list stays visible
- Swiping between home lists now auto-scrolls the selected list chip into view so the active list stays visible

## [1.0.4] - 2026-04-22

### Added
- Widget settings now include a global switch to show or hide list title icons

### Changed
- Widget settings redraw immediately from cached widget state while a background refresh still updates live Home Assistant data

### Fixed
- Widget loading placeholder now uses a centered, rounded card style instead of a bright white overlay
- Widget list headers now fall back to the default list icon when custom icon data is missing or stale

## [1.0.3] - 2026-04-21

### Added
- Inline home list editing with drag-to-reorder, long-press item details, shared editor behavior, and horizontal swiping between lists
- Home overview now shows resolved list icons, and the editor reclaims vertical space while typing by hiding the overview strip
- Per-widget item height control and a dedicated widget loading placeholder during startup
- Extended translations for the new app and widget UI copy across all supported locales

### Changed
- Refreshed the app UI across login, home, settings, and Material 3 theme tokens, shapes, and spacing
- Widget refresh interval is now configured per widget instance instead of globally, and widget schedules are created and cancelled per widget
- Widget title area now opens the app directly while the refresh action stays refresh-only

### Fixed
- While adding items inline, the active field stays visible after Enter and a second Back after closing the keyboard restores the full layout instead of exiting the app
- Widget toggles are now optimistic across widget instances, show a pending state while syncing, and revert cleanly if Home Assistant update fails
- Build support and warning cleanup for `compileSdk 35`, including AGP 8.7.3, Gradle 8.9, deprecated UI API cleanup, and preserved debug symbols for `libdatastore_shared_counter.so`

## [1.0.2] - 2026-04-15

### Changed
- Widget settings: back gesture/button now saves changes instead of discarding them

### Added
- Full i18n support for 10 languages: English, German, Spanish, French, Portuguese (Brazil), Dutch, Russian, Japanese, Korean, Chinese (Simplified)

## [1.0.1] - 2026-04-13

### Fixed
- Release build: added ProGuard keep rules for widget data classes (`WidgetListData`, `WidgetSettings`, `RefreshTokenResponse`) that R8 was obfuscating, breaking Gson serialization
- Release build: extracted local `SimpleState` classes to shared model package covered by existing keep rules, fixing HA API state parsing
- Release build: added `@SerializedName` field preservation rule for R8 full mode (default in AGP 8.5+)
- Release build: added `android:usesCleartextTraffic="true"` to allow HTTP connections to local Home Assistant instances
- Added ProGuard keep rules for LocalTodoStore inner classes and Gson TypeToken

## [1.0.0] - 2026-04-09

### Added
- Material3 home screen with multi-list management
- Glance 1.1.0 home screen widget with multi-list support
- OAuth2 authentication with Home Assistant
- Long-Lived Access Token authentication fallback
- To-do item creation, editing, completion, and deletion
- Drag-to-reorder items (via WebSocket)
- Item descriptions with Markdown support (Markwon)
- Due date and due time support with date/time pickers
- Overdue item highlighting
- Dynamic relative due dates (Today, Tomorrow, In 3d, etc.)
- Full-screen list editor (Google Keep-inspired design)
- Item detail dialog (long-press) with description and due date
- Customizable widget appearance (font size, background opacity, compact mode)
- Per-list icon customization (emoji, image, or disabled)
- Show/hide widget title setting
- Checkbox-only interaction mode for widget
- Show/hide completed items in widget
- Configurable refresh interval (15m / 30m / 1h / 2h / 4h)
- Proactive OAuth token refresh (prevents 401 errors)
- Dark and light theme support (follows system)
- Adaptive launcher icon with monochrome support
