package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** SPI provider for the IRCv3 message-redaction draft extension. */
public final class Ircv3MessageRedactionExtensionProvider
    implements Ircv3ExtensionDefinitionProvider {

  @Override
  public String providerId() {
    return "message-redaction";
  }

  @Override
  public int sortOrder() {
    return 220;
  }

  @Override
  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
    return List.of(
        Ircv3ExtensionProviderSupport.capability(
            "message-redaction",
            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
            "draft/message-redaction",
            "message-redaction",
            "Message redaction (draft)",
            Ircv3ExtensionRegistry.UiGroup.CONVERSATION,
            300,
            "Allows delete/redaction updates for messages.",
            "draft/message-redaction"));
  }

  @Override
  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return List.of(
        Ircv3ExtensionProviderSupport.feature(
            400,
            "Message redaction",
            List.of(),
            List.of("message-redaction", "draft/message-redaction")));
  }
}
