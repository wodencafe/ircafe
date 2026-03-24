package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.PrivateMessageEvent;

/** Emits structured private-message events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxPrivateMessageEmitter {
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";

  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final Ircv3MultilineAccumulator multilineAccumulator;
  @NonNull private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  @NonNull private final PircbotxPrivateConversationSupport privateConversationSupport;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final Function<PircBotX, String> selfNickResolver;
  @NonNull private final Function<Object, String> privateTargetFromEvent;

  public PircbotxPrivateMessageEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      Consumer<ServerIrcEvent> emit,
      Function<PircBotX, String> selfNickResolver,
      Function<Object, String> privateTargetFromEvent) {
    this(
        serverId,
        conn,
        rosterEmitter,
        bouncerDiscovery,
        chatHistoryBatches,
        multilineAccumulator,
        new PircbotxPlaybackCaptureRecorder(conn),
        new PircbotxPrivateConversationSupport(conn),
        emit,
        selfNickResolver,
        privateTargetFromEvent);
  }

  public void onPrivateMessage(PrivateMessageEvent event) {
    String botNick = selfNickResolver.apply(event != null ? event.getBot() : null);
    String pmDest = privateTargetFromEvent.apply(event);

    Optional<String> batchId = Ircv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event);
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String msg = PircbotxUtil.safeStr(event::getMessage, "");
      String action = PircbotxUtil.parseCtcpAction(msg);
      String kind = action == null ? "PRIVMSG" : "ACTION";
      String hintPayload = action == null ? msg : action;
      Map<String, String> tags = PircbotxEventMetadata.ircv3TagsFromEvent(event);
      String batchMsgId = PircbotxEventMetadata.ircv3MessageId(tags);

      boolean fromSelf =
          botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
      if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
        String hinted =
            privateConversationSupport.inferPrivateDestinationFromHints(
                from, kind, hintPayload, batchMsgId);
        if (!hinted.isBlank()) {
          pmDest = hinted;
        }
      }
      if (privateConversationSupport.shouldSuppressSelfBootstrapMessage(fromSelf, pmDest, msg)) {
        return;
      }

      String convTarget =
          privateConversationSupport.deriveConversationTarget(botNick, from, pmDest);
      ChatHistoryEntry.Kind entryKind =
          action != null ? ChatHistoryEntry.Kind.ACTION : ChatHistoryEntry.Kind.PRIVMSG;
      String payload = action != null ? action : msg;
      if (chatHistoryBatches.appendIfActive(
          batchId.get(), entryKind, at, convTarget, from, payload, batchMsgId, tags)) {
        return;
      }
    }

    Instant at = PircbotxEventMetadata.inboundAt(event);
    String from = event.getUser().getNick();
    String msg = event.getMessage();
    String actionPayload = PircbotxUtil.parseCtcpAction(msg);
    String hintKind = actionPayload == null ? "PRIVMSG" : "ACTION";
    String hintPayload = actionPayload == null ? msg : actionPayload;

    boolean fromSelf = false;
    try {
      fromSelf =
          botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
    } catch (Exception ignored) {
    }

    Map<String, String> tags = new HashMap<>(PircbotxEventMetadata.ircv3TagsFromEvent(event));
    String messageId = PircbotxEventMetadata.ircv3MessageId(tags);
    if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
      String hinted =
          privateConversationSupport.inferPrivateDestinationFromHints(
              from, hintKind, hintPayload, messageId);
      if (!hinted.isBlank()) {
        pmDest = hinted;
      }
    }
    if (pmDest != null && !pmDest.isBlank()) {
      tags.put(TAG_IRCAFE_PM_TARGET, pmDest);
    }
    PircbotxEventMetadata.withObservedHostmaskTag(tags, event.getUser());
    Map<String, String> ircv3Tags = tags;
    messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);

    if (privateConversationSupport.shouldSuppressSelfBootstrapMessage(fromSelf, pmDest, msg)) {
      return;
    }

    if ("*status".equalsIgnoreCase(from)) {
      bouncerDiscovery.maybeMarkZncDetected("private message from *status", null);
    }

    if (bouncerDiscovery.maybeCaptureZncListNetworks(from, msg)) {
      return;
    }

    if ("*playback".equalsIgnoreCase(from)) {
      conn.onPlaybackControlLine(msg);
    }

    String convTarget = privateConversationSupport.deriveConversationTarget(botNick, from, pmDest);
    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("PRIVMSG", from, convTarget, at, msg, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    msg = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
    }
    actionPayload = PircbotxUtil.parseCtcpAction(msg);
    if (!"*playback".equalsIgnoreCase(from)) {
      String action = PircbotxUtil.parseCtcpAction(msg);
      if (action != null) {
        if (playbackCaptureRecorder.maybeCapture(
            convTarget, at, ChatHistoryEntry.Kind.ACTION, from, action, messageId, ircv3Tags)) {
          return;
        }
      } else {
        if (playbackCaptureRecorder.maybeCapture(
            convTarget, at, ChatHistoryEntry.Kind.PRIVMSG, from, msg, messageId, ircv3Tags)) {
          return;
        }
      }
    }

    rosterEmitter.maybeEmitHostmaskObserved("", event.getUser());

    String action = actionPayload;
    if (action != null) {
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.PrivateAction(at, from, action, messageId, ircv3Tags)));
      return;
    }

    if (fromSelf && PircbotxUtil.isCtcpWrapped(msg)) {
      return;
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.PrivateMessage(at, from, msg, messageId, ircv3Tags)));
  }
}
