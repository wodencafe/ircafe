package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Registers history/replay and message-mutation command handlers. */
@Component
final class HistoryMutationOutboundCommandRegistrar implements OutboundCommandRegistrar {

  private final OutboundChatHistoryCommandService outboundChatHistoryCommandService;
  private final OutboundReadMarkerCommandService outboundReadMarkerCommandService;
  private final OutboundHelpCommandService outboundHelpCommandService;
  private final OutboundUploadCommandService outboundUploadCommandService;
  private final OutboundMessageMutationCommandService outboundMessageMutationCommandService;
  private final OutboundSayQuoteCommandService outboundSayQuoteCommandService;

  HistoryMutationOutboundCommandRegistrar(
      OutboundChatHistoryCommandService outboundChatHistoryCommandService,
      OutboundReadMarkerCommandService outboundReadMarkerCommandService,
      OutboundHelpCommandService outboundHelpCommandService,
      OutboundUploadCommandService outboundUploadCommandService,
      OutboundMessageMutationCommandService outboundMessageMutationCommandService,
      OutboundSayQuoteCommandService outboundSayQuoteCommandService) {
    this.outboundChatHistoryCommandService =
        Objects.requireNonNull(
            outboundChatHistoryCommandService, "outboundChatHistoryCommandService");
    this.outboundReadMarkerCommandService =
        Objects.requireNonNull(
            outboundReadMarkerCommandService, "outboundReadMarkerCommandService");
    this.outboundHelpCommandService =
        Objects.requireNonNull(outboundHelpCommandService, "outboundHelpCommandService");
    this.outboundUploadCommandService =
        Objects.requireNonNull(outboundUploadCommandService, "outboundUploadCommandService");
    this.outboundMessageMutationCommandService =
        Objects.requireNonNull(
            outboundMessageMutationCommandService, "outboundMessageMutationCommandService");
    this.outboundSayQuoteCommandService =
        Objects.requireNonNull(outboundSayQuoteCommandService, "outboundSayQuoteCommandService");
  }

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(
        ParsedInput.ChatHistoryBefore.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryBefore(
                d, cmd.limit(), cmd.selector()));
    registry.register(
        ParsedInput.ChatHistoryLatest.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryLatest(
                d, cmd.limit(), cmd.selector()));
    registry.register(
        ParsedInput.ChatHistoryBetween.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryBetween(
                d, cmd.startSelector(), cmd.endSelector(), cmd.limit()));
    registry.register(
        ParsedInput.ChatHistoryAround.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryAround(
                d, cmd.selector(), cmd.limit()));
    registry.register(
        ParsedInput.MarkRead.class, (d, cmd) -> outboundReadMarkerCommandService.handleMarkRead(d));
    registry.register(
        ParsedInput.Help.class, (d, cmd) -> outboundHelpCommandService.handleHelp(cmd.topic()));
    registry.register(
        ParsedInput.Upload.class,
        (d, cmd) ->
            outboundUploadCommandService.handleUpload(d, cmd.msgType(), cmd.path(), cmd.caption()));
    registry.register(
        ParsedInput.ReplyMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleReplyMessage(
                d, cmd.messageId(), cmd.body()));
    registry.register(
        ParsedInput.ReactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleReactMessage(
                d, cmd.messageId(), cmd.reaction()));
    registry.register(
        ParsedInput.UnreactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleUnreactMessage(
                d, cmd.messageId(), cmd.reaction()));
    registry.register(
        ParsedInput.EditMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleEditMessage(
                d, cmd.messageId(), cmd.body()));
    registry.register(
        ParsedInput.RedactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleRedactMessage(
                d, cmd.messageId(), cmd.reason()));
    registry.register(
        ParsedInput.Quote.class,
        (d, cmd) -> outboundSayQuoteCommandService.handleQuote(d, cmd.rawLine()));
    registry.register(
        ParsedInput.Say.class, (d, cmd) -> outboundSayQuoteCommandService.handleSay(d, cmd.text()));
  }
}
