package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.NonNull;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Installs small PircBotX hooks that require reflection.
 *
 * <p>PircBotX does not expose a public API for swapping its {@link InputParser}, but we need to
 * decorate it to surface selected IRCv3 capabilities into our own event stream (e.g. {@code
 * away-notify}, {@code account-notify}, {@code extended-join}, {@code account-tag}, {@code
 * setname}, {@code chghost}, typing/reply/react/read-marker tags, and CAP updates).
 */
@Component
@InfrastructureLayer
public class PircbotxInputParserHookInstaller {

  private static final Logger log = LoggerFactory.getLogger(PircbotxInputParserHookInstaller.class);

  @NonNull private final Ircv3StsPolicyService stsPolicies;
  @NonNull private final Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog;

  @NonNull
  private final Ircv3CapabilityNegotiationRuntimeSupport capabilityNegotiationRuntimeSupport;

  @NonNull private final Ircv3HistoryTransportRuntimeSupport historyTransportRuntimeSupport;
  @NonNull private final Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport;
  @NonNull private final Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport;
  @NonNull private final Ircv3TypingRuntimeSupport typingRuntimeSupport;
  @NonNull private final Ircv3AccountTagRuntimeSupport accountTagRuntimeSupport;
  @NonNull private final Ircv3ChannelContextRuntimeSupport channelContextRuntimeSupport;
  @NonNull private final PircbotxMultilineCapStateSupport multilineCapStateSupport;
  @NonNull private final Ircv3StandardReplyRuntimeSupport standardReplyRuntimeSupport;
  @NonNull private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;
  @NonNull private final Ircv3EchoMessageRuntimeSupport echoMessageRuntimeSupport;
  @NonNull private final Ircv3LabeledResponseRuntimeSupport labeledResponseRuntimeSupport;

  @Autowired
  public PircbotxInputParserHookInstaller(
      Ircv3StsPolicyService stsPolicies,
      Ircv3RuntimeCatalogs catalogs,
      Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport,
      Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport,
      Ircv3TypingRuntimeSupport typingRuntimeSupport,
      Ircv3AccountTagRuntimeSupport accountTagRuntimeSupport,
      Ircv3ChannelContextRuntimeSupport channelContextRuntimeSupport,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport) {
    this(
        stsPolicies,
        runtimeComposition(
            catalogs,
            messageMutationRuntimeSupport,
            readMarkerRuntimeSupport,
            typingRuntimeSupport,
            accountTagRuntimeSupport,
            channelContextRuntimeSupport,
            serverTimeRuntimeSupport));
  }

  /** Explicit non-Spring composition for focused transport tests and embedded callers. */
  public PircbotxInputParserHookInstaller(
      Ircv3StsPolicyService stsPolicies, Ircv3RuntimeCatalogs catalogs) {
    this(stsPolicies, runtimeComposition(catalogs));
  }

  private PircbotxInputParserHookInstaller(
      Ircv3StsPolicyService stsPolicies, RuntimeComposition runtime) {
    RuntimeComposition requiredRuntime = Objects.requireNonNull(runtime, "runtime");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.inboundCommandRuntimeCatalog = requiredRuntime.inboundCommands();
    this.capabilityNegotiationRuntimeSupport = requiredRuntime.capabilityNegotiation();
    this.historyTransportRuntimeSupport = requiredRuntime.historyTransport();
    this.messageMutationRuntimeSupport = requiredRuntime.messageMutation();
    this.readMarkerRuntimeSupport = requiredRuntime.readMarker();
    this.typingRuntimeSupport = requiredRuntime.typing();
    this.accountTagRuntimeSupport = requiredRuntime.accountTag();
    this.channelContextRuntimeSupport = requiredRuntime.channelContext();
    this.multilineCapStateSupport = requiredRuntime.multiline();
    this.standardReplyRuntimeSupport = requiredRuntime.standardReply();
    this.serverTimeRuntimeSupport = requiredRuntime.serverTime();
    this.echoMessageRuntimeSupport = requiredRuntime.echoMessage();
    this.labeledResponseRuntimeSupport = requiredRuntime.labeledResponse();
  }

  public void installIrcv3Hook(
      PircBotX bot, String serverId, PircbotxConnectionState conn, Consumer<ServerIrcEvent> sink) {
    if (bot == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    try {
      InputParser replacement = createParser(bot, sid, conn, sink);
      boolean swapped = swapInputParser(bot, replacement);
      if (swapped) {
        log.debug(
            "[{}] installed IRCv3 InputParser hook (away/account/extended-join/account-tag/setname/chghost/tags/cap)",
            sid);
      } else {
        log.warn(
            "[{}] could not install away-notify InputParser hook (no compatible field found)", sid);
      }
    } catch (Exception ex) {
      log.warn("[{}] failed to install away-notify InputParser hook", sid, ex);
    }
  }

  PircbotxIrcv3InputParser createParser(
      PircBotX bot, String serverId, PircbotxConnectionState conn, Consumer<ServerIrcEvent> sink) {
    return new PircbotxIrcv3InputParser(
        bot,
        serverId,
        conn,
        sink,
        stsPolicies,
        inboundCommandRuntimeCatalog,
        capabilityNegotiationRuntimeSupport,
        historyTransportRuntimeSupport,
        messageMutationRuntimeSupport,
        readMarkerRuntimeSupport,
        typingRuntimeSupport,
        accountTagRuntimeSupport,
        channelContextRuntimeSupport,
        multilineCapStateSupport,
        standardReplyRuntimeSupport,
        serverTimeRuntimeSupport,
        echoMessageRuntimeSupport,
        labeledResponseRuntimeSupport);
  }

  boolean swapInputParser(PircBotX bot, InputParser replacement) throws Exception {
    Field target = null;
    Class<?> c = bot.getClass();
    while (c != null) {
      for (Field f : c.getDeclaredFields()) {
        if (InputParser.class.isAssignableFrom(f.getType())) {
          target = f;
          break;
        }
      }
      if (target != null) break;
      c = c.getSuperclass();
    }
    if (target == null) return false;

    target.setAccessible(true);
    target.set(bot, replacement);
    return true;
  }

  private static RuntimeComposition runtimeComposition(Ircv3RuntimeCatalogs catalogs) {
    Ircv3RuntimeCatalogs requiredCatalogs = Objects.requireNonNull(catalogs, "catalogs");
    Ircv3InboundTagSignalRuntimeCatalog inboundTags = requiredCatalogs.inboundTags();
    Ircv3InboundCommandSignalRuntimeCatalog inboundCommands = requiredCatalogs.inboundCommands();
    Ircv3MessageIdRuntimeSupport messageId = new Ircv3MessageIdRuntimeSupport(inboundTags);
    Ircv3MessageTagsRuntimeSupport messageTags =
        new Ircv3MessageTagsRuntimeSupport(requiredCatalogs.messageTags(), messageId);
    return runtimeComposition(
        requiredCatalogs,
        new Ircv3MessageMutationRuntimeSupport(
            requiredCatalogs.messageMutations(), inboundTags, inboundCommands),
        new Ircv3ReadMarkerRuntimeSupport(
            requiredCatalogs.outboundCommands(), inboundTags, inboundCommands),
        new Ircv3TypingRuntimeSupport(
            requiredCatalogs.outboundCommands(), inboundTags, inboundCommands),
        new Ircv3AccountTagRuntimeSupport(inboundTags),
        new Ircv3ChannelContextRuntimeSupport(inboundTags),
        new Ircv3ServerTimeRuntimeSupport(inboundTags, messageTags));
  }

  private static RuntimeComposition runtimeComposition(
      Ircv3RuntimeCatalogs catalogs,
      Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport,
      Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport,
      Ircv3TypingRuntimeSupport typingRuntimeSupport,
      Ircv3AccountTagRuntimeSupport accountTagRuntimeSupport,
      Ircv3ChannelContextRuntimeSupport channelContextRuntimeSupport,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport) {
    Ircv3RuntimeCatalogs requiredCatalogs = Objects.requireNonNull(catalogs, "catalogs");
    Ircv3InboundTagSignalRuntimeCatalog inboundTags = requiredCatalogs.inboundTags();
    Ircv3InboundCommandSignalRuntimeCatalog inboundCommands = requiredCatalogs.inboundCommands();
    Ircv3MessageIdRuntimeSupport messageId = new Ircv3MessageIdRuntimeSupport(inboundTags);
    return new RuntimeComposition(
        inboundCommands,
        new Ircv3CapabilityNegotiationRuntimeSupport(inboundCommands),
        new Ircv3HistoryTransportRuntimeSupport(inboundCommands, inboundTags),
        Objects.requireNonNull(messageMutationRuntimeSupport, "messageMutationRuntimeSupport"),
        Objects.requireNonNull(readMarkerRuntimeSupport, "readMarkerRuntimeSupport"),
        Objects.requireNonNull(typingRuntimeSupport, "typingRuntimeSupport"),
        Objects.requireNonNull(accountTagRuntimeSupport, "accountTagRuntimeSupport"),
        Objects.requireNonNull(channelContextRuntimeSupport, "channelContextRuntimeSupport"),
        new PircbotxMultilineCapStateSupport(
            new Ircv3MultilineCapabilityRuntimeSupport(inboundCommands)),
        new Ircv3StandardReplyRuntimeSupport(inboundCommands, messageId),
        Objects.requireNonNull(serverTimeRuntimeSupport, "serverTimeRuntimeSupport"),
        new Ircv3EchoMessageRuntimeSupport(inboundTags),
        new Ircv3LabeledResponseRuntimeSupport(inboundTags));
  }

  private record RuntimeComposition(
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommands,
      Ircv3CapabilityNegotiationRuntimeSupport capabilityNegotiation,
      Ircv3HistoryTransportRuntimeSupport historyTransport,
      Ircv3MessageMutationRuntimeSupport messageMutation,
      Ircv3ReadMarkerRuntimeSupport readMarker,
      Ircv3TypingRuntimeSupport typing,
      Ircv3AccountTagRuntimeSupport accountTag,
      Ircv3ChannelContextRuntimeSupport channelContext,
      PircbotxMultilineCapStateSupport multiline,
      Ircv3StandardReplyRuntimeSupport standardReply,
      Ircv3ServerTimeRuntimeSupport serverTime,
      Ircv3EchoMessageRuntimeSupport echoMessage,
      Ircv3LabeledResponseRuntimeSupport labeledResponse) {

    private RuntimeComposition {
      Objects.requireNonNull(inboundCommands, "inboundCommands");
      Objects.requireNonNull(capabilityNegotiation, "capabilityNegotiation");
      Objects.requireNonNull(historyTransport, "historyTransport");
      Objects.requireNonNull(messageMutation, "messageMutation");
      Objects.requireNonNull(readMarker, "readMarker");
      Objects.requireNonNull(typing, "typing");
      Objects.requireNonNull(accountTag, "accountTag");
      Objects.requireNonNull(channelContext, "channelContext");
      Objects.requireNonNull(multiline, "multiline");
      Objects.requireNonNull(standardReply, "standardReply");
      Objects.requireNonNull(serverTime, "serverTime");
      Objects.requireNonNull(echoMessage, "echoMessage");
      Objects.requireNonNull(labeledResponse, "labeledResponse");
    }
  }
}
