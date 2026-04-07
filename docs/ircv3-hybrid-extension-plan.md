# IRCv3 Hybrid Extension Plan

## Status

- Proposed
- Scope: internal refactor plan for IRCv3 capability and tag-feature handling
- Goal: improve IRCv3 spec correctness and reduce duplication without making core protocol handling opaque

## Why We Are Doing This

We recently cleaned up `REQUESTABLE_CAPABILITIES` after finding that the codebase mixed:

- real `CAP REQ` capabilities
- IRCv3 client tags that are not negotiated capabilities
- draft capabilities requested under final names
- experimental work such as `message-edit`

That cleanup fixed the immediate protocol issue, but the surrounding code still has duplicated metadata and mixed models:

- requestable capability metadata is split across multiple classes
- tag-based features and capability-based features are still partially conflated
- experimental features are not clearly separated from published IRCv3 behavior
- some connection-state and diagnostic paths still model tags as if they were CAP-negotiated

This plan keeps the protocol-critical parts in core code while making optional IRCv3 features easier to isolate, reason about, and evolve.

## Canonical References

- IRCv3 specs index: <https://ircv3.net/irc/>
- IRCv3 registry: <https://ircv3.net/registry>
- Message tags: <https://ircv3.net/specs/extensions/message-tags>
- Reply tag: <https://ircv3.net/specs/client-tags/reply.html>
- React tag: <https://ircv3.net/specs/client-tags/react>
- Typing tag: <https://ircv3.net/specs/client-tags/typing.html>

## Architecture Decision

We will use a hybrid approach:

- Keep core transport and negotiation semantics in the main codebase.
- Extract a small internal IRCv3 extension registry/SPI for metadata and optional feature handlers.
- Delay external `ServiceLoader` plugin support until there is a real need for third-party or experimental extensions.

This is intentionally not a full pluginization of IRCv3 handling.

## Why Not Make Every IRCv3 Feature a Plugin

A full SPI conversion would add complexity in the most protocol-sensitive part of the app.

Core IRCv3 features such as `message-tags`, `batch`, `server-time`, `echo-message`, `standard-replies`, and `labeled-response` affect:

- parsing
- replay ordering
- history correctness
- diagnostics
- connection-state transitions
- user-visible consistency across backends

Those are not good candidates for opaque runtime plugins. They need to stay easy to inspect, debug, and test in the main codebase.

## What Should Stay In Core

These should remain built-in and non-optional in architecture terms:

- capability negotiation plumbing
- canonical capability normalization
- connection-state tracking for real negotiated capabilities
- core IRCv3 parser/tag parser support
- transport-critical capabilities:
  - `message-tags`
  - `batch`
  - `server-time`
  - `echo-message`
  - `labeled-response`
  - `standard-replies`
  - `cap-notify`

Other long-standing core capabilities such as `multi-prefix`, `away-notify`, `account-notify`, `extended-join`, `setname`, `chghost`, `monitor`, and `extended-monitor` can still use the shared registry metadata, but their negotiation behavior should remain plainly visible in core.

## What Should Move Behind An Internal Extension Registry

Good first candidates:

- `read-marker`
- `multiline`
- `chathistory`
- `message-redaction`
- tag-feature descriptors for:
  - `reply`
  - `react`
  - `unreact`
  - `typing`
  - `channel-context`

Special case:

- `message-edit` should be isolated as experimental or removed from the IRC backend path entirely until there is a stable spec basis for it.

## Current Code Seams To Unify

These are the main places where IRCv3 metadata and feature behavior are currently scattered:

- `src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3CapabilityCatalog.java`
- `src/main/java/cafe/woden/ircclient/ui/settings/Ircv3PanelSupport.java`
- `src/main/java/cafe/woden/ircclient/ui/servertree/view/ServerTreeNetworkInfoDialogBuilder.java`
- `src/main/java/cafe/woden/ircclient/app/core/MediatorOutboundUiActionHandler.java`
- `src/main/java/cafe/woden/ircclient/config/RuntimeConfigStore.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/state/PircbotxConnectionState.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/PircbotxCapabilityNegotiationSupport.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/PircbotxTagSignalSupport.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/client/PircbotxAvailabilitySupport.java`
- `src/main/java/cafe/woden/ircclient/irc/pircbotx/client/PircbotxCapabilityCommandSupport.java`
- `src/main/java/cafe/woden/ircclient/irc/quassel/QuasselCoreIrcClientService.java`

## Immediate Cleanup Backlog From The Current Scan

These are the concrete leftovers this plan is intended to address:

- stale capability metadata in `Ircv3PanelSupport` for tag-only or non-requestable items such as `typing`, `draft/reply`, `draft/react`, `draft/unreact`, `draft/channel-context`, `sts`, and `message-edit`
- stale CAP-state tracking in `PircbotxConnectionState` and related parse tests for tag-only features
- lingering `message-edit` behavior presented through the IRC backend even though it is not part of the published IRCv3 surface we are targeting
- duplicated normalization logic for draft/final aliases across UI, config, and backend code

## Existing Seams We Should Reuse

We do not need to invent a brand-new extension-loading pattern from scratch.

Relevant existing patterns in the codebase:

- `src/main/java/cafe/woden/ircclient/util/PluginServiceLoaderSupport.java`
  - existing Java `ServiceLoader` plugin loading support
- `src/main/java/cafe/woden/ircclient/irc/backend/BackendRoutingIrcClientService.java`
  - example of loading built-in plus plugin-provided services
- `src/main/java/cafe/woden/ircclient/app/outbound/backend/OutboundBackendFeatureRegistry.java`
  - example of a small registry over built-in implementations

The preferred order is:

1. introduce a built-in registry first
2. stabilize the internal SPI
3. consider external plugin loading only after the internal model is proven

## Target Model

Introduce a built-in registry centered on a small definition interface, for example:

```java
public interface Ircv3ExtensionDefinition {
  String id();
  Ircv3ExtensionKind kind();
  SpecStatus specStatus();
  List<String> canonicalNames();
  List<String> requestTokens();
  List<String> aliases();
  List<String> dependencies();
  boolean requestable();
  boolean experimental();
  Ircv3UiMetadata uiMetadata();
}
```

Supporting concepts:

- `Ircv3ExtensionKind`
  - `CAPABILITY`
  - `TAG_FEATURE`
  - `EXPERIMENTAL`
- `SpecStatus`
  - `STABLE`
  - `DRAFT`
  - `EXPERIMENTAL`
- `Ircv3UiMetadata`
  - label
  - group
  - impact summary
  - sort order

Then add a registry:

```java
public interface Ircv3ExtensionRegistry {
  List<Ircv3ExtensionDefinition> all();
  Optional<Ircv3ExtensionDefinition> byName(String name);
  List<Ircv3ExtensionDefinition> requestableCapabilities();
  List<Ircv3ExtensionDefinition> visibleFeatures();
}
```

## Design Rules

The registry must encode these rules directly:

- only real capabilities may appear in `requestableCapabilities()`
- draft capabilities are requested under their `draft/...` names until ratified
- tag features are never surfaced as `CAP REQ` targets
- aliases are accepted for parsing and compatibility, not used to decide requestability
- experimental extensions are isolated from published IRCv3 features

## Suggested Internal Extension Types

We do not need every extension to own parsing or transport hooks immediately.

Phase 1 should focus on metadata centralization only.

Later, optional handler interfaces can be added:

```java
public interface Ircv3CapabilityLifecycleHandler {
  default void onCapOffered(...) {}
  default void onCapAck(...) {}
  default void onCapDel(...) {}
}

public interface Ircv3TagFeatureHandler {
  default void onInboundTags(...) {}
  default void onOutboundAvailabilityCheck(...) {}
}
```

The first migration does not need these hooks if metadata extraction alone removes duplication.

## Phased Plan

### Phase 0: Lock In Current Protocol Rules

Purpose:

- prevent backsliding while we refactor

Work:

- add or tighten tests asserting that `requestableCapabilities()` includes only real capabilities
- add tests that tag features do not appear in toggleable capability lists
- add tests that draft-only capabilities map to draft request tokens
- add tests for `typing` availability based on `message-tags` + `CLIENTTAGDENY`, not CAP ACK

Exit criteria:

- a failing test would catch any reintroduction of `typing`, `reply`, `react`, `channel-context`, `message-edit`, or `sts` into requestable capability UI/negotiation paths

Verification:

- `./gradlew test`

### Phase 1: Introduce The Internal Registry

Purpose:

- create one canonical source of truth for IRCv3 feature metadata

Work:

- add `Ircv3ExtensionDefinition` and `Ircv3ExtensionRegistry`
- implement built-in definitions for the currently supported IRCv3 capabilities and tag features
- reimplement `Ircv3CapabilityCatalog` as a thin adapter over the registry

Primary migrations:

- `Ircv3CapabilityCatalog`
- `Ircv3PanelSupport`
- `ServerTreeNetworkInfoDialogBuilder`
- `MediatorOutboundUiActionHandler`

Exit criteria:

- labels, grouping, sort order, requestability, and canonical-name normalization all come from one place
- duplicated switch statements for capability metadata are removed or drastically reduced

Verification:

- `./gradlew test`
- add focused tests for registry normalization and UI metadata consumers

### Phase 2: Separate Capabilities From Tag Features Explicitly

Purpose:

- remove the remaining mental model that “all IRCv3 features are capabilities”

Work:

- audit all feature checks to classify them as:
  - negotiated capability
  - tag-based feature on top of `message-tags`
  - experimental feature
- update UI text and diagnostics to reflect that distinction
- remove stale capability metadata for `typing`, `reply`, `react`, `unreact`, `channel-context`, and `message-edit`

Primary migrations:

- `Ircv3PanelSupport`
- `ServerTreeNetworkInfoDialogBuilder`
- related help and status copy

Exit criteria:

- no UI or diagnostics path describes tag-only features as requestable negotiated capabilities

Verification:

- `./gradlew test`

### Phase 3: Clean Up Connection-State Modeling

Purpose:

- stop storing incorrect CAP ACK state for tag-only features

Work:

- remove or quarantine CAP-tracking fields for:
  - `draft/reply`
  - `draft/react`
  - `draft/unreact`
  - `draft/channel-context`
  - `typing`
  - `draft/typing`
- keep real state where it belongs:
  - `message-tags` negotiated or not
  - `CLIENTTAGDENY` typing policy known or not
- update logs and capability snapshots accordingly

Primary migrations:

- `PircbotxConnectionState`
- `PircbotxRegistrationLifecycleHandler`
- parse tests that currently assert bogus ACK state for tag-only features

Exit criteria:

- connection snapshots only expose negotiated capability state for real capabilities
- tag-based feature availability is derived from transport prerequisites and server policy

Verification:

- `./gradlew test`
- if module boundaries move, also run `./gradlew architectureTest`

### Phase 4: Migrate One Optional Feature As A Pilot

Purpose:

- validate the extension seam on a low-risk feature before expanding it

Recommended pilot:

- `read-marker`

Why `read-marker`:

- it is a real capability
- its behavior is narrower than history or multiline
- it has clear outbound and availability boundaries

Work:

- add a built-in `ReadMarkerExtensionDefinition`
- if needed, add a small lifecycle/command handler interface for optional feature behavior
- route `read-marker` normalization and UI metadata through the registry

Exit criteria:

- at least one optional feature is handled end-to-end through the new seam
- the seam is small enough to repeat without over-engineering

Verification:

- focused `read-marker` tests
- `./gradlew test`

### Phase 5: Decide The Fate Of `message-edit`

Purpose:

- resolve the remaining mismatch between published IRCv3 support and in-app experimental behavior

Options:

- remove IRC-backend `message-edit` support from the public feature set
- keep it behind an explicit experimental extension definition
- move it to backend-specific behavior that is not presented as standard IRCv3 support

Recommendation:

- do not keep `message-edit` mixed into normal IRCv3 feature handling

Exit criteria:

- the app no longer presents `message-edit` as if it were part of the normal supported IRCv3 surface

Verification:

- `./gradlew test`

### Phase 6: External Plugin Support Only If Needed

Purpose:

- support third-party or local experimental IRCv3 extensions without destabilizing core behavior

Preconditions:

- the internal registry is stable
- at least one real use case exists for third-party extension loading
- plugin APIs are narrow and versioned

Implementation direction:

- use the existing `PluginServiceLoaderSupport`
- load only non-core extension definitions or feature handlers
- reject plugins that try to replace core transport semantics

Good plugin candidates:

- experimental client-tag features
- bouncer-specific metadata enrichments
- local-only diagnostics helpers

Bad plugin candidates:

- message parsing core
- batch handling
- server-time ordering
- connection-state fundamentals

Exit criteria:

- plugin-provided extensions are additive and optional
- disabling all plugins leaves a fully correct built-in IRCv3 stack

Verification:

- `./gradlew test`
- targeted plugin loading tests if this phase is implemented

## Recommended First Implementation Slice

Do this first:

1. Add the internal registry and built-in definitions.
2. Rewire `Ircv3CapabilityCatalog`, `Ircv3PanelSupport`, and `ServerTreeNetworkInfoDialogBuilder` to consume it.
3. Remove stale switch cases for non-requestable items from those UI metadata paths.

Why this first:

- it is behavior-preserving
- it centralizes duplicated knowledge immediately
- it reduces the chance of future protocol drift
- it does not require a risky parser or transport rewrite

## Migration Checklist

Use this checklist during implementation:

- preserve Java 25 compatibility
- keep EDT-sensitive UI code free of blocking work
- do not move heavy parsing into runtime-discovered plugins
- add tests whenever capability/tag behavior changes
- keep backward-compatibility parsing for draft/final aliases where needed
- prefer removing stale branches over adding more normalization layers
- avoid exposing experimental features as standard IRCv3 support

## Risks And Mitigations

Risk:

- the SPI becomes too abstract and harder to debug than the current direct code

Mitigation:

- keep phase 1 metadata-only
- do not add lifecycle hooks until a real migration needs them

Risk:

- core behavior gets hidden behind plugins and becomes fragile

Mitigation:

- keep negotiation, parser, and connection-state fundamentals built-in

Risk:

- alias support regresses while normalizing draft/final names

Mitigation:

- add dedicated normalization tests before moving logic

Risk:

- message-edit remains ambiguous and keeps leaking into the main IRCv3 model

Mitigation:

- treat it as a separate phase with an explicit decision

## Definition Of Done For This Refactor Track

This effort is done when:

- there is one canonical source of truth for IRCv3 feature metadata
- requestable capabilities are distinct from tag-based features and experimental features
- connection-state modeling tracks only real negotiated capabilities
- optional IRCv3 features can be added through a small internal seam
- external plugin loading is optional and only used for additive, non-core features

## Notes For Future Implementers

If a future change adds a new IRCv3 feature, answer these questions before touching code:

1. Is it a real negotiated capability, a client tag, or an experimental behavior?
2. Is it published, draft, or local-only?
3. Should it be requestable, parse-only, or hidden behind an experimental switch?
4. Does it affect core transport semantics or only optional behavior?
5. Can it be modeled as metadata only, or does it need a feature handler?

If those answers are not clear, stop and resolve them before adding new request paths or UI toggles.
