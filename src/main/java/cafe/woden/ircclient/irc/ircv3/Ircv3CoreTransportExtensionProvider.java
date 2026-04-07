package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Built-in provider for the core IRCv3 transport and metadata capabilities. */
public final class Ircv3CoreTransportExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "core-transport";
  }

  @Override
  public int sortOrder() {
    return 100;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "multi-prefix",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "multi-prefix",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            130,
            "Preserves all nick privilege prefixes (not just the highest) in user data."),
        Ircv3ExtensionProviderSupport.capability(
            "cap-notify",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "CAP updates",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            140,
            "Allows capability change notifications after initial connection."),
        Ircv3ExtensionProviderSupport.capability(
            "invite-notify",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Invite notifications",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            145,
            "Receives invite events for channels you share without extra queries."),
        Ircv3ExtensionProviderSupport.capability(
            "away-notify",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Away status updates",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            90,
            "Tracks away/back state transitions for users."),
        Ircv3ExtensionProviderSupport.capability(
            "account-notify",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Account status updates",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            80,
            "Tracks account login/logout changes for users."),
        Ircv3ExtensionProviderSupport.capability(
            "monitor",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "MONITOR",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            155,
            "Lets IRCafe track online/offline state for monitored nicknames."),
        Ircv3ExtensionProviderSupport.capability(
            "extended-monitor",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Extended MONITOR",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            160,
            "Extends MONITOR presence notifications to additional events."),
        Ircv3ExtensionProviderSupport.capability(
            "extended-join",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Extended join data",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            100,
            "Adds account/realname metadata to join events when available."),
        Ircv3ExtensionProviderSupport.capability(
            "setname",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Setname updates",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            120,
            "Receives user real-name changes without extra lookups."),
        Ircv3ExtensionProviderSupport.capability(
            "chghost",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Hostmask changes",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            110,
            "Keeps hostmask/userhost identity changes in sync."),
        Ircv3ExtensionProviderSupport.capability(
            "message-tags",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Message tags",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            10,
            "Foundation for many IRCv3 features: carries structured metadata on messages."),
        Ircv3ExtensionProviderSupport.capability(
            "server-time",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Server timestamps",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            30,
            "Uses server-provided timestamps to improve ordering and replay accuracy."),
        Ircv3ExtensionProviderSupport.capability(
            "standard-replies",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Standard replies",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            60,
            "Provides structured success/error replies from the server."),
        Ircv3ExtensionProviderSupport.capability(
            "echo-message",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Echo own messages",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            40,
            "Server echoes your outbound messages, improving multi-client/bouncer consistency."),
        Ircv3ExtensionProviderSupport.capability(
            "labeled-response",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "Labeled responses",
            Ircv3ExtensionRegistry.UiGroup.CORE,
            50,
            "Correlates command responses with requests more reliably."));
  }
}
