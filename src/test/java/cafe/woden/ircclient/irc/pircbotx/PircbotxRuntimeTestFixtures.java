package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures.Runtime;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxInviteEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateConversationSupport;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxRosterEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxUnknownCtcpEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pircbotx.PircBotX;

/** Explicit PircBotX test composition backed by installed IRCv3 runtime providers. */
public final class PircbotxRuntimeTestFixtures {

  private PircbotxRuntimeTestFixtures() {}

  public static Runtime runtime() {
    return Ircv3RuntimeTestFixtures.runtime();
  }

  public static PircbotxChatHistoryBatchCollector chatHistoryBatches(
      String serverId, Consumer<ServerIrcEvent> emit) {
    return chatHistoryBatches(serverId, emit, runtime());
  }

  public static PircbotxChatHistoryBatchCollector chatHistoryBatches(
      String serverId, Consumer<ServerIrcEvent> emit, Runtime runtime) {
    return new PircbotxChatHistoryBatchCollector(
        serverId,
        emit,
        runtime.catalogs().inboundCommands(),
        runtime.catalogs().inboundTags(),
        runtime.serverTime(),
        runtime.messageTags());
  }

  public static PircbotxMonitorEventEmitter monitorEvents(
      String serverId, Consumer<ServerIrcEvent> emit) {
    return monitorEvents(serverId, emit, runtime());
  }

  public static PircbotxMonitorEventEmitter monitorEvents(
      String serverId, Consumer<ServerIrcEvent> emit, Runtime runtime) {
    return new PircbotxMonitorEventEmitter(
        serverId, emit, runtime.catalogs().inboundCommands(), runtime.serverTime());
  }

  public static PircbotxInviteEventEmitter inviteEvents(
      String serverId, PircbotxRosterEmitter rosterEmitter, Consumer<ServerIrcEvent> emit) {
    return inviteEvents(serverId, rosterEmitter, emit, runtime());
  }

  public static PircbotxInviteEventEmitter inviteEvents(
      String serverId,
      PircbotxRosterEmitter rosterEmitter,
      Consumer<ServerIrcEvent> emit,
      Runtime runtime) {
    return new PircbotxInviteEventEmitter(
        serverId, rosterEmitter, runtime.catalogs().inboundCommands(), emit);
  }

  public static PircbotxWhoEventEmitter whoEvents(
      String serverId, PircbotxConnectionState conn, Consumer<ServerIrcEvent> emit) {
    return whoEvents(serverId, conn, emit, runtime());
  }

  public static PircbotxWhoEventEmitter whoEvents(
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> emit,
      Runtime runtime) {
    return new PircbotxWhoEventEmitter(serverId, conn, emit, runtime.catalogs().inboundCommands());
  }

  public static PircbotxUnknownCtcpEmitter unknownCtcp(
      String serverId,
      Consumer<ServerIrcEvent> emit,
      BiPredicate<PircBotX, String> nickMatchesSelf,
      BiPredicate<PircBotX, String> selfEchoDetector,
      Function<PircBotX, String> selfNickResolver) {
    return unknownCtcp(
        serverId, emit, nickMatchesSelf, selfEchoDetector, selfNickResolver, runtime());
  }

  public static PircbotxUnknownCtcpEmitter unknownCtcp(
      String serverId,
      Consumer<ServerIrcEvent> emit,
      BiPredicate<PircBotX, String> nickMatchesSelf,
      BiPredicate<PircBotX, String> selfEchoDetector,
      Function<PircBotX, String> selfNickResolver,
      Runtime runtime) {
    return new PircbotxUnknownCtcpEmitter(
        serverId, emit, nickMatchesSelf, selfEchoDetector, selfNickResolver, runtime.serverTime());
  }

  public static PircbotxPrivateConversationSupport privateConversation(
      PircbotxConnectionState conn) {
    return privateConversation(conn, runtime());
  }

  public static PircbotxPrivateConversationSupport privateConversation(
      PircbotxConnectionState conn, Runtime runtime) {
    return new PircbotxPrivateConversationSupport(conn, runtime.zncPlayback());
  }

  public static PircbotxServerResponseEmitter serverResponses(
      String serverId, Consumer<ServerIrcEvent> emit) {
    return serverResponses(serverId, emit, runtime());
  }

  public static PircbotxServerResponseEmitter serverResponses(
      String serverId, Consumer<ServerIrcEvent> emit, Runtime runtime) {
    return new PircbotxServerResponseEmitter(
        serverId, emit, runtime.serverTime(), runtime.messageTags());
  }
}
