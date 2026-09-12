package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures.Runtime;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.util.function.Consumer;
import org.pircbotx.PircBotX;

/** Explicit parser-helper composition backed by installed IRCv3 runtime providers. */
public final class PircbotxParserRuntimeTestFixtures {

  private PircbotxParserRuntimeTestFixtures() {}

  public static Runtime runtime() {
    return Ircv3RuntimeTestFixtures.runtime();
  }

  public static PircbotxAccountTagSupport accountTags(
      String serverId, Consumer<ServerIrcEvent> sink) {
    return accountTags(serverId, sink, runtime());
  }

  public static PircbotxAccountTagSupport accountTags(
      String serverId, Consumer<ServerIrcEvent> sink, Runtime runtime) {
    return new PircbotxAccountTagSupport(serverId, sink, runtime.accountTag());
  }

  public static PircbotxCapabilityNegotiationSupport capabilityNegotiation(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      PircbotxCapabilityStateSupport capabilityStateSupport) {
    return capabilityNegotiation(bot, serverId, conn, sink, capabilityStateSupport, runtime());
  }

  public static PircbotxCapabilityNegotiationSupport capabilityNegotiation(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      PircbotxCapabilityStateSupport capabilityStateSupport,
      Runtime runtime) {
    return new PircbotxCapabilityNegotiationSupport(
        bot,
        serverId,
        conn,
        sink,
        capabilityStateSupport,
        runtime.capabilityNegotiation(),
        runtime.zncPlayback());
  }

  public static PircbotxMultilineCapStateSupport multiline() {
    return multiline(runtime());
  }

  public static PircbotxMultilineCapStateSupport multiline(Runtime runtime) {
    return new PircbotxMultilineCapStateSupport(runtime.multiline());
  }

  public static PircbotxPresenceSignalSupport presence(
      String serverId, Consumer<ServerIrcEvent> sink) {
    return presence(serverId, sink, runtime());
  }

  public static PircbotxPresenceSignalSupport presence(
      String serverId, Consumer<ServerIrcEvent> sink, Runtime runtime) {
    return new PircbotxPresenceSignalSupport(serverId, sink, runtime.catalogs().inboundCommands());
  }

  public static PircbotxStandardReplySupport standardReplies(
      String serverId, Consumer<ServerIrcEvent> sink) {
    return standardReplies(serverId, sink, runtime());
  }

  public static PircbotxStandardReplySupport standardReplies(
      String serverId, Consumer<ServerIrcEvent> sink, Runtime runtime) {
    return new PircbotxStandardReplySupport(serverId, sink, runtime.standardReply());
  }

  public static PircbotxTagSignalSupport tagSignals(
      String serverId, Consumer<ServerIrcEvent> sink) {
    return tagSignals(serverId, sink, runtime());
  }

  public static PircbotxTagSignalSupport tagSignals(
      String serverId, Consumer<ServerIrcEvent> sink, Runtime runtime) {
    return new PircbotxTagSignalSupport(
        serverId,
        sink,
        runtime.channelContext(),
        runtime.messageMutation(),
        runtime.readMarker(),
        runtime.typing());
  }
}
