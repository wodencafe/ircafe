package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** SPI provider for the IRCv3 multiline draft extension. */
public final class Ircv3MultilineExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "multiline";
  }

  @Override
  public int sortOrder() {
    return 210;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "multiline",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "draft/multiline",
            "multiline",
            "Multiline messages (draft)",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            220,
            "Allows sending and receiving multiline messages as a single logical message.",
            "draft/multiline"));
  }
}
