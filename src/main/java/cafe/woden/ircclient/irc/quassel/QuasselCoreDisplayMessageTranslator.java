package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isActionMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isNoticeMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isPlainMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.firstChannelToken;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.normalizeReason;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseKickDetails;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseModeDetails;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseNickChange;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseTopic;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.serverResponse;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.mode.ChannelModeObservationFactory;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.KickDetails;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Translates prepared native display messages without owning session or transport state. */
final class QuasselCoreDisplayMessageTranslator {
  private static final int MESSAGE_TYPE_NICK = 0x0008;
  private static final int MESSAGE_TYPE_MODE = 0x0010;
  private static final int MESSAGE_TYPE_JOIN = 0x0020;
  private static final int MESSAGE_TYPE_PART = 0x0040;
  private static final int MESSAGE_TYPE_QUIT = 0x0080;
  private static final int MESSAGE_TYPE_KICK = 0x0100;
  private static final int MESSAGE_TYPE_SERVER = 0x0400;
  private static final int MESSAGE_TYPE_INFO = 0x0800;
  private static final int MESSAGE_TYPE_ERROR = 0x1000;
  private static final int MESSAGE_TYPE_TOPIC = 0x4000;
  private static final int MESSAGE_TYPE_INVITE = 0x20000;

  private QuasselCoreDisplayMessageTranslator() {}

  /** Values prepared by the service after routing, IRCv3 interception, and backlog handling. */
  record Observation(
      Instant at,
      BufferInfoValue bufferInfo,
      String target,
      String from,
      String senderHostmask,
      String payloadText,
      String messageId,
      Map<String, String> tags,
      MessageValue message) {}

  /** Session queries and observations bound to the message's network by the caller. */
  interface SessionPort {
    boolean isSelfNick(String nick);

    String currentNick();

    String queryTarget();

    void observeJoin(Instant at, String target);

    void leave(String target);

    void observeNick(Instant at, String nick);
  }

  static void translate(Observation observation, SessionPort session, Consumer<IrcEvent> emit) {
    Instant at = observation.at();
    BufferInfoValue bufferInfo = observation.bufferInfo();
    String target = observation.target();
    String fromDisplay = observation.from();
    String senderHostmask = observation.senderHostmask();
    String payloadText = observation.payloadText();
    String messageId = observation.messageId();
    Map<String, String> ircv3Tags = observation.tags();
    MessageValue message = observation.message();
    int typeBits = message.typeBits();
    String content = Objects.toString(message.content(), "");
    if (isJoinMessage(typeBits)) {
      handleJoinMessage(session, at, target, fromDisplay, senderHostmask, emit);
      return;
    }

    if (isPartMessage(typeBits)) {
      handlePartMessage(session, at, target, fromDisplay, senderHostmask, payloadText, emit);
      return;
    }

    if (isQuitMessage(typeBits)) {
      if (!target.isEmpty()) {
        emitObservedHostmask(at, target, fromDisplay, senderHostmask, emit);
        emit.accept(
            new IrcEvent.UserQuitChannel(at, target, fromDisplay, normalizeReason(payloadText)));
      }
      return;
    }

    if (isNickMessage(typeBits)) {
      String newNick = parseNickChange(payloadText, fromDisplay);
      if (!target.isEmpty()) {
        emit.accept(new IrcEvent.UserNickChangedChannel(at, target, fromDisplay, newNick));
      }
      if (session.isSelfNick(fromDisplay)) {
        session.observeNick(at, newNick);
      }
      return;
    }

    if (isTopicMessage(typeBits)) {
      String topic = parseTopic(payloadText);
      if (!target.isEmpty()) {
        emit.accept(new IrcEvent.ChannelTopicUpdated(at, target, topic));
        return;
      }
    }

    if (isModeMessage(typeBits)) {
      if (!target.isEmpty()) {
        String details = parseModeDetails(payloadText);
        emit.accept(
            ChannelModeObservationFactory.fromQuasselDisplayMessage(
                at, target, fromDisplay, details));
        return;
      }
    }

    if (isKickMessage(typeBits)) {
      if (!target.isEmpty()) {
        KickDetails kick = parseKickDetails(payloadText);
        String kickedNick = Objects.toString(kick.nick(), "").trim();
        if (!kickedNick.isEmpty()) {
          if (session.isSelfNick(kickedNick)) {
            session.leave(target);
            emit.accept(new IrcEvent.KickedFromChannel(at, target, fromDisplay, kick.reason()));
          } else {
            emit.accept(
                new IrcEvent.UserKickedFromChannel(
                    at, target, kickedNick, fromDisplay, kick.reason()));
          }
          return;
        }
      }
    }

    if (isInviteMessage(typeBits)) {
      String channel = firstChannelToken(payloadText);
      if (channel.isEmpty()) channel = target;
      if (!channel.isEmpty()) {
        emit.accept(
            new IrcEvent.InvitedToChannel(
                at, channel, fromDisplay, session.currentNick(), "", false));
        return;
      }
    }

    if (isNoticeMessage(typeBits)) {
      if (target.isEmpty() && isQueryBuffer(bufferInfo)) {
        target = session.queryTarget();
      }
      emit.accept(new IrcEvent.Notice(at, fromDisplay, target, payloadText, messageId, ircv3Tags));
      return;
    }

    if (isActionMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        emit.accept(
            new IrcEvent.ChannelAction(at, target, fromDisplay, payloadText, messageId, ircv3Tags));
      } else {
        emit.accept(new IrcEvent.PrivateAction(at, fromDisplay, payloadText, messageId, ircv3Tags));
      }
      return;
    }

    if (isPlainMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        emit.accept(
            new IrcEvent.ChannelMessage(
                at, target, fromDisplay, payloadText, messageId, ircv3Tags));
        return;
      }
      emit.accept(new IrcEvent.PrivateMessage(at, fromDisplay, payloadText, messageId, ircv3Tags));
      return;
    }

    String statusLine =
        payloadText.isBlank() ? renderUnknownMessageType(message, target) : payloadText;
    if (isErrorMessage(typeBits)) {
      emit.accept(
          new IrcEvent.Error(
              at, statusLine.isBlank() ? "Quassel reported an error" : statusLine, null));
      return;
    }

    emit.accept(serverResponse(at, statusLine, content, messageId, ircv3Tags));
  }

  private static void handleJoinMessage(
      SessionPort session,
      Instant at,
      String channel,
      String fromDisplay,
      String senderHostmask,
      Consumer<IrcEvent> emit) {
    if (channel.isEmpty()) return;
    if (session.isSelfNick(fromDisplay)) {
      session.observeJoin(at, channel);
      return;
    }
    emitObservedHostmask(at, channel, fromDisplay, senderHostmask, emit);
    emit.accept(new IrcEvent.UserJoinedChannel(at, channel, fromDisplay));
  }

  private static void handlePartMessage(
      SessionPort session,
      Instant at,
      String channel,
      String fromDisplay,
      String senderHostmask,
      String content,
      Consumer<IrcEvent> emit) {
    if (channel.isEmpty()) return;
    String reason = normalizeReason(content);
    if (session.isSelfNick(fromDisplay)) {
      session.leave(channel);
      emit.accept(new IrcEvent.LeftChannel(at, channel, reason));
      return;
    }
    emitObservedHostmask(at, channel, fromDisplay, senderHostmask, emit);
    emit.accept(new IrcEvent.UserPartedChannel(at, channel, fromDisplay, reason));
  }

  private static boolean isNickMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_NICK) != 0;
  }

  private static boolean isModeMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_MODE) != 0;
  }

  private static boolean isJoinMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_JOIN) != 0;
  }

  private static boolean isPartMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_PART) != 0;
  }

  private static boolean isQuitMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_QUIT) != 0;
  }

  private static boolean isKickMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_KICK) != 0;
  }

  private static boolean isTopicMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_TOPIC) != 0;
  }

  private static boolean isInviteMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_INVITE) != 0;
  }

  private static boolean isServerInfoMessage(int typeBits) {
    return (typeBits & (MESSAGE_TYPE_SERVER | MESSAGE_TYPE_INFO)) != 0;
  }

  private static boolean isErrorMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_ERROR) != 0;
  }

  private static void emitObservedHostmask(
      Instant at, String channel, String nick, String hostmask, Consumer<IrcEvent> emit) {
    String normalizedNick = Objects.toString(nick, "").trim();
    String normalizedHostmask = Objects.toString(hostmask, "").trim();
    if (normalizedNick.isEmpty() || !PircbotxUtil.isUsefulHostmask(normalizedHostmask)) {
      return;
    }
    emit.accept(new IrcEvent.UserHostmaskObserved(at, channel, normalizedNick, normalizedHostmask));
  }

  private static String renderUnknownMessageType(
      QuasselCoreDatastreamCodec.MessageValue message, String target) {
    String prefix = target.isEmpty() ? "" : ("[" + target + "] ");
    if (isServerInfoMessage(message.typeBits())) {
      String content = Objects.toString(message.content(), "").trim();
      return content.isEmpty() ? (prefix + "(server)") : (prefix + content);
    }
    String content = Objects.toString(message.content(), "").trim();
    if (!content.isEmpty()) {
      return prefix + content;
    }
    return prefix + "(quassel message type " + message.typeBits() + ")";
  }

  private static boolean isChannelBuffer(BufferInfoValue bufferInfo) {
    return bufferInfo != null && (bufferInfo.typeBits() & 0x02) != 0;
  }

  private static boolean isQueryBuffer(BufferInfoValue bufferInfo) {
    return bufferInfo != null && (bufferInfo.typeBits() & 0x04) != 0;
  }
}
