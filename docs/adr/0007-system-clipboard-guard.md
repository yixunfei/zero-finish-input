# ADR 0007: Opt-in system clipboard cleanup

Status: accepted

Foreground authorization and automatic startup were updated by
[ADR 0012](0012-keyboard-private-copy-and-foreground-cleanup.md).

## Context

Users can accidentally invoke a source application's ordinary Copy action. An
ordinary default IME can observe some system clipboard changes, but cannot
intercept writes or prevent other authorized readers from accessing them first.
The user explicitly approved a separate, granular, default-off mitigation.

## Decision

The app provides independent monitoring, keyboard reminder, system notification,
cleanup mode (none, confirmation, automatic) and authentication controls. All
active behaviors default off. Authentication is a separate choice from operation
confirmation; enabling it changes automatic cleanup to confirmation mode.

Only the AndroidSystemClipboard adapter may obtain the platform clipboard,
subscribe to changes, inspect a timestamp/empty state and clear it. It never reads
the body, label, source package or URI. Android 8.x uses a literal empty text clip;
Android 9+ uses clearPrimaryClip. The platform test exception only writes fixed
public fixtures. privacyCheck enforces exact file exceptions and continues to
forbid body reads everywhere. Existing private-vault access remains unchanged.

Monitoring requires explicit opt-in, a live IME service and default-IME identity.
Work is serialized on a bounded worker, separate from the input thread. Initial
registration processes the existing current item in explicitly enabled automatic
mode; other modes establish a baseline without cleanup. Duplicate
classification callbacks and self-generated empty updates do not loop. Settings,
service and default-IME changes invalidate pending work before queued cleanup.

Keyboard reminders reserve a row only while enabled, so clip changes do not move
keys. Notifications contain only a generic status and open a nonexported page;
they never directly clear content. POST_NOTIFICATIONS is requested only when the
user enables notifications. Denial does not enable any substitute collection or
background service. Channels and PendingIntents are bounded and stale intents
are cancelled. There is no foreground service, polling or boot receiver.

Confirmation targets the current system clipboard and names the destructive
effect. Optional system authentication creates a one-use grant, followed by a
foreground confirmation; a callback alone never clears anything. New clips,
settings changes, expiry, recreation and leaving the confirmation page invalidate
authorization. Requests carry opaque in-memory tickets, not content. Existing
authentication is reused with a distinct, allowlisted prompt title.

## Limits

The feature only tries to reduce how long accidentally copied content remains in
the system clipboard and helps the user clear it. It cannot prevent access at the
instant of writing. The same statement is visible on the settings page, which
also states that the feature defaults off.

No public compare-and-clear operation exists. Timestamp and generation checks
reduce stale actions but do not eliminate the race between the final check and
clear. A new item can be cleared in that interval. Timestamps are not unique
security identities, callbacks do not prove user intent, and process/OEM limits
can interrupt observation. Clearing cannot revoke earlier copies or histories.

## Validation

Tests cover default-off behavior, option combinations, baseline/duplicate/empty
events, missing default-IME status, stale tickets, denied authentication, changed
clips, cancellation and platform failures. Instrumentation checks preferences,
granular layouts, notification permission declarations and the isolated platform
adapter. Real device credential/biometric flows and vendor callback behavior still
require device acceptance testing.
