# ADR 0005: Validated and transactional user lexicon updates

Status: accepted

## Context

Manual additions could exceed the reader's 20,000-term limit, producing a file
that the next process could not load. Imports used a recursive general JSON
parser without structural depth limits, and parser failures could reach the UI.
Repository mutations published memory before encrypted persistence succeeded.
Direct Android storage construction also prevented deterministic fault tests.

## Decision

- Keep format 1, the current file name, key alias and AES-GCM envelope.
- Introduce a minimal encrypted byte-store port in security. Production remains
  backed by EncryptedFileStore; tests can inject failures without accessing user
  data or application key aliases.
- Separate fixed-schema streaming validation from repository state ownership.
  Reject unknown/duplicate fields, nested values, invalid scalars and duplicate
  phrase identities before mutation. Discard parser messages and causes.
- Serialize background updates, persist a new snapshot first, and only then
  publish it. Advance the clear generation before waiting for active writes.
- Bound additions/merged imports to 20,000 terms and 5 MiB of compact JSON.
  Preserve local IDs for matching phrases and assign fresh IDs to new imports.
- Confirm import merge rules and plaintext export scope before file selection.

## Consequences

Invalid previously accepted files fail closed and are never automatically
rewritten. Valid format-1 data needs no migration. Oversized manual updates fail
instead of evicting existing phrases. Compact exports of new data fit the import
byte limit. The application still owns asynchronous scheduling; the repository
does not perform work on the IME thread or maintain a second task queue.

## Alternatives

Trimming manual additions hides data loss and does not protect failed writes.
Keeping JSONObject with a pre-scan duplicates parsing logic and is harder to
audit than accepting the fixed schema through Android's existing JsonReader.
New parser dependencies and a persisted format revision provide no necessary
benefit for this change.
