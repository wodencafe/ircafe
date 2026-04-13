# IRCv3 Optional Feature Rollout

## Status

- Completed rollout checkpoint
- Scope: finish the remaining optional IRCv3 feature cleanup after the registry and plugin-aware metadata work
- Goal: move optional feature behavior behind the same small seam without pushing transport-critical logic into opaque plugins
- Result:
  - `chathistory` now has a shared feature-support seam across commands, history loading, and message-action gating
  - `multiline` now has shared availability/limit support at the app layer plus shared backend token/limit helpers
  - runtime capability normalization is catalog-backed, plugin-aware, and conflict-guarded
  - plugin IRCv3 metadata conflicts now degrade to built-ins and surface a plugin problem instead of breaking catalog startup
  - final sweep completed with one last UI alias fix for `reply` and `draft/typing`

## Deliberate Core Boundaries

These stay in core code by design:

- transport semantics:
  - `message-tags`
  - `batch`
  - `server-time`
  - `echo-message`
  - `labeled-response`
  - `standard-replies`
- backend-negotiated feature ports:
  - `IrcClientService`
  - `IrcNegotiatedFeaturePort`
  - backend-specific availability and limit state in PircbotX, Quassel, and Matrix
- explicitly experimental behavior:
  - `message-edit`

## Current Baseline

These pieces are already done:

- requestable capability metadata is centralized in the IRCv3 extension registry/catalog
- tag-only features are no longer treated as negotiated requestable capabilities
- `message-edit` is isolated as experimental
- runtime capability normalization can use plugin-provided aliases
- optional feature seams now exist for:
  - `read-marker`
  - `message-redaction`

This rollout picks up from there and focuses on the remaining optional features that still have behavior spread across multiple layers.

## Working Rules

- Keep core transport semantics in core code:
  - `message-tags`
  - `batch`
  - `server-time`
  - `echo-message`
  - `labeled-response`
  - `standard-replies`
- Use the new seam for optional feature availability, help text, and command gating first.
- Delay parser/transport extraction until the feature has a stable behavior seam.
- Prefer incremental slices that keep `architectureTest` and focused tests green.

## Phase 1: Chathistory Command Seam

Status:

- Completed

Purpose:

- move command-facing `chathistory` availability and help messaging behind a dedicated support class

Steps:

1. Add `Ircv3ChatHistoryFeatureSupport` under `app/api`.
2. Give it:
   - canonical feature id
   - negotiated availability check
   - optional ZNC playback visibility for later phases
   - standard unavailable/help messages
3. Wire outbound `/chathistory` command handling through that support.
4. Stop duplicating direct `irc.isChatHistoryAvailable(...)` checks in the outbound command path.
5. Add focused tests for:
   - negotiated availability
   - ZNC playback fallback visibility
   - outbound command refusal/help when `chathistory` is unavailable

Primary files:

- `src/main/java/cafe/woden/ircclient/app/api/Ircv3ChatHistoryFeatureSupport.java`
- `src/main/java/cafe/woden/ircclient/app/outbound/chathistory/OutboundChatHistoryCommandService.java`
- `src/main/java/cafe/woden/ircclient/app/outbound/chathistory/OutboundChatHistoryRequestSupport.java`

Exit criteria:

- outbound `/chathistory` flows no longer own raw availability logic
- help and unavailable messaging come from one place

Verification:

- focused `test` task for chathistory command/support tests
- `architectureTest`

## Phase 2: Chathistory History Loader Adoption

Status:

- Completed

Purpose:

- reuse the same support seam in history paging services without changing transport semantics

Steps:

1. Adopt `Ircv3ChatHistoryFeatureSupport` in local/remote history services where it improves duplicated availability checks.
2. Keep ZNC fallback behavior explicit in those services.
3. Do not hide DB replay or remote wait/timeout logic behind the support seam.
4. Centralize repeated “remote history available or not” decisions where it reduces duplication.

Primary files:

- `src/main/java/cafe/woden/ircclient/logging/history/DbChatHistoryService.java`
- `src/main/java/cafe/woden/ircclient/logging/history/RemoteOnlyChatHistoryService.java`

Exit criteria:

- DB-backed and remote-only history paths use the shared support for capability checks where practical
- ZNC fallback remains explicit and debuggable

Verification:

- focused history/logging tests
- `architectureTest test`

## Phase 3: Multiline Availability And Planning Seam

Status:

- Completed

Purpose:

- separate multiline availability/limit reasoning from the concrete transport implementation

Steps:

1. Add `Ircv3MultilineFeatureSupport` under `app/api`.
2. Move shared multiline availability and negotiated-limit reasoning into it.
3. Rewire app-level send planning to use the support seam.
4. Keep backend-specific raw `BATCH` send behavior in backend code.

Primary files:

- `src/main/java/cafe/woden/ircclient/app/outbound/messaging/OutboundMultilineMessageSupport.java`
- `src/main/java/cafe/woden/ircclient/app/api/Ircv3MultilineFeatureSupport.java`

Exit criteria:

- app-level multiline decision making no longer reaches directly into negotiated-feature state
- user-facing fallback reasons are produced from one place

Verification:

- focused outbound multiline tests
- `architectureTest`

## Phase 4: Multiline Transport Cleanup

Status:

- Completed

Purpose:

- remove duplicated backend-side multiline limit and request-token logic while keeping transport behavior in core

Steps:

1. Audit PircbotX multiline max-bytes/max-lines handling.
2. Audit Quassel multiline capability/limit parsing.
3. Extract only the duplicated limit/token logic that is safe to share.
4. Keep actual raw send/parsing behavior backend-local.

Primary files:

- `src/main/java/cafe/woden/ircclient/irc/pircbotx/client/PircbotxMultilineMessageSupport.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/state/PircbotxConnectionState.java`
- `src/main/java/cafe/woden/ircclient/irc/quassel/QuasselCoreIrcClientService.java`

Exit criteria:

- multiline availability and limit reasoning are consistent across app/backend paths
- backend-specific transport code remains easy to inspect

Verification:

- focused PircbotX/Quassel multiline tests
- `test`

## Phase 5: Normalization And Fallback Cleanup

Status:

- Completed

Purpose:

- reduce now-redundant built-in alias helpers after the runtime registry path is proven

Steps:

1. Audit remaining uses of `Ircv3CapabilityNameSupport`.
2. Keep it only as a deliberate built-in fallback if still needed.
3. Remove duplicated hard-coded alias knowledge where the runtime resolver now owns it.
4. Add guardrail tests for plugin/built-in conflict behavior.

Primary files:

- `src/main/java/cafe/woden/ircclient/util/Ircv3CapabilityNameSupport.java`
- `src/main/java/cafe/woden/ircclient/config/api/Ircv3CapabilityNameResolverPort.java`
- `src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionCatalog.java`

Exit criteria:

- alias normalization responsibility is explicit
- conflict behavior is test-covered

Verification:

- focused resolver/catalog tests
- `test`

## Phase 6: Final Sweep

Status:

- Completed

Purpose:

- confirm the codebase now models optional IRCv3 features consistently

Steps:

1. Re-scan the repo for feature-specific availability logic that bypasses the support seam.
2. Re-scan UI/help/network-info copy for protocol-model drift.
3. Confirm experimental features remain explicitly marked.
4. Update the higher-level IRCv3 plan document with completed status and deferred items.

Exit criteria:

- no obvious bypasses remain for the migrated features
- protocol terminology is consistent across command/help/UI paths

Final sweep notes:

- no further production bypasses were found for migrated `chathistory` or `multiline` behavior
- the last UI drift fix was to treat `reply` the same as `draft/reply` when normalizing staged drafts
- typing-state normalization now also reacts to `draft/typing`
- `message-edit` remains explicitly experimental

Verification:

- `./gradlew spotlessApply architectureTest test`

## Recommended Order

Implement in this order:

1. `chathistory` command seam
2. `chathistory` history-loader adoption
3. `multiline` availability/planning seam
4. `multiline` transport cleanup
5. normalization/fallback cleanup
6. final sweep

## Recommended Follow-Up

If follow-on work is needed, the next useful slices are:

- extension-author documentation for the IRCv3 provider SPI
  - see [ircv3-extension-provider-guide.md](/home/chris/Downloads/ircclient/ircafe/docs/ircv3-extension-provider-guide.md)
- one small example plugin/provider fixture beyond the existing tests
- optional future cleanup of built-in fallback helpers if the default resolver path is no longer needed outside tests
