# Runtime screenshot attachments — t_62291cf6

NOT TESTED ON PHONE. Host fakes are not evidence of a successful phone send.

## Diagnosis and scope

The supplied phone evidence places 26 JPEG screenshots in DCIM/Screenshots and
camera photos in DCIM/Camera; Pictures/Screenshots does not exist. Exact-path
attachments reach confirmation, so staging/share are not the demonstrated failure.
DCIM/Screenshots was already in shared-storage-aliases.json: adding that alias again
would not fix the resolver. The previous type-only resolver walked all shared storage
and refused the whole search on an unrelated unreadable folder, depth/budget cap or
result cap, even if MediaStore had already returned screenshots. Its latest filter
only sorted, presenting all results as ambiguous. Its date filter used modification
time, which can change on copy/import, rather than the screenshot's capture timestamp.
The fake tree recreates the supplied naming/folder shape plus an unrelated deep folder:
whole-storage image search refuses, but bounded screenshot search succeeds. This is a
reproduced resolver defect, not a claim that the phone's exact failing directory or
exception has been captured.

Screenshot/camera/download searches now start in all existing known and learned
aliases of that type. MediaStore metadata remains primary for MIME and OEM buckets;
indexed screenshot-name images are considered even outside the known aliases.
An unavailable media source falls back to the filesystem. Filesystem results must
still satisfy exclusion/link, entry/depth/deadline/result limits. Screenshot_ names
with a valid local YYYY-MM-DD-HH-mm-ss timestamp (optional two-digit fraction) use that
time for recency, date matching and confirmation; other files retain media/filesystem
times. latest selects a unique newest timestamp; equal newest timestamps require a
human choice. photo means camera photos, not screenshots. Explicit folder filters
remain authoritative. No path is invented or logged.

## Picker and confirmation

Zero matches and incomplete/unreadable searches offer ACTION_OPEN_DOCUMENT through
the foreground task activity. The picker requests one openable content document,
not a tree grant, starting at DCIM/Screenshots for screenshots or Download otherwise.
The initial URI is a best guess only; Android/provider may ignore it. The service
awaits the activity's return before confirmation. No background service starts system
UI; absent/destroyed UI fails as a readable Needs you handoff.

The selected URI stays process-only. A persistable read grant, if offered by Android,
is acquired for that document, used to make a bounded staged copy, and released
immediately after staging, before confirmation or any send. No grant remains after
send. If process death occurs before release, the next task-screen construction
releases stale read-only non-tree document grants; workspace tree grants are preserved.
No URI is saved in EQO preferences for that recovery. Partial copies are deleted on
failure, cancellation or Stop, including cancellation during final outgoing staging. Provider metadata
is display-only; nothing reads/trusts document contents as instructions. Date missing
from a provider is shown as unavailable. Byte size is checked while copying even if
provider size is absent or inaccurate. The total limit remains 25 MB.

Ready to attach shows file name, date and size, with Send / Cancel and Choose a
different file. Different opens the same one-document picker and requires another
Send confirmation; for a multi-file request it replaces the offered set with that one
chosen document. The existing 60-second confirmation timeout is unchanged. A picker
cancel never authorizes a send. No auto-send extension, automatic retry, permission
bypass, takeover exemption or resume token is introduced. Stop/real takeover and
unknown send effects retain the existing guards. Staged outgoing copies retain the
existing short-lived receiving-app share policy; original selected-document grants
are not held for that policy.

## Reasons and planning

Attachment preparation uses fixed, allowlisted diagnoses (no_matching_file,
folder_not_readable, search_incomplete, staging_failed, provider_failed,
send_route_failed, cancellation/selection/size/unavailable/not-allowed/invalid).
The run screen shows the fixed human-readable explanation, and EqoRun code/reason
contains only the kind. Names, paths, URIs, search words and exception payloads never
enter these diagnoses. Draft/manual-send handoffs remain honest, not successful sends.

RegistryPlanVocabulary contains executable JSON few-shots for latest screenshot,
yesterday screenshot with an explicit example local date, explicit capture then
last_screenshot, the eBay bill by email (known own address only), and latest camera
photo. TAKE_SCREENSHOT is only for capturing the current screen; existing screenshots
must use find:. Relative dates must be expanded using the real request date, not the
example date. Fake planner outputs test schema/prompt contracts, not live model quality.

## Owner phone checklist

Record build commit and pass/fail using synthetic files and recipients only.

- [ ] Android 11 / All files access on: latest screenshot from DCIM/Screenshots is
      shown with correct capture date/size, not a new EQO capture or camera photo.
- [ ] Yesterday screenshot and two tied newest screenshots: date match and human
      choice are correct. Missing Pictures/Screenshots does not stop the search.
- [ ] Latest photo chooses DCIM/Camera. An explicit folder limits search to that folder.
- [ ] Zero matches / search failure opens phone picker in best-guess folder. Cancel
      reports Needs you; no app launches with an attachment and nothing sends.
- [ ] Pick a document, wait for Ready to attach, confirm name/date/actual byte size,
      Cancel, retry and Send. Check original-document persisted read grant is released.
- [ ] Choose a different file shows a fresh confirmation and uses only replacement.
- [ ] Provider lacks date/size, denies access, returns a deleted file or fails while
      copying; human-readable fixed reason and no filenames/URIs/paths in EqoRun.
- [ ] Cancel/Stop during picker or confirmation; late result; rotation/process death;
      background EQO; real takeover. None silently resume, approve or retry a send.
- [ ] WhatsApp, email and SMS routes: selected staged copy is handed off to the approved
      recipient only; route unavailable shows a specific reason. Delivery is not invented.
- [ ] Natural latest/yesterday screenshot, photo and bill requests preview find:;
      explicit capture previews TAKE_SCREENSHOT then last_screenshot. Foreground EQO
      capture refusal and protected-screen refusal remain intact.
