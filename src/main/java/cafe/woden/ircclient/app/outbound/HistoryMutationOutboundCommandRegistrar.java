package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.outbound.chathistory.OutboundChatHistoryCommandService;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import cafe.woden.ircclient.app.outbound.messaging.OutboundSayQuoteCommandService;
import cafe.woden.ircclient.app.outbound.readmarker.OutboundReadMarkerCommandService;
import cafe.woden.ircclient.app.outbound.upload.OutboundUploadCommandService;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers history/replay and message-mutation command handlers. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class HistoryMutationOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final OutboundChatHistoryCommandService outboundChatHistoryCommandService;
  @NonNull private final OutboundReadMarkerCommandService outboundReadMarkerCommandService;
  @NonNull private final OutboundHelpCommandService outboundHelpCommandService;
  @NonNull private final OutboundUploadCommandService outboundUploadCommandService;

  @NonNull
  private final OutboundMessageMutationCommandService outboundMessageMutationCommandService;

  @NonNull private final OutboundSayQuoteCommandService outboundSayQuoteCommandService;

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
