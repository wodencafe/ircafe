package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Built-in provider for core history-related IRCv3 transport capabilities. */
public final class Ircv3CoreHistoryExtensionProvider implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "core-history";
  }

  @Override
  public int sortOrder() {
    return 230;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "batch",
            Ircv3ExtensionRegistry.SpecStatus.STABLE,
            "batch",
            Ircv3ExtensionRegistry.UiGroup.HISTORY,
            410,
            "Groups related events into coherent batches (useful for playback/history)."));
  }
}
