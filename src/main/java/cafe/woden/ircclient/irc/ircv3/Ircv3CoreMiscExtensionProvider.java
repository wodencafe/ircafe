package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Built-in provider for remaining non-transport IRCv3 metadata and tag features. */
public final class Ircv3CoreMiscExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "core-misc";
  }

  @Override
  public int sortOrder() {
    return 300;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "znc.in/playback",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "ZNC playback",
            Ircv3ExtensionRegistry.UiGroup.HISTORY,
            440,
            "Requests playback support from ZNC bouncers when available."),
        Ircv3ExtensionProviderSupport.capability(
            "account-tag",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Account tags",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            70,
            "Attaches account metadata to messages for richer identity info."),
        Ircv3ExtensionProviderSupport.capability(
            "userhost-in-names",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "USERHOST in NAMES",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            150,
            "May provide richer host/user identity details during names lists."),
        Ircv3ExtensionProviderSupport.nonRequestableCapability(
            "sts",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Strict transport security",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            20,
            "Learns strict transport policy and upgrades future connects for this host to TLS."),
        Ircv3ExtensionProviderSupport.tagFeature(
            "reply",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Replies",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            250,
            "Reply threading is carried by message tags on top of message-tags transport.",
            "draft/reply"),
        Ircv3ExtensionProviderSupport.tagFeature(
            "react",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "Reactions",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            260,
            "Reactions are carried by message tags on top of message-tags transport.",
            "draft/react"),
        Ircv3ExtensionProviderSupport.tagFeature(
            "unreact",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "Reaction removal",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            265,
            "Reaction removals are carried by message tags on top of message-tags transport.",
            "draft/unreact"),
        Ircv3ExtensionProviderSupport.tagFeature(
            "typing",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Typing",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            230,
            "Typing indicators are sent as client-only tags and depend on CLIENTTAGDENY policy.",
            "draft/typing"),
        Ircv3ExtensionProviderSupport.tagFeature(
            "channel-context",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "Channel context",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            245,
            "Channel-context is a client tag layered on top of message-tags transport.",
            "draft/channel-context"),
        Ircv3ExtensionProviderSupport.experimental(
            "message-edit",
            "Message edits (experimental)",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            280,
            "Experimental message editing support; not part of the published IRCv3 surface.",
            "draft/message-edit"));
  }

  @Override
  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of(
        Ircv3ExtensionProviderSupport.feature(100, "Replies", List.of("message-tags"), List.of()),
        Ircv3ExtensionProviderSupport.feature(200, "Reactions", List.of("message-tags"), List.of()),
        Ircv3ExtensionProviderSupport.feature(
            300, "Reaction removal", List.of("message-tags"), List.of()),
        Ircv3ExtensionProviderSupport.feature(600, "Typing", List.of("message-tags"), List.of()));
  }
}
