# ADR 0008: Optional clipboard overlay and explicit inspection

Status: accepted

## Context

Physical-device feedback found that status-bar notifications were easy to miss,
selection actions were absent on an iQOO Neo9 running Android 16, and clipboard
history was confused with the Android current item. The user approved an
independent overlay, including SYSTEM_ALERT_WINDOW, with education before asking
for system permission. Source menu support and vendor behavior remain unverified
on that physical device.

## Decision

Keep monitoring, notifications, keyboard reminders, overlay reminders, automatic
cleanup and authentication separate. The overlay defaults off, uses a bounded
window and lifetime, and contains no secret material. Every route into overlay
permission settings first explains its purpose, data limits and revocation.
User confirmation opens system settings; the app cannot grant the permission.

The existing IME/application process owns the window. It does not acquire a
foreground service, accessibility capability, polling loop or background restart
mechanism. Screen-off, permission revocation and entering the app dismiss it.
Window taps only navigate to the existing confirmation flow. Automatic cleanup
events receive distinct in-memory identities so successive copies remain visible
even if their resulting status is identical.

An explicit foreground inspection can target existing current content without a
listener callback. It checks only metadata and issues a new one-use confirmation
ticket. Even automatic mode requires confirmation for this inspection; observation
mode allows this user-requested action. Request cancellation and configuration
leases still protect asynchronous work. No private-vault format changes occur.

## Limits and alternatives

Public clipboard metadata APIs may return null for either empty or inaccessible
content. The UI reports that ambiguity and distinguishes explicit denial and
device locking. Hidden AppOps APIs and reflection are not used. Vendor histories,
favorites and cloud copies cannot be cleared through the public current-item API.

The existing PROCESS_TEXT and SEND entries remain input-only. Tests must include a
separate-UID source using native text selection/copy and sharing; same-application
tests cannot establish cross-app behavior. Overlay permission does not make a
source application expose selected text. Arbitrary menu injection and history
deletion remain outside the ordinary, unrooted application model.

High-importance notifications alone would still depend on system notification
presentation. Full-screen intents and background authentication are inappropriate
for this reminder. A permanent foreground service would add permissions and
power/lifecycle costs without guaranteeing clipboard callbacks or access.

## Validation

Verify default-off and persisted options, permission education cancellation and
approval, denial and revocation, single-window replacement, dismissal, timeout,
lock screen, foreground navigation, stale tickets, repeated automatic clears,
explicit inspection with missing callbacks, cross-app copy and text-share entry.
Inspect narrow and landscape layouts in both themes and languages. Retain a
separate physical-device acceptance step for OEM selection menus and overlays.
