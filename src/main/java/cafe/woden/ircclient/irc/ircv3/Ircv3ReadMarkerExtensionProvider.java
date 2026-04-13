package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** SPI provider for the IRCv3 read-marker draft extension. */
public final class Ircv3ReadMarkerExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "read-marker";
  }

  @Override
  public int sortOrder() {
    return 200;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "read-marker",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "draft/read-marker",
            "read-marker",
            "Read markers (draft)",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            240,
            "Enables read-position markers on servers that support them.",
            "draft/read-marker"));
  }

  @Override
  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of(
        Ircv3ExtensionProviderSupport.feature(
            700, "Read markers", List.of(), List.of("read-marker", "draft/read-marker")));
  }
}
