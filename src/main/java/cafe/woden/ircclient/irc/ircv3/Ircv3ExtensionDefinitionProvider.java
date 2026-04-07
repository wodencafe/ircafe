package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Internal SPI for contributing IRCv3 extension metadata to the registry. */
public interface Ircv3ExtensionDefinitionProvider {

  String providerId();

  int sortOrder();

  default List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of();
  }

  default List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of();
  }
}
