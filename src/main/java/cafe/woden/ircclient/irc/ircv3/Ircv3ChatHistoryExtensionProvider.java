package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** SPI provider for the IRCv3 chathistory draft extension. */
public final class Ircv3ChatHistoryExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "chathistory";
  }

  @Override
  public int sortOrder() {
    return 240;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "chathistory",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "draft/chathistory",
            "chathistory",
            "Chat history (draft)",
            Ircv3ExtensionRegistry.UiGroup.HISTORY,
            430,
            "Enables server-side history retrieval and backfill features.",
            "draft/chathistory"));
  }

  @Override
  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of(
        Ircv3ExtensionProviderSupport.feature(
            500,
            "History",
            List.of(),
            List.of("chathistory", "draft/chathistory", "znc.in/playback")));
  }
}
