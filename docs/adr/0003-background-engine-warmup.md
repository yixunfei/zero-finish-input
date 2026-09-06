# ADR 0003: Background engine warm-up and handoff

Status: accepted

## Context

Android 16 may call `onStartInput` on the latency-sensitive IME thread while
librime session creation or a language-pack dictionary scan is still cold. A
synchronous start delays the first frame and can make the keyboard appear
unresponsive. A worker result can also outlive the editor that requested it.

## Decision

`ZeroInputService` starts each session with an in-memory immediate engine. A
shared bounded single-thread executor serializes Rime initialization,
language-pack discovery, and engine preparation. `EngineWarmupCoordinator`
starts the candidate engine off the input thread and wraps it in
`PreparedInputEngine`. The controller accepts the wrapper only when the session
token, package name, language, selected pack, privacy snapshot, and empty
composition still match; otherwise ownership is closed immediately.

Between the worker and the IME looper, a small result-delivery owner retains the
wrapper until the callback runs. Service teardown, callback removal, callback
replacement, and handler-post rejection all close the still-owned wrapper; a
successful controller handoff consumes that ownership exactly once.

All application workers use finite queues. Cancellation purges queued futures,
and queue rejection never runs storage or native work on the IME input thread.
The user-requested personalization clear is a control operation: it evicts stale
optional work first and, only on the settings worker's durable wait path, may
make one final synchronous attempt if the executor refuses the control task.

## Consequences

- The keyboard can render and accept basic input before native/data-backed
  engines finish warming up.
- The first few keystrokes may use the documented fallback engine; an upgrade
  is retried after the composition becomes idle.
- Native engine creation is serialized with runtime initialization, reducing
  contention and making shutdown ordering explicit.
- Optional learning/history refresh work may be retried or dropped when a
  bounded queue is saturated; correctness and privacy boundaries are kept.
