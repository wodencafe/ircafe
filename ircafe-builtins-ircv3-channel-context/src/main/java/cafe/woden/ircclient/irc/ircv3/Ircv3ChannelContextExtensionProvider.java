package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionContribution;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionKind;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3SpecStatus;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiGroup;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiMetadata;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Set;

/** Built-in metadata and runtime provider for the IRCv3 channel-context tag feature. */
@AutoService({Ircv3ExtensionProvider.class, Ircv3InboundTagSignalProvider.class})
public final class Ircv3ChannelContextExtensionProvider
    implements Ircv3ExtensionProvider, Ircv3InboundTagSignalProvider {

  private static final String FEATURE = "channel-context";
  private static final String DRAFT_FEATURE = "draft/channel-context";

  @Override
  public String providerId() {
    return FEATURE;
  }

  @Override
  public int sortOrder() {
    return 280;
  }

  @Override
  public List<Ircv3ExtensionContribution> extensions() {
    return List.of(
        new Ircv3ExtensionContribution(
            FEATURE,
            Ircv3ExtensionKind.TAG_FEATURE,
            Ircv3SpecStatus.STABLE,
            List.of(DRAFT_FEATURE),
            "",
            FEATURE,
            new Ircv3UiMetadata(
                "Channel context",
                Ircv3UiGroup.CONVERSATION,
                245,
                "Channel-context is a client tag layered on top of message-tags transport.")));
  }

  @Override
  public Set<Ircv3InboundTagOperation> inboundTagOperations() {
    return Set.of(Ircv3InboundTagOperation.CHANNEL_CONTEXT);
  }

  @Override
  public List<Ircv3InboundTagSignal> parse(
      Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
    if (operation != Ircv3InboundTagOperation.CHANNEL_CONTEXT || request == null) {
      return List.of();
    }
    boolean privateMessageCommand =
        "PRIVMSG".equalsIgnoreCase(request.command())
            || "NOTICE".equalsIgnoreCase(request.command());
    String target =
        privateMessageCommand
            ? Ircv3ChannelContextPolicy.resolveTarget(
                request.tags(), request.rawTarget(), request.sourceNick())
            : Ircv3ChannelContextPolicy.resolveConversationTarget(
                request.rawTarget(), request.sourceNick());
    return target.isEmpty()
        ? List.of()
        : List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.CONVERSATION_TARGET, target));
  }
}
