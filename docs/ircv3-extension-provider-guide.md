# IRCv3 Extension Provider Guide

## Purpose

IRCafe can load additional IRCv3 extension metadata from built-ins and installed plugin jars.

This SPI is for metadata only. It currently lets a provider contribute:

- extension definitions
- request-token and preference-key aliases
- visible feature metadata for the network-info UI

It does not currently let a plugin inject protocol behavior, parser logic, transport handling, or
Spring-managed feature services.

## When To Use This

Use an `Ircv3ExtensionDefinitionProvider` when you want to add metadata for:

- an experimental IRCv3 capability
- a draft capability or tag feature not built into IRCafe yet
- a bouncer-specific or backend-specific IRCv3-adjacent extension

Do not use this SPI for core transport semantics such as:

- `message-tags`
- `batch`
- `server-time`
- `echo-message`
- `labeled-response`
- `standard-replies`

Those remain part of the core application code.

## Current Boundaries

The provider SPI is defined by
[Ircv3ExtensionDefinitionProvider.java](/home/chris/Downloads/ircclient/ircafe/src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionDefinitionProvider.java).

Providers are loaded through:

- application classpath `ServiceLoader`
- installed plugin jars in the configured plugin directory

The runtime catalog is built by
[Ircv3ExtensionCatalog.java](/home/chris/Downloads/ircclient/ircafe/src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionCatalog.java).

Important limits:

- providers must be plain `ServiceLoader` classes with a public no-arg constructor
- providers are loaded at startup, not hot-reloaded
- metadata conflicts cause plugin metadata to be ignored and recorded as a plugin problem
- behavior is still implemented in core feature-support and backend classes

## What A Provider Can Contribute

### Extension Definitions

An extension definition describes one IRCv3 capability, tag feature, or experimental extension.

Each definition includes:

- `id`
- `kind`
- `specStatus`
- aliases
- request token
- preference key
- UI label/group/sort/help metadata

The public types come from
[Ircv3ExtensionRegistry.java](/home/chris/Downloads/ircclient/ircafe/src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionRegistry.java).

### Visible Features

Visible features are the user-facing features shown in network-info surfaces. These can depend on:

- `requiredAll`
- `requiredAny`

This lets you describe features such as “requires `message-tags` and one of these extension names”.

## Minimal Example

```java
package example.ircv3;

import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionRegistry;
import java.util.List;

public final class ExampleIrcv3Provider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "example-ircv3";
  }

  @Override
  public int sortOrder() {
    return 900;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        new Ircv3ExtensionRegistry.ExtensionDefinition(
            "example-cap",
            Ircv3ExtensionRegistry.ExtensionKind.CAPABILITY,
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            List.of("draft/example-cap"),
            "draft/example-cap",
            "example-cap",
            new Ircv3ExtensionRegistry.UiMetadata(
                "Example capability (draft)",
                Ircv3ExtensionRegistry.UiGroup.OTHER,
                900,
                "Adds example plugin-provided IRCv3 capability metadata.")));
  }

  @Override
  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of(
        new Ircv3ExtensionRegistry.FeatureDefinition(
            900,
            "Example feature",
            List.of("message-tags"),
            List.of("example-cap", "draft/example-cap")));
  }
}
```

## Service Registration

Your jar must include:

`META-INF/services/cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider`

with the fully qualified provider class name, for example:

```text
example.ircv3.ExampleIrcv3Provider
```

The application ships built-in providers through
[META-INF/services/cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider](/home/chris/Downloads/ircclient/ircafe/src/main/resources/META-INF/services/cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider).

An executable fixture matching this guide lives in
[Ircv3ExtensionProviderGuideFixtureTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionProviderGuideFixtureTest.java).

## Naming Rules

Choose names carefully.

- `id` should be the canonical feature identity inside IRCafe
- `requestToken` should be the exact token to use for `CAP REQ`
- `preferenceKey` should be the stable persisted key
- `aliases` should include accepted final/draft names and any compatible historical names

Recommended pattern for draft capabilities:

- `id`: final semantic name, for example `read-marker`
- `requestToken`: draft request token, for example `draft/read-marker`
- `preferenceKey`: stable final key, for example `read-marker`
- `aliases`: include both draft and final names

Recommended pattern for tag features:

- use `ExtensionKind.TAG_FEATURE`
- leave `requestToken` empty
- include draft and final tag aliases as needed

Recommended pattern for experimental extensions:

- use `ExtensionKind.EXPERIMENTAL`
- keep the UI label explicit about the experimental status

## Conflict Rules

The registry rejects conflicting metadata.

Conflicts include:

- duplicate provider ids
- duplicate extension names or aliases
- duplicate request tokens when they resolve to the same indexed name
- duplicate visible feature labels with different definitions

If a plugin jar introduces conflicting IRCv3 metadata:

- the runtime catalog falls back to built-in providers
- the problem is recorded through the installed-plugin diagnostics path

Relevant tests:

- [Ircv3ExtensionRegistryTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionRegistryTest.java)
- [Ircv3ExtensionCatalogTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionCatalogTest.java)

## What This Does Not Solve Yet

Adding metadata here does not automatically add:

- outbound command handling
- UI compose helpers
- backend availability checks
- parser support
- event projection
- transcript rendering behavior

If a new extension needs real behavior, add that separately in core code and decide whether it
belongs behind one of the existing feature-support seams.

## Verification Checklist

When adding a provider, verify:

1. the provider loads through the catalog
2. aliases normalize the way you expect
3. requestable capabilities appear only when they should
4. non-requestable tag features do not produce `CAP REQ` tokens
5. feature labels appear correctly in the network-info UI
6. plugin conflict cases fail safely

Useful tests to model from:

- [Ircv3CapabilityNameResolverAdapterTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3CapabilityNameResolverAdapterTest.java)
- [Ircv3ExtensionCatalogTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionCatalogTest.java)
- [Ircv3ExtensionProviderGuideFixtureTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionProviderGuideFixtureTest.java)
- [Ircv3ExtensionRegistryTest.java](/home/chris/Downloads/ircclient/ircafe/src/test/java/cafe/woden/ircclient/irc/ircv3/Ircv3ExtensionRegistryTest.java)
