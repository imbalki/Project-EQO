# ADR-0007: Shared-storage file access, attachments and EQO screenshots

Status: implemented; unit tests with fakes pass; phone validation pending (Realme, Android 11).

## Context

The owner wants EQO to find files on the phone and send them (WhatsApp, Gmail, Messages) as part of an approved plan, and to attach a screenshot EQO just took. Android 11 scoped storage hides most shared folders from an ordinary app, and a file is shared with another app only through a content URI that EQO grants.

## Decision

- **All files access** (`MANAGE_EXTERNAL_STORAGE`) is requested only through a guided setup row. EQO never grants it to itself and never writes secure settings; the owner turns it on in Android's own page. The row is separate from the readiness rows (optional, never "next step"). EQO is sideloaded, so Google Play's restriction on this permission does not apply; the manifest entry says so and carries a targeted lint ignore.
- **Read-only file actions.** `FIND_FILES(query, folder)` and `LIST_FILES(folder)` return names, sizes and paths only, never contents, and their output is fenced as untrusted data. They need All files access outside EQO's own folders and say where to turn it on. A plain relative path such as `out/docs` keeps the earlier behaviour of listing EQO's workspace. `LIST_FILES` renamed its parameter to `folder` (`path` is still accepted).
- **Place rules** (`SharedStorageLayout`): only shared storage is reachable. EQO's private data and settings, other apps' `Android/data` and `Android/obb`, and the share staging folder are refused after resolving links. Hidden entries are skipped and links are not followed.
- **Attachments** are an optional `attachment` parameter on `SEND_EMAIL`, `SEND_WHATSAPP` and `SEND_SMS`: a file path, several separated by `|`, or `last_screenshot`. The planner validator rejects `..`, links and control characters. The plan preview names every file that will go out, so the owner approves exactly that.
- **Sharing.** Files are copied into a staging folder and served by a separate, non-exported `EqoSharedFileProvider` (authority `ai.eqo.app.sharedfiles`) that serves only that folder, not the owner's real folders. `ACTION_SEND` / `ACTION_SEND_MULTIPLE` carry `EXTRA_STREAM`, a `ClipData`, `FLAG_GRANT_READ_URI_PERMISSION`, and are aimed at one package (WhatsApp, Gmail, or the default messaging app). The existing Send-button automation then presses Send. The earlier provider (`ai.eqo.app.fileprovider`) is unchanged.
- **Cleanup.** Staged copies are removed after one hour (swept at the start of every share), immediately when opening the app fails, and when nothing could be composed. The hour leaves time for a large upload to finish after Send.
- **Screenshots.** `TAKE_SCREENSHOT` uses the accessibility service's `takeScreenshot`, saves a PNG to `Pictures/EQO` (or to EQO's private Pictures folder when All files access is off) and records it as `last_screenshot`. It is refused when a protected window (`FLAG_SECURE`) is showing, when EQO's own window is in front, and while the owner has taken over. If the window turns protected during capture the picture is deleted and not recorded.

## Limits

- The planner writes the whole plan before any step runs, so it cannot use a path that `FIND_FILES` would discover mid-run. A path has to come from the owner's request, or be `last_screenshot`. Letting a plan pause and ask the owner to pick from search results is future work.
- WhatsApp's file preview, Google Messages' compose screen and Gmail's compose screen are driven by view ids and labels that have not been tried on the phone yet; if a Send button is not found the step reports "could not press Send" and the owner finishes it.
- The `jid` extra that opens a WhatsApp chat directly from a share is undocumented and may be ignored by some WhatsApp versions, in which case WhatsApp asks which chat to use.
- 25 MB total per send (Gmail's limit); old EQO screenshots are not deleted automatically.
