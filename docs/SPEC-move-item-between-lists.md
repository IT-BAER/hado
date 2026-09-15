# Spec + Plan: Move a to-do item to another list

Status: implemented in 1.2.0. Source: GitHub issue #7 (AlexPla).

## 1. Problem

Users capture into an "inbox" list (voice, quick add) and cannot re-file those items into the right
list. Home Assistant has no "move between lists" service (`todo/item/move` only reorders inside one
entity), so the app has to emulate it: create the item in the target list, then remove it from the
source list.

## 2. Findings that shape the design

- The Home screen does not use `TodoRepository` / `HomeViewModel` for item writes. Every page of the
  pager is a `TodoListEditor` (`widget/AddItemActivity.kt`) that talks to HA through
  `WidgetHttpClient` or to `LocalTodoStore` directly. `HomeViewModel.addItem` / `toggleItem` /
  `removeItem` have no callers. The issue's suggestion to build on `TodoRepository.addItem` /
  `removeItem` would therefore add code on a path the UI never runs. The move goes into
  `TodoListEditor`.
- There is no item context menu. Long-press (and tap in "checkbox only" mode) opens
  `ItemDetailDialog`, which is shared with the widget's `ItemDetailActivity`.
- `TodoListEditor` only knows its own `entityId`. The list of other lists lives in
  `HomeUiState.todoLists` (`HaState` with `friendlyName` and `supportedFeatures`).
- HA `todo.add_item` (checked against `homeassistant/components/todo/__init__.py`, dev branch):
  - requires `CREATE_TODO_ITEM` on the target;
  - rejects the whole call with `ServiceValidationError` if it contains `description`, `due_date`
    or `due_datetime` and the target lacks the matching feature bit;
  - accepts at most one of `due_date` / `due_datetime`;
  - always creates the item as `needs_action`. Status cannot be set on create.
- `todo.remove_item` requires `DELETE_TODO_ITEM` on the source.
- `todo.add_item` returns no uid. The editor already solves this with `resolveAddedItem`
  (re-fetch, diff against known uids, match summary).

## 3. Scope

In scope:

- "Move to list" action for one item, from the in-app list editor on the Home screen.
- HA mode and Local Mode.
- Active and completed items.
- Preserve summary, description, due (date or datetime) and completed status where the target
  supports them.

Out of scope, deliberately:

- The widget's full-screen editor (`AddItemActivity`) and the widget detail popup
  (`ItemDetailActivity`). Neither has the list of lists; the action stays hidden there. Follow-up in
  section 9.
- Multi-select / bulk move.
- Moving between HA and Local Mode (the app is in exactly one mode at a time).
- Undo.
- Routing through `TodoRepository` / `HomeViewModel` (see section 2).

## 4. Behavior

### 4.1 Entry point

`ItemDetailDialog` gets an optional `onMoveToList: (() -> Unit)? = null` parameter. When non-null, a
"Move to list" `TextButton` (icon `Icons.AutoMirrored.Filled.DriveFileMove` or
`Icons.Default.DriveFileMove`, whichever the installed material-icons-extended exposes) renders in
the dialog body below the due date section. `ItemDetailActivity` does not pass it, so the widget
popup is unchanged.

`TodoListEditor` passes `onMoveToList` only when all hold:

- `moveTargets` (new parameter, see 4.2) is not empty;
- the source list supports `DELETE_TODO_ITEM` (Local Mode lists always do, `ALL_FEATURES`);
- the item uid does not start with `temp_` (optimistic add not yet confirmed).

Tapping it closes the detail dialog without saving edits and opens the target picker. Unsaved edits
in the detail dialog are discarded, same as Cancel.

### 4.2 Target picker

New parameters on `TodoListEditor`:

```kotlin
moveTargets: List<HaState> = emptyList(),
onItemMoved: (targetEntityId: String) -> Unit = {}
```

`HomeScreen` passes `uiState.todoLists.filter { it.entityId != pageList.entityId }` and
`onItemMoved = { viewModel.loadItems(it, force = true) }` so the target page's cache is fresh when
the user swipes to it.

The picker is an `AlertDialog` titled "Move to list" with one row per target, in the user's saved
list order, showing the list icon (`ListIconManager.resolveIcon` + `ListIconPreview`, as elsewhere)
and name. Rows are:

- disabled when the target lacks `CREATE_TODO_ITEM`;
- annotated with a one-line caption when content would be lost (see 4.3);
- a single tap starts the move; no second confirmation.

### 4.3 Field mapping to the target

Computed from the target's `supportedFeatures`, identical for HA and Local Mode:

| Source field | Target supports | Sent / kept |
|---|---|---|
| `description` | `SET_DESCRIPTION_ON_ITEM` | `description` |
| `description` | not | dropped, caption "Description will be lost" |
| due with time | `SET_DUE_DATETIME_ON_ITEM` | `due_datetime` (same `T` -> space conversion as the detail save) |
| due with time | only `SET_DUE_DATE_ON_ITEM` | `due_date` = date part, caption "Time will be lost" |
| any due | neither | dropped, caption "Due date will be lost" |
| date-only due | `SET_DUE_DATE_ON_ITEM` | `due_date` |
| date-only due | only `SET_DUE_DATETIME_ON_ITEM` | dropped, caption "Due date will be lost" |
| status `completed` | `UPDATE_TODO_ITEM` | follow-up `update_item status=completed` |
| status `completed` | not | arrives as `needs_action`, caption "Will be marked as not done" |

Multiple losses join into one caption line. The mapping is one pure function
(`buildMovePayload(item, targetFeatures): MovePlan`) so it can be checked in isolation (section 7).

Date-only due -> datetime-only target: dropped rather than inventing a midnight time. Decided to keep
the item's meaning honest; revisit if a real integration needs it.

### 4.4 HA sequence

All on `Dispatchers.IO`, in the editor's `scope`, the network part under `NonCancellable` so leaving
the page mid-move cannot stop between add and remove.

1. Optimistic: remove the row from `items` with the existing exit animation (`pendingDeletes`).
2. `POST api/services/todo/add_item` on the target with `item` + mapped fields.
   Failure (null response, non-2xx, exception): restore the row at its original index, snackbar
   `error_move_failed`. Stop. Source is untouched.
3. `resolveAddedItem(target, knownUids = target uids fetched before step 2, summary)`. The known
   uids come from one `get_items` on the target before step 2; the editor's `items` belong to the
   source, not the target.
4. If the add position preference is TOP and the item is not already first active and the target
   supports `MOVE_TODO_ITEM`: `httpClient.moveTodoItem(target, newUid, null)`. Failure is logged
   only, same as `addItem`.
5. If the source item was completed and the target supports `UPDATE_TODO_ITEM`:
   `update_item status=completed` on `newUid`. Failure is logged only.
   Steps 4 and 5 are skipped when step 3 could not resolve the uid.
6. `POST api/services/todo/remove_item` on the source with the original uid.
   Failure: the item now exists in both lists. Do not restore the row silently: re-fetch the source,
   snackbar `error_move_remove_failed` ("Copied to %1$s, but could not remove it from this list").
7. Success: snackbar `move_done` ("Moved to %1$s"), `onChanged()`, `onItemMoved(target)`.

Invariant: the source item is only removed after HA returned 2xx for the add. Any failure leaves at
worst a duplicate, never a lost item.

### 4.5 Local Mode sequence

New `LocalTodoStore.moveItemToList(fromEntityId, toEntityId, itemUid, item: TodoItem, position)`:
inserts `item` (uid and status kept, fields already mapped per 4.3) into the target at `position`,
then removes the uid from the source. Both `saveItems` calls go through one `prefs.edit()` so a
process kill cannot leave a half move. The editor then runs steps 1 and 7 of 4.4.

### 4.6 Concurrency

- A pending toggle job for the item (`pendingToggleJobs[uid]`) is cancelled before the move starts,
  and the move uses the item's current optimistic status.
- The Home screen's 5 s `loadItems` poll can re-insert the source row between steps 1 and 6.
  Accepted: the next poll after step 6 removes it again. No locking.

## 5. Strings

New keys in `values/strings.xml` and all 9 locale dirs (`de`, `es`, `fr`, `ja`, `ko`, `nl`,
`pt-rBR`, `ru`, `zh-rCN`):

- `action_move_to_list` - "Move to list"
- `dialog_move_to_list_title` - "Move to list"
- `move_done` - "Moved to %1$s"
- `error_move_failed` - "Could not move the item"
- `error_move_remove_failed` - "Copied to %1$s, but could not remove it from this list"
- `move_loss_description` - "Description will be lost"
- `move_loss_time` - "Time will be lost"
- `move_loss_due` - "Due date will be lost"
- `move_loss_status` - "Will be marked as not done"

Note: CLAUDE.md says "11 locales" but lists 10 and the repo has 10 `values*` dirs. Fix the count in
CLAUDE.md while here.

## 6. Files touched

- `app/src/main/java/com/baer/hado/widget/AddItemActivity.kt` - `TodoListEditor` params
  `moveTargets` / `onItemMoved`, `moveItem(item, target)` next to `deleteItem`, picker dialog,
  `ItemDetailDialog` `onMoveToList` param + button, `buildMovePayload` next to `resolveAddedItem`.
- `app/src/main/java/com/baer/hado/ui/home/HomeScreen.kt` - pass `moveTargets` and `onItemMoved`.
- `app/src/main/java/com/baer/hado/data/local/LocalTodoStore.kt` - `moveItemToList`.
- `app/src/main/res/values*/strings.xml` - section 5.
- `CHANGELOG.md` - entry at release time.

No new Gson-serialized class, so no R8 keep rule. `MovePlan` stays an in-memory data class.
No new dependency.

## 7. Implementation order

1. `buildMovePayload` + a throwaway `main`-style check covering every row of the 4.3 table
   (no test source set exists; delete the harness afterwards). Build.
2. `LocalTodoStore.moveItemToList`. Build.
3. `TodoListEditor.moveItem` (HA + local), picker, `ItemDetailDialog` button. Build.
4. `HomeScreen` wiring. Build.
5. Strings, English first, then 9 locales.
6. `./gradlew assembleDebug`, then `assembleRelease` for the device pass.

## 8. Verification

Manual on a device; no test source set.

HA mode (use a `local_todo` list, which supports all features, and a `shopping_list`, which supports
create/delete/update/move but not description or due):

1. Active item with description + datetime due, `local_todo` A -> `local_todo` B: appears in B with
   all fields, gone from A, snackbar "Moved to B". Check in the HA web UI, not only the app.
2. Same item A -> shopping list: picker caption lists description and due loss; item arrives with
   summary only.
3. Completed item A -> B: arrives completed in B.
4. Add-position TOP: moved item is first active in B. BOTTOM: last.
5. Add fails: temporarily rename the target entity or take HA offline (airplane mode) before tapping
   the target. Row reappears in A at its old index, `error_move_failed`, B unchanged.
6. Remove fails: target a source list whose integration rejects delete (or block
   `remove_item` with an HA permission-restricted user). Item exists in both lists, snackbar
   `error_move_remove_failed`, A shows the item after re-fetch.
7. Swipe to B right after the move: item is visible without manual refresh.
8. Button absent in: widget item popup, widget full-screen editor, a source list without
   `DELETE_TODO_ITEM`, a just-added item still showing a `temp_` uid, a single-list setup.

Local Mode:

9. Repeat 1, 3, 4 with two local lists. Force-stop the app, reopen: state persisted, no duplicate.

Release:

10. Steps 1 and 9 on the minified release build.

## 9. Follow-ups, not part of this change

- Widget editor / widget popup support: needs the list of lists there. `AddItemActivity` could fetch
  `api/states` once via `WidgetHttpClient`, or read the widget's `ALL_LISTS_KEY` Glance state
  (only the widget's configured lists).
- Home screen edits do not refresh placed widgets (nothing under `ui/` calls `TodoWidgetWorker` or
  `WidgetStateMutator`). A move shows up in widgets on their next scheduled refresh, same as every
  other in-app edit today. Pre-existing, not changed here.
- `HomeViewModel.addItem` / `toggleItem` / `removeItem` and the matching `TodoRepository` item
  writes are dead code for the current UI. Candidate for deletion.
