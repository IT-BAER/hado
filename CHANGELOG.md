# Changelog

All notable changes to HAdo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
