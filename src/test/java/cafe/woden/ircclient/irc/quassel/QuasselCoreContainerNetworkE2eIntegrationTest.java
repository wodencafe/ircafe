package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Real-core round trips for live messaging and stored backlog through a local IRC server. */
class QuasselCoreContainerNetworkE2eIntegrationTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(100);
  private static final Duration SETUP_TIMEOUT = Duration.ofSeconds(100);
  private static final Duration NETWORK_SYNC_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration IRC_BOT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(40);
  private static final long POLL_INTERVAL_MS = 50L;

  @TempDir Path tlsDirectory;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void quasselCoreCanCreateAndConnectNetworkThenReceiveLiveChannelMessage() throws Exception {
    withConnectedNetwork(
        session -> {
          int messageCount =
              countChannelMessages(
                  session.events(),
                  session.serverId(),
                  session.channel(),
                  session.botNick(),
                  session.cfg().messageText());
          session.bot().privmsg(session.channel(), session.cfg().messageText());
          IrcEvent.ChannelMessage message =
              awaitChannelMessage(
                  session.events(),
                  session.serverId(),
                  session.channel(),
                  session.botNick(),
                  session.cfg().messageText(),
                  messageCount,
                  MESSAGE_TIMEOUT);
          assertTrue(
              Long.parseLong(message.messageId()) > 0,
              "live Quassel messages must carry core message IDs");
        });
  }

  @Test
  void tlsGatewaySupportsCoreSetupMessagingLagAndReconnectAndRejectsUntrustedCertificate()
      throws Exception {
    withConnectedNetwork(
        tlsDirectory,
        session -> {
          assertTrue(session.transportSocket().get() instanceof javax.net.ssl.SSLSocket);
          var ssl = (javax.net.ssl.SSLSocket) session.transportSocket().get();
          assertTrue(ssl.getSession().isValid());
          assertTrue(ssl.getSession().getProtocol().startsWith("TLS"));
          awaitInboundBarrier(session, "inbound through TLS");
          session
              .service()
              .sendToChannel(session.serverId(), session.channel(), "outbound through TLS")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  session.cfg().nick(),
                  "PRIVMSG",
                  session.channel(),
                  "outbound through TLS",
                  MESSAGE_TIMEOUT);
          session.service().requestLagProbe(session.serverId()).blockingAwait();
          awaitMeasuredLag(session);

          Socket original = session.transportSocket().get();
          reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
          assertTrue(original != session.transportSocket().get());
          assertTrue(session.transportSocket().get() instanceof javax.net.ssl.SSLSocket);
          awaitInboundBarrier(session, "TLS after reconnect");
          session
              .service()
              .sendToChannel(session.serverId(), session.channel(), "TLS outbound after reconnect")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  session.cfg().nick(),
                  "PRIVMSG",
                  session.channel(),
                  "TLS outbound after reconnect",
                  MESSAGE_TIMEOUT);

          var strictConfig =
              session
                  .runtimeConfig()
                  .withTlsEndpoint(
                      session.runtimeConfig().host(), session.runtimeConfig().port(), false);
          var strict = newService(strictConfig, new AtomicReference<>());
          var strictEvents = strict.events().test();
          try {
            strict.connect(session.serverId()).blockingAwait();
            awaitNextEvent(
                strictEvents, session.serverId(), IrcEvent.Disconnected.class, 0, CONNECT_TIMEOUT);
            var error =
                awaitNextEvent(
                    strictEvents, session.serverId(), IrcEvent.Error.class, 0, MESSAGE_TIMEOUT);
            Throwable cause = error.cause();
            while (cause != null && !(cause instanceof javax.net.ssl.SSLHandshakeException))
              cause = cause.getCause();
            assertNotNull(cause, "untrusted certificate must fail in the TLS handshake: " + error);
            assertEquals(
                0, countEvents(strictEvents, session.serverId(), IrcEvent.ConnectionReady.class));
            assertTrue(!strict.hasEstablishedQuasselCoreSession(session.serverId()));
          } finally {
            strict.shutdownNow();
            strictEvents.cancel();
          }
        });
  }

  private static void awaitMeasuredLag(ConnectedNetwork session) throws InterruptedException {
    long deadlineNs = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    while (session.service().lastMeasuredLagMs(session.serverId()).isEmpty()) {
      assertTrue(System.nanoTime() < deadlineNs, "TLS heartbeat reply must update measured lag");
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertTrue(session.service().lastMeasuredLagMs(session.serverId()).orElseThrow() >= 0);
  }

  @Test
  void outboundChannelPrivateNoticeAndActionMessagesReachAnotherIrcClient() throws Exception {
    withConnectedNetwork(
        session -> {
          QuasselCoreIrcClientService service = session.service();
          String sid = session.serverId();
          String from = service.currentNick(sid).orElseThrow();
          service.sendToChannel(sid, session.channel(), "outbound channel message").blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "PRIVMSG", session.channel(), "outbound channel message", MESSAGE_TIMEOUT);
          service
              .sendPrivateMessage(sid, session.botNick(), "outbound private message")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "PRIVMSG", session.botNick(), "outbound private message", MESSAGE_TIMEOUT);
          service
              .sendNoticeToChannel(sid, session.channel(), "outbound channel notice")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "NOTICE", session.channel(), "outbound channel notice", MESSAGE_TIMEOUT);
          service
              .sendNoticePrivate(sid, session.botNick(), "outbound private notice")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "NOTICE", session.botNick(), "outbound private notice", MESSAGE_TIMEOUT);
          service
              .sendRaw(sid, "PRIVMSG " + session.channel() + " :\u0001ACTION waves\u0001")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "PRIVMSG", session.channel(), "\u0001ACTION waves\u0001", MESSAGE_TIMEOUT);
        });
  }

  @Test
  void backlogSelectorsReturnStoredMessagesWithStableIdsAfterReconnect() throws Exception {
    withConnectedNetwork(
        session -> {
          List<IrcEvent.ChannelMessage> seeded = new ArrayList<>();
          for (int index = 0; index < 6; index++) {
            String text = "backlog-roundtrip-" + index;
            session.bot().privmsg(session.channel(), text);
            seeded.add(
                awaitChannelMessage(
                    session.events(),
                    session.serverId(),
                    session.channel(),
                    session.botNick(),
                    text,
                    0,
                    MESSAGE_TIMEOUT));
          }
          List<String> ids = seeded.stream().map(IrcEvent.ChannelMessage::messageId).toList();
          assertEquals(ids.size(), ids.stream().distinct().count());
          for (int index = 1; index < ids.size(); index++) {
            assertTrue(Long.parseLong(ids.get(index)) > Long.parseLong(ids.get(index - 1)));
          }

          List<ChatHistoryEntry> latest =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryLatest(session.serverId(), session.channel(), "*", 3));
          assertHistoryMatches(latest, seeded.subList(3, 6), session.channel());
          List<ChatHistoryEntry> before =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryBefore(
                          session.serverId(), session.channel(), "msgid=" + ids.get(3), 2));
          assertHistoryMatches(before, seeded.subList(1, 3), session.channel());
          List<ChatHistoryEntry> after =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryLatest(
                          session.serverId(), session.channel(), "msgid=" + ids.get(2), 3));
          assertHistoryMatches(after, seeded.subList(3, 6), session.channel());

          reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
          List<ChatHistoryEntry> replay =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryLatest(session.serverId(), session.channel(), "*", 3));
          assertHistoryMatches(replay, seeded.subList(3, 6), session.channel());
          assertEquals(
              latest, replay, "stored backlog IDs, timestamps, and text must survive reconnect");
        });
  }

  @Test
  void nativeUserJoinNickPartAndQuitEventsKeepTheirChannelAndReasons() throws Exception {
    withConnectedNetwork(
        session -> {
          List<IrcEvent> expected = new ArrayList<>();
          String guestNick = "lifeGuest";
          String renamedNick = "newGuest";
          try (SimpleIrcBot guest =
              SimpleIrcBot.connect(
                  session.ircServer().getHost(),
                  session.ircServer().getMappedPort(session.cfg().ircPort()),
                  guestNick)) {
            guest.join(session.channel(), IRC_BOT_TIMEOUT);
            expected.add(
                awaitMatchingEvent(
                    session,
                    IrcEvent.UserJoinedChannel.class,
                    event ->
                        session.channel().equals(event.channel())
                            && guestNick.equals(event.nick())));
            guest.sendLine("NICK " + renamedNick);
            expected.add(
                awaitMatchingEvent(
                    session,
                    IrcEvent.UserNickChangedChannel.class,
                    event ->
                        session.channel().equals(event.channel())
                            && guestNick.equals(event.oldNick())
                            && renamedNick.equals(event.newNick())));
            guest.sendLine("PART " + session.channel() + " :guest part reason");
            var parted =
                awaitMatchingEvent(
                    session,
                    IrcEvent.UserPartedChannel.class,
                    event ->
                        session.channel().equals(event.channel())
                            && renamedNick.equals(event.nick()));
            assertEquals("guest part reason", parted.reason());
            expected.add(parted);
            int joinedCount =
                countEvents(session.events(), session.serverId(), IrcEvent.UserJoinedChannel.class);
            guest.join(session.channel(), IRC_BOT_TIMEOUT);
            expected.add(
                awaitNextEvent(
                    session.events(),
                    session.serverId(),
                    IrcEvent.UserJoinedChannel.class,
                    joinedCount,
                    MESSAGE_TIMEOUT));
            guest.sendLine("QUIT :guest quit reason");
            var quit =
                awaitMatchingEvent(
                    session,
                    IrcEvent.UserQuitChannel.class,
                    event ->
                        session.channel().equals(event.channel())
                            && renamedNick.equals(event.nick()));
            assertEquals("guest quit reason", quit.reason());
            expected.add(quit);
          }
          awaitInboundBarrier(session, "after guest lifecycle");
          assertEventsInOrderOnce(session, expected);
          assertEquals(
              session.cfg().nick(),
              session.service().currentNick(session.serverId()).orElseThrow());
        });
  }

  @Test
  void selfNickPartAndRejoinUpdateNickAndMembershipWithoutDuplicateJoins() throws Exception {
    withConnectedNetwork(
        session -> {
          String oldNick = session.service().currentNick(session.serverId()).orElseThrow();
          String newNick = "newClient";
          session.service().changeNick(session.serverId(), newNick).blockingAwait();
          var nick =
              awaitMatchingEvent(
                  session,
                  IrcEvent.NickChanged.class,
                  event -> oldNick.equals(event.oldNick()) && newNick.equals(event.newNick()));
          assertEquals(newNick, session.service().currentNick(session.serverId()).orElseThrow());
          session
              .service()
              .partChannel(session.serverId(), session.channel(), "client part reason")
              .blockingAwait();
          var part =
              awaitMatchingEvent(
                  session,
                  IrcEvent.LeftChannel.class,
                  event -> session.channel().equals(event.channel()));
          assertEquals("client part reason", part.reason());
          int joins =
              countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class);
          session.service().joinChannel(session.serverId(), session.channel()).blockingAwait();
          var joined =
              awaitNextEvent(
                  session.events(),
                  session.serverId(),
                  IrcEvent.JoinedChannel.class,
                  joins,
                  JOIN_TIMEOUT);
          assertEquals(session.channel(), joined.channel());
          session
              .service()
              .sendToChannel(session.serverId(), session.channel(), "from renamed client")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  newNick, "PRIVMSG", session.channel(), "from renamed client", MESSAGE_TIMEOUT);
          awaitInboundBarrier(session, "after self lifecycle");
          assertEventsInOrderOnce(session, List.of(nick, part, joined));
          assertTrue(
              matchingEvents(
                      session.events(), session.serverId(), IrcEvent.UserNickChangedChannel.class)
                  .stream()
                  .noneMatch(
                      event -> newNick.equals(event.oldNick()) && newNick.equals(event.newNick())),
              "Core self-nick acknowledgements must not publish a redundant channel rename");
          assertEquals(
              joins + 1,
              countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class));
        });
  }

  @Test
  void nativeTopicModeAndKickEventsPreserveDetailsAndAllowSelfRejoin() throws Exception {
    withConnectedNetwork(
        session -> {
          Socket transport = session.transportSocket().get();
          int disconnects =
              countEvents(session.events(), session.serverId(), IrcEvent.Disconnected.class);
          List<IrcEvent> expected = new ArrayList<>();
          // The fixture bot creates the channel first and owns channel operator privileges.
          session.bot().sendLine("TOPIC " + session.channel() + " :container lifecycle topic");
          expected.add(
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelTopicUpdated.class,
                  event ->
                      session.channel().equals(event.channel())
                          && "container lifecycle topic".equals(event.topic())));
          session.bot().sendLine("MODE " + session.channel() + " +i");
          var mode =
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelModeObserved.class,
                  event ->
                      session.channel().equals(event.channel()) && "+i".equals(event.details()));
          assertEquals(session.botNick(), mode.by());
          assertEquals(IrcEvent.ChannelModeKind.DELTA, mode.kind());
          assertEquals(IrcEvent.ChannelModeProvenance.QUASSEL_DISPLAY_MESSAGE, mode.provenance());
          expected.add(mode);
          session.bot().sendLine("MODE " + session.channel() + " -i");
          expected.add(
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelModeObserved.class,
                  event ->
                      session.channel().equals(event.channel()) && "-i".equals(event.details())));
          String guestNick = "kickGuest";
          try (SimpleIrcBot guest =
              SimpleIrcBot.connect(
                  session.ircServer().getHost(),
                  session.ircServer().getMappedPort(session.cfg().ircPort()),
                  guestNick)) {
            guest.join(session.channel(), IRC_BOT_TIMEOUT);
            awaitMatchingEvent(
                session, IrcEvent.UserJoinedChannel.class, event -> guestNick.equals(event.nick()));
            session
                .bot()
                .sendLine("KICK " + session.channel() + " " + guestNick + " :guest kick reason");
            var kick =
                awaitMatchingEvent(
                    session,
                    IrcEvent.UserKickedFromChannel.class,
                    event ->
                        session.channel().equals(event.channel())
                            && guestNick.equals(event.nick()));
            assertEquals(session.botNick(), kick.by());
            assertEquals("guest kick reason", kick.reason());
            expected.add(kick);
          }
          String self = session.service().currentNick(session.serverId()).orElseThrow();
          session.bot().sendLine("KICK " + session.channel() + " " + self + " :client kick reason");
          var kicked =
              awaitMatchingEvent(
                  session,
                  IrcEvent.KickedFromChannel.class,
                  event -> session.channel().equals(event.channel()));
          assertEquals(session.botNick(), kicked.by());
          assertEquals("client kick reason", kicked.reason());
          expected.add(kicked);
          int joins =
              countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class);
          session.service().joinChannel(session.serverId(), session.channel()).blockingAwait();
          expected.add(
              awaitNextEvent(
                  session.events(),
                  session.serverId(),
                  IrcEvent.JoinedChannel.class,
                  joins,
                  JOIN_TIMEOUT));
          awaitInboundBarrier(session, "after kick recovery");
          assertEventsInOrderOnce(session, expected);
          assertTrue(
              transport == session.transportSocket().get(),
              "native mode and kick frames must keep the transport open");
          assertEquals(
              disconnects,
              countEvents(session.events(), session.serverId(), IrcEvent.Disconnected.class));
          assertEquals(
              joins + 1,
              countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class));
        });
  }

  @Test
  void offlineMessagesCatchUpThroughOverlappingPagesWithStableIds() throws Exception {
    withConnectedNetwork(
        session -> {
          String sid = session.serverId();
          List<IrcEvent.ChannelMessage> expected = seedMessages(session, "before-offline-", 2);
          QuasselCoreIrcClientService observer =
              newService(session.runtimeConfig(), new AtomicReference<>());
          TestSubscriber<ServerIrcEvent> observed = observer.events().test();
          try {
            observer.connect(sid).blockingAwait();
            awaitNextEvent(observed, sid, IrcEvent.ConnectionReady.class, 0, CONNECT_TIMEOUT);
            int readyCount = countEvents(session.events(), sid, IrcEvent.ConnectionReady.class);
            session.service().disconnect(sid, "offline catch-up test").blockingAwait();
            for (int index = 0; index < 4; index++) {
              String text = "while-offline-" + index;
              session.bot().privmsg(session.channel(), text);
              // A second client confirms that Core stored each message before reconnecting.
              expected.add(
                  awaitChannelMessage(
                      observed,
                      sid,
                      session.channel(),
                      session.botNick(),
                      text,
                      0,
                      MESSAGE_TIMEOUT));
            }
            assertEquals(
                0,
                countChannelMessages(
                    session.events(), sid, session.channel(), session.botNick(), "while-offline-"));
            session.service().connect(sid).blockingAwait();
            awaitNextEvent(
                session.events(), sid, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
            expected.addAll(seedMessages(session, "after-offline-", 1));

            List<ChatHistoryEntry> latest =
                requestHistory(
                    session,
                    session.service().requestChatHistoryLatest(sid, session.channel(), "*", 4));
            assertHistoryMatches(latest, expected.subList(3, 7), session.channel());
            List<ChatHistoryEntry> overlap =
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBefore(
                            sid, session.channel(), "msgid=" + expected.get(4).messageId(), 4));
            assertHistoryMatches(overlap, expected.subList(0, 4), session.channel());
            Map<String, ChatHistoryEntry> byId = new LinkedHashMap<>();
            overlap.forEach(entry -> byId.put(entry.messageId(), entry));
            latest.forEach(entry -> byId.put(entry.messageId(), entry));
            assertHistoryMatches(new ArrayList<>(byId.values()), expected, session.channel());
            assertEquals(
                overlap.getLast(), latest.getFirst(), "overlapping entries must be identical");
          } finally {
            observed.cancel();
            observer.shutdownNow();
          }
        });
  }

  @Test
  void boundedAndAroundHistoryExcludeBetweenAnchorsAndIncludeSurroundingContext() throws Exception {
    withConnectedNetwork(
        session -> {
          List<IrcEvent.ChannelMessage> seeded = seedMessages(session, "history-window-", 7);
          String start = "msgid=" + seeded.get(1).messageId();
          String end = "msgid=" + seeded.get(5).messageId();
          List<ChatHistoryEntry> between =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryBetween(
                          session.serverId(), session.channel(), start, end, 10));
          assertHistoryMatches(between, seeded.subList(2, 5), session.channel());
          List<ChatHistoryEntry> reverse =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryBetween(
                          session.serverId(), session.channel(), end, start, 10));
          assertEquals(between, reverse);
          List<ChatHistoryEntry> around =
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryAround(
                          session.serverId(),
                          session.channel(),
                          "msgid=" + seeded.get(3).messageId(),
                          4));
          assertTrue(around.size() <= 4);
          assertTrue(
              around.stream()
                  .anyMatch(entry -> entry.messageId().equals(seeded.get(2).messageId())));
          assertTrue(
              around.stream()
                  .anyMatch(entry -> entry.messageId().equals(seeded.get(3).messageId())));
          assertTrue(
              around.stream()
                  .anyMatch(entry -> entry.messageId().equals(seeded.get(4).messageId())));
          List<IrcEvent.ChannelMessage> expectedAround =
              seeded.stream()
                  .filter(
                      message ->
                          around.stream()
                              .anyMatch(entry -> entry.messageId().equals(message.messageId())))
                  .toList();
          assertHistoryMatches(around, expectedAround, session.channel());
        });
  }

  @Test
  void timestampHistoryUsesObservedMiddleBoundaryBeforeAndAfterReconnect() throws Exception {
    withConnectedNetwork(
        session -> {
          List<IrcEvent.ChannelMessage> seeded = seedMessages(session, "timestamp-before-", 2);
          awaitNextTimestampSecond(seeded.getLast().at());
          seeded.addAll(seedMessages(session, "timestamp-boundary-", 2));
          Instant boundary = seeded.get(2).at();
          awaitNextTimestampSecond(seeded.getLast().at());
          seeded.addAll(seedMessages(session, "timestamp-after-", 2));
          List<IrcEvent.ChannelMessage> expectedBefore =
              seeded.stream().filter(message -> message.at().isBefore(boundary)).toList();
          List<IrcEvent.ChannelMessage> expectedAfter =
              seeded.stream().filter(message -> message.at().isAfter(boundary)).toList();
          for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
              reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
              // Repopulate the bounded timestamp index from stored history on the new session.
              assertHistoryMatches(
                  requestHistory(
                      session,
                      session
                          .service()
                          .requestChatHistoryLatest(session.serverId(), session.channel(), "*", 6)),
                  seeded,
                  session.channel());
            }
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBefore(
                            session.serverId(), session.channel(), "timestamp=" + boundary, 2)),
                expectedBefore,
                session.channel());
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryLatest(
                            session.serverId(), session.channel(), "timestamp=" + boundary, 6)),
                expectedAfter,
                session.channel());
            String firstId = seeded.getFirst().messageId();
            List<IrcEvent.ChannelMessage> expectedMixed =
                expectedBefore.stream()
                    .filter(
                        message -> Long.parseLong(message.messageId()) > Long.parseLong(firstId))
                    .toList();
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBetween(
                            session.serverId(),
                            session.channel(),
                            "msgid=" + firstId,
                            "timestamp=" + boundary,
                            6)),
                expectedMixed,
                session.channel());
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBetween(
                            session.serverId(),
                            session.channel(),
                            "timestamp=" + boundary,
                            "msgid=" + firstId,
                            6)),
                expectedMixed,
                session.channel());
            Instant lower = seeded.getFirst().at();
            Instant upper = seeded.getLast().at();
            List<IrcEvent.ChannelMessage> expectedBetween =
                seeded.stream()
                    .filter(message -> message.at().isAfter(lower) && message.at().isBefore(upper))
                    .toList();
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBetween(
                            session.serverId(),
                            session.channel(),
                            "timestamp=" + lower,
                            "timestamp=" + upper,
                            6)),
                expectedBetween,
                session.channel());
            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBetween(
                            session.serverId(),
                            session.channel(),
                            "timestamp=" + upper,
                            "timestamp=" + lower,
                            6)),
                expectedBetween,
                session.channel());
          }
        });
  }

  @Test
  void emptyHistoryReplyCompletesAndNextRequestStillReturnsMessages() throws Exception {
    withConnectedNetwork(
        session -> {
          List<IrcEvent.ChannelMessage> seeded = seedMessages(session, "empty-history-", 1);
          assertTrue(
              requestHistory(
                      session,
                      session
                          .service()
                          .requestChatHistoryLatest(
                              session.serverId(),
                              session.channel(),
                              "msgid=" + seeded.getFirst().messageId(),
                              10))
                  .isEmpty());
          // This response contains only channel events (JOIN/MODE/TOPIC), which are filtered
          // from chat history but must still complete the request.
          assertTrue(
              requestHistory(
                      session,
                      session
                          .service()
                          .requestChatHistoryBefore(
                              session.serverId(),
                              session.channel(),
                              "msgid=" + seeded.getFirst().messageId(),
                              50))
                  .isEmpty());
          assertHistoryMatches(
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryLatest(session.serverId(), session.channel(), "*", 1)),
              seeded,
              session.channel());
        });
  }

  @Test
  void inboundPrivateNoticeAndActionMessagesKeepTheirKindsAndIdsInHistory() throws Exception {
    withConnectedNetwork(
        session -> {
          String sid = session.serverId();
          String ourNick = session.service().currentNick(sid).orElseThrow();
          session.bot().privmsg(ourNick, "incoming private");
          IrcEvent.PrivateMessage privateMessage =
              awaitMatchingEvent(
                  session,
                  IrcEvent.PrivateMessage.class,
                  event -> "incoming private".equals(event.text()));
          session.bot().sendLine("NOTICE " + ourNick + " :incoming private notice");
          IrcEvent.Notice privateNotice =
              awaitMatchingEvent(
                  session,
                  IrcEvent.Notice.class,
                  event -> "incoming private notice".equals(event.text()));
          session.bot().privmsg(ourNick, "\u0001ACTION private wave\u0001");
          IrcEvent.PrivateAction privateAction =
              awaitMatchingEvent(
                  session,
                  IrcEvent.PrivateAction.class,
                  event -> "private wave".equals(event.action()));
          session.bot().sendLine("NOTICE " + session.channel() + " :incoming channel notice");
          IrcEvent.Notice channelNotice =
              awaitMatchingEvent(
                  session,
                  IrcEvent.Notice.class,
                  event -> "incoming channel notice".equals(event.text()));
          session.bot().privmsg(session.channel(), "\u0001ACTION channel wave\u0001");
          IrcEvent.ChannelAction channelAction =
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelAction.class,
                  event -> "channel wave".equals(event.action()));
          assertEquals(session.botNick(), privateMessage.from());
          assertEquals(session.botNick(), privateNotice.from());
          assertEquals(session.botNick(), privateAction.from());
          assertEquals(session.botNick(), channelNotice.from());
          assertEquals(session.botNick(), channelAction.from());
          assertEquals(session.botNick(), privateNotice.target());
          assertEquals(session.channel(), channelNotice.target());
          assertEquals(session.channel(), channelAction.channel());
          List<ChatHistoryEntry> query =
              requestHistory(
                  session,
                  session.botNick(),
                  session.service().requestChatHistoryLatest(sid, session.botNick(), "*", 3));
          assertEquals(
              List.of("incoming private", "incoming private notice", "private wave"),
              query.stream().map(ChatHistoryEntry::text).toList());
          assertEquals(
              List.of(
                  ChatHistoryEntry.Kind.PRIVMSG,
                  ChatHistoryEntry.Kind.NOTICE,
                  ChatHistoryEntry.Kind.ACTION),
              query.stream().map(ChatHistoryEntry::kind).toList());
          assertEquals(
              List.of(
                  privateMessage.messageId(), privateNotice.messageId(), privateAction.messageId()),
              query.stream().map(ChatHistoryEntry::messageId).toList());
          assertEquals(
              List.of(privateMessage.at(), privateNotice.at(), privateAction.at()),
              query.stream().map(ChatHistoryEntry::at).toList());
          assertTrue(
              query.stream()
                  .allMatch(
                      entry ->
                          session.botNick().equals(entry.target())
                              && session.botNick().equals(entry.from())
                              && Long.parseLong(entry.messageId()) > 0));
          List<ChatHistoryEntry> channel =
              requestHistory(
                  session,
                  session.service().requestChatHistoryLatest(sid, session.channel(), "*", 2));
          assertEquals(
              List.of("incoming channel notice", "channel wave"),
              channel.stream().map(ChatHistoryEntry::text).toList());
          assertEquals(
              List.of(ChatHistoryEntry.Kind.NOTICE, ChatHistoryEntry.Kind.ACTION),
              channel.stream().map(ChatHistoryEntry::kind).toList());
          assertEquals(
              List.of(channelNotice.messageId(), channelAction.messageId()),
              channel.stream().map(ChatHistoryEntry::messageId).toList());
        });
  }

  @Test
  void channelSelfEchoIsDeliveredOnceAndStoredWithTheSameNativeId() throws Exception {
    withConnectedNetwork(
        session -> {
          String sid = session.serverId();
          String ourNick = session.service().currentNick(sid).orElseThrow();
          session.service().sendToChannel(sid, session.channel(), "self echo").blockingAwait();
          session
              .bot()
              .awaitMessage(ourNick, "PRIVMSG", session.channel(), "self echo", MESSAGE_TIMEOUT);
          IrcEvent.ChannelMessage echo =
              awaitChannelMessage(
                  session.events(),
                  sid,
                  session.channel(),
                  ourNick,
                  "self echo",
                  0,
                  MESSAGE_TIMEOUT);
          List<ChatHistoryEntry> stored =
              requestHistory(
                  session,
                  session.service().requestChatHistoryLatest(sid, session.channel(), "*", 1));
          assertHistoryMatches(stored, List.of(echo), session.channel());
          assertTrue(Long.parseLong(echo.messageId()) > 0);
          reconnectAndAwaitReady(session.service(), session.events(), sid);
          assertEquals(
              stored,
              requestHistory(
                  session,
                  session.service().requestChatHistoryLatest(sid, session.channel(), "*", 1)));
          // A live sentinel ensures both replay responses were consumed before counting echoes.
          seedMessages(session, "echo-sentinel-", 1);
          assertEquals(
              1,
              countChannelMessages(session.events(), sid, session.channel(), ourNick, "self echo"));
        });
  }

  @Test
  void duplicateChannelAndNickNamesStayIsolatedAcrossNetworks() throws Exception {
    withConnectedNetwork(
        session -> {
          withSecondNetwork(
              session,
              second -> {
                String firstTarget =
                    session.cfg().channel() + "{net:" + session.networkName() + "}";
                String secondTarget = session.cfg().channel() + "{net:" + second.name() + "}";
                awaitJoinedChannel(session, secondTarget);
                session.bot().privmsg(session.cfg().channel(), "first network history");
                second.bot().privmsg(session.cfg().channel(), "second network history");
                var firstMessage =
                    awaitChannelMessage(
                        session.events(),
                        session.serverId(),
                        firstTarget,
                        session.cfg().botNick(),
                        "first network history",
                        0,
                        MESSAGE_TIMEOUT);
                var secondMessage =
                    awaitChannelMessage(
                        session.events(),
                        session.serverId(),
                        secondTarget,
                        session.cfg().botNick(),
                        "second network history",
                        0,
                        MESSAGE_TIMEOUT);

                assertHistoryMatches(
                    requestHistory(
                        session,
                        firstTarget,
                        session
                            .service()
                            .requestChatHistoryLatest(session.serverId(), firstTarget, "*", 1)),
                    List.of(firstMessage),
                    firstTarget);
                assertHistoryMatches(
                    requestHistory(
                        session,
                        secondTarget,
                        session
                            .service()
                            .requestChatHistoryLatest(session.serverId(), secondTarget, "*", 1)),
                    List.of(secondMessage),
                    secondTarget);

                String from = session.service().currentNick(session.serverId()).orElseThrow();
                session
                    .service()
                    .sendToChannel(session.serverId(), firstTarget, "first channel only")
                    .blockingAwait();
                session
                    .service()
                    .sendToChannel(session.serverId(), secondTarget, "second channel only")
                    .blockingAwait();
                session
                    .service()
                    .sendPrivateMessage(
                        session.serverId(),
                        session.cfg().botNick() + "{net:" + session.networkName() + "}",
                        "first private only")
                    .blockingAwait();
                session
                    .service()
                    .sendPrivateMessage(
                        session.serverId(),
                        session.cfg().botNick() + "{net:" + second.name() + "}",
                        "second private only")
                    .blockingAwait();
                session
                    .bot()
                    .awaitMessages(
                        from,
                        Map.of(
                            session.cfg().channel(),
                            "first channel only",
                            session.cfg().botNick(),
                            "first private only"),
                        List.of("second channel only", "second private only"),
                        MESSAGE_TIMEOUT);
                second
                    .bot()
                    .awaitMessages(
                        from,
                        Map.of(
                            session.cfg().channel(),
                            "second channel only",
                            session.cfg().botNick(),
                            "second private only"),
                        List.of("first channel only", "first private only"),
                        MESSAGE_TIMEOUT);
              });
        });
  }

  @Test
  void unexpectedTransportLossAutomaticallyReconnectsAndRestoresMessagingAndHistory()
      throws Exception {
    withConnectedNetwork(
        session -> {
          session.bot().privmsg(session.channel(), "before transport loss");
          var original =
              awaitChannelMessage(
                  session.events(),
                  session.serverId(),
                  session.channel(),
                  session.botNick(),
                  "before transport loss",
                  0,
                  MESSAGE_TIMEOUT);
          int readyCount =
              countEvents(session.events(), session.serverId(), IrcEvent.ConnectionReady.class);
          int reconnectCount =
              countEvents(session.events(), session.serverId(), IrcEvent.Reconnecting.class);
          int disconnectedCount =
              countEvents(session.events(), session.serverId(), IrcEvent.Disconnected.class);
          Socket originalSocket = session.transportSocket().get();
          assertNotNull(originalSocket);
          originalSocket.close(); // Drop only the transport; don't request a service disconnect.
          awaitNextEvent(
              session.events(),
              session.serverId(),
              IrcEvent.Disconnected.class,
              disconnectedCount,
              CONNECT_TIMEOUT);
          awaitNextEvent(
              session.events(),
              session.serverId(),
              IrcEvent.Reconnecting.class,
              reconnectCount,
              CONNECT_TIMEOUT);
          awaitNextEvent(
              session.events(),
              session.serverId(),
              IrcEvent.ConnectionReady.class,
              readyCount,
              CONNECT_TIMEOUT);
          assertTrue(
              session.transportSocket().get() != originalSocket,
              "reconnect must open a new transport");
          assertTrue(session.service().hasEstablishedQuasselCoreSession(session.serverId()));
          assertHistoryMatches(
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryLatest(session.serverId(), session.channel(), "*", 1)),
              List.of(original),
              session.channel());
          session.bot().privmsg(session.channel(), "after automatic reconnect");
          awaitChannelMessage(
              session.events(),
              session.serverId(),
              session.channel(),
              session.botNick(),
              "after automatic reconnect",
              0,
              MESSAGE_TIMEOUT);
          String from = session.service().currentNick(session.serverId()).orElseThrow();
          session
              .service()
              .sendToChannel(session.serverId(), session.channel(), "recovered outbound")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  from, "PRIVMSG", session.channel(), "recovered outbound", MESSAGE_TIMEOUT);
        });
  }

  @Test
  void duplicateChannelsKeepHistoryAndReadMarkersIsolatedThroughNetworkRenameAndRemoval()
      throws Exception {
    withConnectedNetwork(
        session ->
            withSecondNetwork(
                session,
                second -> {
                  var first =
                      session.service().quasselCoreNetworks(session.serverId()).stream()
                          .filter(network -> session.networkName().equals(network.networkName()))
                          .findFirst()
                          .orElseThrow();
                  String firstTarget = session.channel() + "{net:" + session.networkName() + "}";
                  String secondTarget = session.channel() + "{net:" + second.name() + "}";
                  session.bot().privmsg(session.channel(), "first marker anchor");
                  var firstAnchor =
                      awaitChannelMessage(
                          session.events(),
                          session.serverId(),
                          firstTarget,
                          session.botNick(),
                          "first marker anchor",
                          0,
                          MESSAGE_TIMEOUT);
                  awaitNextTimestampSecond(firstAnchor.at());
                  second.bot().privmsg(session.channel(), "second marker anchor");
                  var secondAnchor =
                      awaitChannelMessage(
                          session.events(),
                          session.serverId(),
                          secondTarget,
                          session.botNick(),
                          "second marker anchor",
                          0,
                          MESSAGE_TIMEOUT);
                  int markerCount =
                      countEvents(
                          session.events(), session.serverId(), IrcEvent.ReadMarkerObserved.class);
                  session
                      .service()
                      .sendReadMarker(session.serverId(), firstTarget, firstAnchor.at())
                      .blockingAwait();
                  awaitMarkerTimestamp(
                      session.events(),
                      session.serverId(),
                      firstTarget,
                      firstAnchor.at(),
                      markerCount);
                  int secondMarkerCount =
                      countEvents(
                          session.events(), session.serverId(), IrcEvent.ReadMarkerObserved.class);
                  session
                      .service()
                      .sendReadMarker(session.serverId(), secondTarget, secondAnchor.at())
                      .blockingAwait();
                  awaitMarkerTimestamp(
                      session.events(),
                      session.serverId(),
                      secondTarget,
                      secondAnchor.at(),
                      secondMarkerCount);
                  List<IrcEvent.ReadMarkerObserved> updates =
                      matchingEvents(
                          session.events(), session.serverId(), IrcEvent.ReadMarkerObserved.class);
                  assertTrue(
                      updates.stream()
                          .skip(markerCount)
                          .noneMatch(
                              marker -> {
                                Instant timestamp =
                                    Instant.parse(marker.marker().substring("timestamp=".length()));
                                return (firstTarget.equals(marker.target())
                                        && timestamp.equals(secondAnchor.at()))
                                    || (secondTarget.equals(marker.target())
                                        && timestamp.equals(firstAnchor.at()));
                              }));

                  String renamed = "renamed-second";
                  session
                      .service()
                      .quasselCoreUpdateNetwork(
                          session.serverId(),
                          Integer.toString(second.networkId()),
                          new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
                              renamed,
                              session.cfg().ircAlias() + "-second",
                              session.cfg().ircPort(),
                              false,
                              "",
                              true,
                              null,
                              true))
                      .blockingAwait();
                  awaitNetworkState(
                      session,
                      second.networkId(),
                      state -> renamed.equals(state.networkName()),
                      "renamed second network");
                  String renamedTarget = session.channel() + "{net:" + renamed + "}";
                  second.bot().privmsg(session.channel(), "after second network rename");
                  var renamedMessage =
                      awaitChannelMessage(
                          session.events(),
                          session.serverId(),
                          renamedTarget,
                          session.botNick(),
                          "after second network rename",
                          0,
                          MESSAGE_TIMEOUT);
                  assertHistoryMatches(
                      requestHistory(
                          session,
                          renamedTarget,
                          session
                              .service()
                              .requestChatHistoryLatest(session.serverId(), renamedTarget, "*", 1)),
                      List.of(renamedMessage),
                      renamedTarget);
                  session
                      .service()
                      .sendToChannel(session.serverId(), renamedTarget, "renamed routing")
                      .blockingAwait();
                  second
                      .bot()
                      .awaitMessage(
                          session.cfg().nick(),
                          "PRIVMSG",
                          session.channel(),
                          "renamed routing",
                          MESSAGE_TIMEOUT);
                  assertHistoryMatches(
                      requestHistory(
                          session,
                          firstTarget,
                          session
                              .service()
                              .requestChatHistoryLatest(session.serverId(), firstTarget, "*", 1)),
                      List.of(firstAnchor),
                      firstTarget);

                  session
                      .service()
                      .quasselCoreDisconnectNetwork(session.serverId(), renamed)
                      .blockingAwait();
                  awaitNetworkState(
                      session,
                      second.networkId(),
                      state -> !state.connected(),
                      "second disconnected");
                  session
                      .service()
                      .quasselCoreRemoveNetwork(session.serverId(), renamed)
                      .blockingAwait();
                  reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
                  awaitNetworkState(
                      session,
                      first.networkId(),
                      state -> state.connected(),
                      "surviving first network");
                  assertEquals(
                      List.of(first.networkId()),
                      session.service().quasselCoreNetworks(session.serverId()).stream()
                          .map(QuasselCoreControlPort.QuasselCoreNetworkSummary::networkId)
                          .toList());
                  // A single surviving network uses an unqualified target again, with the original
                  // native IDs.
                  assertHistoryMatches(
                      requestHistory(
                          session,
                          session
                              .service()
                              .requestChatHistoryLatest(
                                  session.serverId(), session.channel(), "*", 1)),
                      List.of(firstAnchor),
                      session.channel());
                  session
                      .service()
                      .sendToChannel(
                          session.serverId(), session.channel(), "surviving network outbound")
                      .blockingAwait();
                  session
                      .bot()
                      .awaitMessage(
                          session.cfg().nick(),
                          "PRIVMSG",
                          session.channel(),
                          "surviving network outbound",
                          MESSAGE_TIMEOUT);
                  awaitInboundBarrier(session, "surviving network inbound");
                  assertEquals(1, session.service().quasselCoreNetworks(session.serverId()).size());
                }));
  }

  @Test
  void readMarkersSynchronizeBetweenClientsAndSurviveReconnect() throws Exception {
    withConnectedNetwork(
        session -> {
          QuasselCoreIrcClientService peer =
              newService(session.runtimeConfig(), new AtomicReference<>());
          TestSubscriber<ServerIrcEvent> peerEvents = peer.events().test();
          try {
            peer.connect(session.serverId()).blockingAwait();
            awaitNextEvent(
                peerEvents, session.serverId(), IrcEvent.ConnectionReady.class, 0, CONNECT_TIMEOUT);
            session.bot().privmsg(session.channel(), "read marker anchor");
            var anchor =
                awaitChannelMessage(
                    session.events(),
                    session.serverId(),
                    session.channel(),
                    session.botNick(),
                    "read marker anchor",
                    0,
                    MESSAGE_TIMEOUT);
            awaitChannelMessage(
                peerEvents,
                session.serverId(),
                session.channel(),
                session.botNick(),
                "read marker anchor",
                0,
                MESSAGE_TIMEOUT);
            assertTrue(
                session.service().isReadMarkerAvailable(session.serverId()),
                "native Quassel markers must be available independently of IRC server capabilities");
            int markerCount =
                countEvents(peerEvents, session.serverId(), IrcEvent.ReadMarkerObserved.class);
            session
                .service()
                .sendReadMarker(session.serverId(), session.channel(), anchor.at())
                .blockingAwait();
            var marker =
                awaitNextEvent(
                    peerEvents,
                    session.serverId(),
                    IrcEvent.ReadMarkerObserved.class,
                    markerCount,
                    MESSAGE_TIMEOUT);
            assertEquals(session.channel(), marker.target());
            assertEquals(
                anchor.at(),
                java.time.Instant.parse(marker.marker().substring("timestamp=".length())));

            int beforeReconnect =
                countEvents(peerEvents, session.serverId(), IrcEvent.ReadMarkerObserved.class);
            reconnectAndAwaitReady(peer, peerEvents, session.serverId());
            int historyCount =
                countEvents(
                    peerEvents, session.serverId(), IrcEvent.ChatHistoryBatchReceived.class);
            peer.requestChatHistoryLatest(session.serverId(), session.channel(), "*", 1)
                .blockingAwait();
            var replay =
                awaitNextEvent(
                    peerEvents,
                    session.serverId(),
                    IrcEvent.ChatHistoryBatchReceived.class,
                    historyCount,
                    MESSAGE_TIMEOUT);
            assertHistoryMatches(replay.entries(), List.of(anchor), session.channel());
            awaitMarkerTimestamp(
                peerEvents, session.serverId(), session.channel(), anchor.at(), beforeReconnect);
          } finally {
            peerEvents.cancel();
            peer.shutdownNow();
          }
        });
  }

  @Test
  void coreProcessRestartRestoresNetworkIdentityHistoryAndReadMarker() throws Exception {
    withConnectedNetwork(
        session -> {
          var network = session.service().quasselCoreNetworks(session.serverId()).getFirst();
          awaitInboundBarrier(session, "persisted before core restart");
          var anchor =
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelMessage.class,
                  event -> "persisted before core restart".equals(event.text()));
          session
              .service()
              .sendReadMarker(session.serverId(), session.channel(), anchor.at())
              .blockingAwait();
          awaitMarkerTimestamp(
              session.events(), session.serverId(), session.channel(), anchor.at(), 0);
          int markers =
              countEvents(session.events(), session.serverId(), IrcEvent.ReadMarkerObserved.class);
          int ready =
              countEvents(session.events(), session.serverId(), IrcEvent.ConnectionReady.class);
          int reconnects =
              countEvents(session.events(), session.serverId(), IrcEvent.Reconnecting.class);
          Socket original = session.transportSocket().get();
          // Restart the actual process in the same container. Its /config SQLite storage is
          // retained.
          controlContainerService(session.core(), "svc-quassel", "-r");
          session.bot().awaitMembershipChange(session.cfg().nick(), "QUIT", "", IRC_BOT_TIMEOUT);
          awaitNextEvent(
              session.events(),
              session.serverId(),
              IrcEvent.Reconnecting.class,
              reconnects,
              CONNECT_TIMEOUT);
          awaitNextEvent(
              session.events(),
              session.serverId(),
              IrcEvent.ConnectionReady.class,
              ready,
              CONNECT_TIMEOUT);
          assertTrue(original != session.transportSocket().get());
          var restored =
              awaitNetworkState(
                  session,
                  network.networkId(),
                  state -> state.connected() && network.networkName().equals(state.networkName()),
                  "restored after Core restart");
          session
              .bot()
              .awaitMembershipChange(session.cfg().nick(), "JOIN", session.channel(), JOIN_TIMEOUT);
          assertEquals(network.identityId(), restored.identityId());
          assertEquals(network.serverHost(), restored.serverHost());
          assertEquals(network.serverPort(), restored.serverPort());
          assertTrue(!session.service().isQuasselCoreSetupPending(session.serverId()));
          assertHistoryMatches(
              requestHistory(
                  session,
                  session
                      .service()
                      .requestChatHistoryBefore(
                          session.serverId(),
                          session.channel(),
                          "msgid=" + (Long.parseLong(anchor.messageId()) + 1L),
                          1)),
              List.of(anchor),
              session.channel());
          awaitMarkerTimestamp(
              session.events(), session.serverId(), session.channel(), anchor.at(), markers);
          session
              .service()
              .sendToChannel(session.serverId(), session.channel(), "outbound after Core restart")
              .blockingAwait();
          session
              .bot()
              .awaitMessage(
                  session.service().currentNick(session.serverId()).orElseThrow(),
                  "PRIVMSG",
                  session.channel(),
                  "outbound after Core restart",
                  MESSAGE_TIMEOUT);
          awaitInboundBarrier(session, "inbound after Core restart");
          assertEquals(
              ready + 1,
              countEvents(session.events(), session.serverId(), IrcEvent.ConnectionReady.class));
        });
  }

  @Test
  void upstreamIrcRestartKeepsCoreSessionAndRestoresChannelMessaging() throws Exception {
    withConnectedNetwork(
        session -> {
          var network = session.service().quasselCoreNetworks(session.serverId()).getFirst();
          awaitInboundBarrier(session, "history before IRC outage");
          var anchor =
              awaitMatchingEvent(
                  session,
                  IrcEvent.ChannelMessage.class,
                  event -> "history before IRC outage".equals(event.text()));
          Socket coreSocket = session.transportSocket().get();
          int ready =
              countEvents(session.events(), session.serverId(), IrcEvent.ConnectionReady.class);
          int disconnected =
              countEvents(session.events(), session.serverId(), IrcEvent.Disconnected.class);
          int reconnects =
              countEvents(session.events(), session.serverId(), IrcEvent.Reconnecting.class);
          int joins =
              countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class);
          controlContainerService(session.ircServer(), "svc-ngircd", "-d");
          awaitNetworkState(
              session, network.networkId(), state -> !state.connected(), "upstream unavailable");
          assertTrue(session.service().hasEstablishedQuasselCoreSession(session.serverId()));
          assertEquals("", session.service().backendAvailabilityReason(session.serverId()));
          session
              .service()
              .quasselCoreDisconnectNetwork(
                  session.serverId(), Integer.toString(network.networkId()))
              .blockingAwait();
          controlContainerService(session.ircServer(), "svc-ngircd", "-u");
          Wait.forListeningPort()
              .withStartupTimeout(CONNECT_TIMEOUT)
              .waitUntilReady(session.ircServer());
          try (SimpleIrcBot recoveredBot =
              SimpleIrcBot.connect(
                  session.ircServer().getHost(),
                  session.ircServer().getMappedPort(session.cfg().ircPort()),
                  session.botNick())) {
            recoveredBot.join(session.channel(), IRC_BOT_TIMEOUT);
            session
                .service()
                .quasselCoreConnectNetwork(
                    session.serverId(), Integer.toString(network.networkId()))
                .blockingAwait();
            awaitNetworkState(
                session, network.networkId(), state -> state.connected(), "upstream reconnected");
            awaitNextEvent(
                session.events(),
                session.serverId(),
                IrcEvent.JoinedChannel.class,
                joins,
                JOIN_TIMEOUT);
            recoveredBot.awaitMembershipChange(
                session.cfg().nick(), "JOIN", session.channel(), JOIN_TIMEOUT);

            assertHistoryMatches(
                requestHistory(
                    session,
                    session
                        .service()
                        .requestChatHistoryBefore(
                            session.serverId(),
                            session.channel(),
                            "msgid=" + (Long.parseLong(anchor.messageId()) + 1L),
                            1)),
                List.of(anchor),
                session.channel());
            recoveredBot.privmsg(session.channel(), "inbound after IRC outage");
            awaitChannelMessage(
                session.events(),
                session.serverId(),
                session.channel(),
                session.botNick(),
                "inbound after IRC outage",
                0,
                MESSAGE_TIMEOUT);
            session
                .service()
                .sendToChannel(session.serverId(), session.channel(), "outbound after IRC outage")
                .blockingAwait();
            recoveredBot.awaitMessage(
                session.service().currentNick(session.serverId()).orElseThrow(),
                "PRIVMSG",
                session.channel(),
                "outbound after IRC outage",
                MESSAGE_TIMEOUT);
            assertTrue(
                coreSocket == session.transportSocket().get(),
                "IRC outage must not replace the Core transport");
            assertEquals(
                disconnected,
                countEvents(session.events(), session.serverId(), IrcEvent.Disconnected.class));
            assertEquals(
                reconnects,
                countEvents(session.events(), session.serverId(), IrcEvent.Reconnecting.class));
            assertEquals(
                ready,
                countEvents(session.events(), session.serverId(), IrcEvent.ConnectionReady.class));
            assertEquals(
                joins + 1,
                countEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class));
          }
        });
  }

  @Test
  void networkEditsDisconnectReconnectAndRemovalAreConfirmedByCore() throws Exception {
    withConnectedNetwork(
        session -> {
          var initial =
              session.service().quasselCoreNetworks(session.serverId()).stream()
                  .filter(network -> session.networkName().equals(network.networkName()))
                  .findFirst()
                  .orElseThrow();
          session
              .service()
              .quasselCoreDisconnectNetwork(
                  session.serverId(), Integer.toString(initial.networkId()))
              .blockingAwait();
          awaitNetworkState(
              session, initial.networkId(), network -> !network.connected(), "disconnected");
          session.bot().awaitMembershipChange(session.cfg().nick(), "QUIT", "", IRC_BOT_TIMEOUT);
          String renamed = "renamed-network";
          session
              .service()
              .quasselCoreUpdateNetwork(
                  session.serverId(),
                  Integer.toString(initial.networkId()),
                  new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
                      renamed,
                      session.cfg().ircAlias(),
                      session.cfg().ircPort(),
                      false,
                      "",
                      true,
                      initial.identityId(),
                      true))
              .blockingAwait();
          awaitNetworkState(
              session,
              initial.networkId(),
              network -> renamed.equals(network.networkName()),
              "renamed");
          reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
          var persisted =
              awaitNetworkState(
                  session,
                  initial.networkId(),
                  network -> renamed.equals(network.networkName()),
                  "persisted renamed network");
          assertEquals(initial.identityId(), persisted.identityId());
          assertEquals(session.cfg().ircAlias(), persisted.serverHost());
          assertEquals(session.cfg().ircPort(), persisted.serverPort());
          assertTrue(!persisted.connected());
          session.service().quasselCoreConnectNetwork(session.serverId(), renamed).blockingAwait();
          awaitNetworkConnected(
              session.service(), session.serverId(), initial.networkId(), NETWORK_SYNC_TIMEOUT);
          session
              .bot()
              .awaitMembershipChange(session.cfg().nick(), "JOIN", session.channel(), JOIN_TIMEOUT);
          session.bot().privmsg(session.channel(), "message after network reconnect");
          awaitChannelMessage(
              session.events(),
              session.serverId(),
              session.channel(),
              session.botNick(),
              "message after network reconnect",
              0,
              MESSAGE_TIMEOUT);
          session
              .service()
              .quasselCoreDisconnectNetwork(session.serverId(), renamed)
              .blockingAwait();
          awaitNetworkState(
              session,
              initial.networkId(),
              network -> !network.connected(),
              "disconnected before removal");
          session.service().quasselCoreRemoveNetwork(session.serverId(), renamed).blockingAwait();
          reconnectAndAwaitReady(session.service(), session.events(), session.serverId());
          assertTrue(
              session.service().quasselCoreNetworks(session.serverId()).stream()
                  .noneMatch(network -> network.networkId() == initial.networkId()),
              "removed network must remain absent after a fresh core handshake");
        });
  }

  private static QuasselCoreControlPort.QuasselCoreNetworkSummary awaitNetworkState(
      ConnectedNetwork session,
      int networkId,
      Predicate<QuasselCoreControlPort.QuasselCoreNetworkSummary> predicate,
      String expected)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + NETWORK_SYNC_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNs) {
      for (var network : session.service().quasselCoreNetworks(session.serverId())) {
        if (network.networkId() == networkId && predicate.test(network)) return network;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for network "
            + networkId
            + " to be "
            + expected
            + "; networks="
            + session.service().quasselCoreNetworks(session.serverId()));
    throw new IllegalStateException("unreachable");
  }

  private static List<IrcEvent.ChannelMessage> seedMessages(
      ConnectedNetwork session, String prefix, int count) throws Exception {
    List<IrcEvent.ChannelMessage> seeded = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String text = prefix + index;
      session.bot().privmsg(session.channel(), text);
      seeded.add(
          awaitChannelMessage(
              session.events(),
              session.serverId(),
              session.channel(),
              session.botNick(),
              text,
              0,
              MESSAGE_TIMEOUT));
    }
    return seeded;
  }

  private static void awaitNextTimestampSecond(Instant previous) throws InterruptedException {
    // Core's negotiated legacy Message format exposes second precision timestamps.
    long deadlineNs = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    while (Instant.now().getEpochSecond() <= previous.getEpochSecond()) {
      assertTrue(
          System.nanoTime() < deadlineNs, "clock did not advance past the observed timestamp");
      Thread.sleep(POLL_INTERVAL_MS);
    }
  }

  private static void awaitInboundBarrier(ConnectedNetwork session, String text) throws Exception {
    session.bot().privmsg(session.channel(), text);
    awaitChannelMessage(
        session.events(),
        session.serverId(),
        session.channel(),
        session.botNick(),
        text,
        0,
        MESSAGE_TIMEOUT);
  }

  private static void assertEventsInOrderOnce(ConnectedNetwork session, List<IrcEvent> expected) {
    List<IrcEvent> actual =
        new ArrayList<>(session.events().values())
            .stream()
                .filter(event -> session.serverId().equals(event.serverId()))
                .map(ServerIrcEvent::event)
                .toList();
    int previous = -1;
    for (IrcEvent event : expected) {
      int index = actual.indexOf(event);
      assertTrue(
          index > previous, "events must retain wire order: " + expected + "; actual=" + actual);
      assertEquals(1L, actual.stream().filter(event::equals).count(), "duplicate event: " + event);
      previous = index;
    }
  }

  private static <T extends IrcEvent> T awaitMatchingEvent(
      ConnectedNetwork session, Class<T> eventType, Predicate<T> predicate)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNs) {
      Optional<T> match =
          matchingEvents(session.events(), session.serverId(), eventType).stream()
              .filter(predicate)
              .findFirst();
      if (match.isPresent()) return match.orElseThrow();
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for matching "
            + eventType.getSimpleName()
            + "; recent events: "
            + summarizeRecentEvents(session.events(), session.serverId(), 16));
    throw new IllegalStateException("unreachable");
  }

  private static void awaitMarkerTimestamp(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String target,
      java.time.Instant timestamp,
      int alreadySeenCount)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (matchingEvents(events, serverId, IrcEvent.ReadMarkerObserved.class).stream()
          .skip(alreadySeenCount)
          .anyMatch(
              marker ->
                  target.equals(marker.target())
                      && timestamp.equals(
                          java.time.Instant.parse(
                              marker.marker().substring("timestamp=".length()))))) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "No read marker for "
            + target
            + " at "
            + timestamp
            + "; recent events: "
            + summarizeRecentEvents(events, serverId, 12));
  }

  private static void awaitJoinedChannel(ConnectedNetwork session, String channel)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + JOIN_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (matchingEvents(session.events(), session.serverId(), IrcEvent.JoinedChannel.class)
          .stream()
          .anyMatch(event -> channel.equals(event.channel()))) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for joined channel "
            + channel
            + "; recent events: "
            + summarizeRecentEvents(session.events(), session.serverId(), 16));
  }

  private static List<ChatHistoryEntry> requestHistory(
      ConnectedNetwork session, Completable request) throws Exception {
    return requestHistory(session, session.channel(), request);
  }

  private static List<ChatHistoryEntry> requestHistory(
      ConnectedNetwork session, String target, Completable request) throws Exception {
    int before =
        countEvents(session.events(), session.serverId(), IrcEvent.ChatHistoryBatchReceived.class);
    request.blockingAwait();
    IrcEvent.ChatHistoryBatchReceived batch =
        awaitNextEvent(
            session.events(),
            session.serverId(),
            IrcEvent.ChatHistoryBatchReceived.class,
            before,
            MESSAGE_TIMEOUT);
    assertEquals(target, batch.target());
    assertTrue(
        batch.batchId().startsWith("quassel-backlog-sync-"), "expected a BacklogManager response");
    return batch.entries();
  }

  private static void assertHistoryMatches(
      List<ChatHistoryEntry> actual, List<IrcEvent.ChannelMessage> expected, String target) {
    assertEquals(
        expected.stream().map(IrcEvent.ChannelMessage::text).toList(),
        actual.stream().map(ChatHistoryEntry::text).toList(),
        "history must be complete and ordered oldest to newest");
    assertEquals(
        expected.stream().map(IrcEvent.ChannelMessage::messageId).toList(),
        actual.stream().map(ChatHistoryEntry::messageId).toList());
    assertEquals(
        expected.stream().map(IrcEvent.ChannelMessage::at).toList(),
        actual.stream().map(ChatHistoryEntry::at).toList());
    for (ChatHistoryEntry entry : actual) {
      assertEquals(target, entry.target());
      assertEquals(ChatHistoryEntry.Kind.PRIVMSG, entry.kind());
      assertEquals(expected.getFirst().from(), entry.from());
    }
  }

  @FunctionalInterface
  private interface SecondNetworkScenario {
    void run(SecondNetwork second) throws Exception;
  }

  private record SecondNetwork(String name, int networkId, SimpleIrcBot bot) {}

  private static void withSecondNetwork(ConnectedNetwork session, SecondNetworkScenario scenario)
      throws Exception {
    E2eConfig cfg = session.cfg();
    String secondAlias = cfg.ircAlias() + "-second";
    try (GenericContainer<?> secondServer =
        new GenericContainer<>(DockerImageName.parse(cfg.ircImage()))
            .withNetwork(session.dockerNetwork())
            .withNetworkAliases(secondAlias)
            .withExposedPorts(cfg.ircPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      secondServer.start();
      try (SimpleIrcBot secondBot =
          SimpleIrcBot.connect(
              secondServer.getHost(), secondServer.getMappedPort(cfg.ircPort()), cfg.botNick())) {
        secondBot.join(cfg.channel(), IRC_BOT_TIMEOUT);
        String secondName = "second-network";
        session
            .service()
            .quasselCoreCreateNetwork(
                session.serverId(),
                new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
                    secondName,
                    secondAlias,
                    cfg.ircPort(),
                    false,
                    "",
                    true,
                    null,
                    List.of(cfg.channel())))
            .blockingAwait();
        var secondNetwork =
            tryAwaitNetworkObserved(
                session.service(), session.serverId(), secondName, NETWORK_SYNC_TIMEOUT);
        assertNotNull(secondNetwork, "second network must be observed by its requested name");
        session
            .service()
            .quasselCoreConnectNetwork(
                session.serverId(), Integer.toString(secondNetwork.networkId()))
            .blockingAwait();
        awaitNetworkConnected(
            session.service(), session.serverId(), secondNetwork.networkId(), NETWORK_SYNC_TIMEOUT);

        awaitJoinedChannel(session, cfg.channel() + "{net:" + secondName + "}");
        scenario.run(new SecondNetwork(secondName, secondNetwork.networkId(), secondBot));
      } catch (Exception | AssertionError failure) {
        throw new AssertionError(
            "Second IRC server log tail:\n" + containerLogTail(secondServer), failure);
      }
    }
  }

  @FunctionalInterface
  private interface NetworkScenario {
    void run(ConnectedNetwork session) throws Exception;
  }

  private record ConnectedNetwork(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      SimpleIrcBot bot,
      E2eConfig cfg,
      Network dockerNetwork,
      GenericContainer<?> core,
      GenericContainer<?> ircServer,
      String networkName,
      AtomicReference<Socket> transportSocket,
      RuntimeCoreConfig runtimeConfig) {
    String serverId() {
      return cfg.serverId();
    }

    String channel() {
      return cfg.channel();
    }

    String botNick() {
      return cfg.botNick();
    }
  }

  private static void withConnectedNetwork(NetworkScenario scenario) throws Exception {
    withConnectedNetwork(null, scenario);
  }

  private static void withConnectedNetwork(Path tlsDirectory, NetworkScenario scenario)
      throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel network E2E test disabled. Set -Dquassel.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName quasselImage = DockerImageName.parse(cfg.quasselImage());
    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());

    try (Network network = Network.newNetwork();
        GenericContainer<?> ircServer =
            new GenericContainer<>(ircImage)
                .withNetwork(network)
                .withNetworkAliases(cfg.ircAlias())
                .withExposedPorts(cfg.ircPort())
                .withEnv("TZ", "UTC")
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
        GenericContainer<?> core =
            new GenericContainer<>(quasselImage)
                .withNetwork(network)
                .withExposedPorts(cfg.quasselPort())
                .withEnv("TZ", "UTC")
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      ircServer.start();
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.quasselPort()));
      try (QuasselContainerTlsGateway tls =
          tlsDirectory == null
              ? null
              : new QuasselContainerTlsGateway(
                  tlsDirectory, runtimeCfg.host(), runtimeCfg.port())) {
        if (tls != null) runtimeCfg = runtimeCfg.withTlsEndpoint(tls.host(), tls.port(), true);
        AtomicReference<Socket> transportSocket = new AtomicReference<>();
        ConcurrentLinkedDeque<QuasselCoreDatastreamCodec.MessageValue> nativeMessages =
            new ConcurrentLinkedDeque<>();
        QuasselCoreIrcClientService service =
            newService(runtimeCfg, transportSocket, nativeMessages);
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try (SimpleIrcBot bot =
            SimpleIrcBot.connect(
                ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), cfg.botNick())) {
          ensureConnectedWithSetupIfNeeded(service, events, runtimeCfg);
          String sid = runtimeCfg.serverId();
          assertEquals("", service.backendAvailabilityReason(sid));

          String networkName = "it-net-" + Long.toHexString(System.currentTimeMillis());
          QuasselCoreControlPort.QuasselCoreNetworkCreateRequest create =
              new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
                  networkName, cfg.ircAlias(), cfg.ircPort(), false, "", true, null, List.of());
          service.quasselCoreCreateNetwork(sid, create).blockingAwait();

          QuasselCoreControlPort.QuasselCoreNetworkSummary createdNetwork =
              tryAwaitNetworkObserved(service, sid, networkName, Duration.ofSeconds(20));
          if (createdNetwork == null) {
            reconnectAndAwaitReady(service, events, sid);
            createdNetwork =
                tryAwaitNetworkObserved(service, sid, networkName, NETWORK_SYNC_TIMEOUT);
          }
          assertNotNull(
              createdNetwork,
              "Requested Quassel network '"
                  + networkName
                  + "' was not observed after creation; networks="
                  + service.quasselCoreNetworks(sid)
                  + "; recent events: "
                  + summarizeRecentEvents(events, sid, 16));
          service
              .quasselCoreConnectNetwork(sid, Integer.toString(createdNetwork.networkId()))
              .blockingAwait();
          awaitNetworkConnected(service, sid, createdNetwork.networkId(), NETWORK_SYNC_TIMEOUT);

          bot.join(cfg.channel(), IRC_BOT_TIMEOUT);
          int joinedCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
          service.joinChannel(sid, cfg.channel()).blockingAwait();
          IrcEvent.JoinedChannel joined =
              awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
          assertEquals(cfg.channel(), joined.channel());

          scenario.run(
              new ConnectedNetwork(
                  service,
                  events,
                  bot,
                  cfg,
                  network,
                  core,
                  ircServer,
                  networkName,
                  transportSocket,
                  runtimeCfg));
        } catch (Exception | AssertionError failure) {
          throw new AssertionError(
              "Quassel network round trip failed; core log tail:\n"
                  + containerLogTail(core)
                  + "\nIRC server log tail:\n"
                  + containerLogTail(ircServer)
                  + "\nRecent native display messages:\n"
                  + nativeMessages,
              failure);
        } finally {
          try {
            service.disconnect(runtimeCfg.serverId(), "container e2e shutdown").blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
          try {
            service.shutdownNow();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  private static String containerLogTail(GenericContainer<?> container) {
    String logs = container.getLogs();
    return logs.substring(Math.max(0, logs.length() - 8_192));
  }

  private static void controlContainerService(
      GenericContainer<?> container, String service, String command) throws Exception {
    // Restart the daemon, preserving Docker's mapped ports and the Core's /config storage.
    var result = container.execInContainer("s6-svc", command, "/run/service/" + service);
    assertEquals(0, result.getExitCode(), result.getStdout() + result.getStderr());
  }

  private static QuasselCoreIrcClientService newService(
      RuntimeCoreConfig cfg, AtomicReference<Socket> transportSocket) throws Exception {
    return newService(cfg, transportSocket, new ConcurrentLinkedDeque<>());
  }

  private static QuasselCoreIrcClientService newService(
      RuntimeCoreConfig cfg,
      AtomicReference<Socket> transportSocket,
      ConcurrentLinkedDeque<QuasselCoreDatastreamCodec.MessageValue> nativeMessages)
      throws Exception {
    IrcProperties.Server server = cfg.toServer();

    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.require(cfg.serverId())).thenReturn(server);
    when(serverCatalog.find(cfg.serverId())).thenReturn(Optional.of(server));
    when(serverCatalog.containsId(cfg.serverId())).thenReturn(true);

    ServerProxyResolver proxyResolver = new ServerProxyResolver(serverCatalog);
    QuasselCoreSocketConnector socketConnector = spy(new QuasselCoreSocketConnector(proxyResolver));
    doAnswer(
            invocation -> {
              Socket socket = (Socket) invocation.callRealMethod();
              transportSocket.set(socket);
              return socket;
            })
        .when(socketConnector)
        .connect(server);
    QuasselCoreProtocolProbe protocolProbe = new QuasselCoreProtocolProbe();
    QuasselCoreDatastreamCodec datastreamCodec =
        new QuasselCoreDatastreamCodec() {
          @Override
          public SignalProxyMessage readSignalProxyMessage(java.io.InputStream input)
              throws IOException {
            SignalProxyMessage message = super.readSignalProxyMessage(input);
            if ("2displayMsg(Message)".equals(message.slotName())
                && !message.params().isEmpty()
                && message.params().getFirst() instanceof MessageValue display) {
              nativeMessages.addLast(display);
              while (nativeMessages.size() > 32) nativeMessages.pollFirst();
            }
            return message;
          }
        };
    QuasselCoreAuthHandshake authHandshake = new QuasselCoreAuthHandshake(datastreamCodec);

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe IT",
                new IrcProperties.Reconnect(true, 250, 1_000, 1.5, 0, 8),
                null,
                null,
                null),
            List.of(server));

    return QuasselRuntimeTestFixtures.service(
        serverCatalog, socketConnector, protocolProbe, authHandshake, datastreamCodec, props);
  }

  private static void ensureConnectedWithSetupIfNeeded(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      RuntimeCoreConfig runtimeCfg)
      throws Exception {
    String sid = runtimeCfg.serverId();
    int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
    service.connect(sid).blockingAwait();

    ConnectOutcome firstOutcome =
        awaitConnectOutcome(service, events, sid, readyCount, SETUP_TIMEOUT);
    if (firstOutcome == ConnectOutcome.READY) {
      return;
    }

    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        service
            .quasselCoreSetupPrompt(sid)
            .orElseThrow(
                () -> new IllegalStateException("setup pending but no setup prompt found"));
    QuasselCoreControlPort.QuasselCoreSetupRequest setup =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
            runtimeCfg.login(),
            runtimeCfg.password(),
            firstNonBlank(prompt.storageBackends(), "SQLite"),
            firstNonBlank(prompt.authenticators(), "Database"),
            Map.of("DatabaseName", "quassel-storage"),
            Map.of());
    service.submitQuasselCoreSetup(sid, setup).blockingAwait();

    int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
    service.connect(sid).blockingAwait();
    awaitNextEvent(
        events, sid, IrcEvent.ConnectionReady.class, reconnectReadyCount, CONNECT_TIMEOUT);
  }

  private static QuasselCoreControlPort.QuasselCoreNetworkSummary tryAwaitNetworkObserved(
      QuasselCoreIrcClientService service, String serverId, String networkName, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        if (!networkName.equalsIgnoreCase(Objects.toString(summary.networkName(), "").trim()))
          continue;
        return summary;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return null;
  }

  private static void awaitNetworkConnected(
      QuasselCoreIrcClientService service, String serverId, int networkId, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        if (summary.networkId() != networkId) continue;
        if (summary.connected()) return;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail("Timed out waiting for Quassel network " + networkId + " to report connected=true");
  }

  private static ConnectOutcome awaitConnectOutcome(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      int alreadySeenReadyCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (countEvents(events, serverId, IrcEvent.ConnectionReady.class) > alreadySeenReadyCount) {
        return ConnectOutcome.READY;
      }
      if (service.isQuasselCoreSetupPending(serverId)) {
        return ConnectOutcome.SETUP_REQUIRED;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail("Timed out waiting for Quassel connect outcome (ready or setup-required)");
    throw new IllegalStateException("unreachable");
  }

  private static void reconnectAndAwaitReady(
      QuasselCoreIrcClientService service, TestSubscriber<ServerIrcEvent> events, String serverId)
      throws InterruptedException {
    int readyCount = countEvents(events, serverId, IrcEvent.ConnectionReady.class);
    service.disconnect(serverId, "refresh network snapshot").blockingAwait();
    service.connect(serverId).blockingAwait();
    awaitNextEvent(events, serverId, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
  }

  private static int countChannelMessages(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String channel,
      String fromNick,
      String expectedTextPart) {
    int count = 0;
    for (ServerIrcEvent event : new ArrayList<>(events.values())) {
      if (event == null || !Objects.equals(serverId, event.serverId())) continue;
      if (!(event.event() instanceof IrcEvent.ChannelMessage msg)) continue;
      if (!channel.equalsIgnoreCase(Objects.toString(msg.channel(), "").trim())) continue;
      if (!fromNick.equalsIgnoreCase(Objects.toString(msg.from(), "").trim())) continue;
      if (!Objects.toString(msg.text(), "").contains(expectedTextPart)) continue;
      count++;
    }
    return count;
  }

  private static IrcEvent.ChannelMessage awaitChannelMessage(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String channel,
      String fromNick,
      String expectedTextPart,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      int seen = countChannelMessages(events, serverId, channel, fromNick, expectedTextPart);
      if (seen > alreadySeenCount) {
        return matchingEvents(events, serverId, IrcEvent.ChannelMessage.class).stream()
            .filter(message -> channel.equalsIgnoreCase(message.channel()))
            .filter(message -> fromNick.equalsIgnoreCase(message.from()))
            .filter(message -> message.text().contains(expectedTextPart))
            .skip(alreadySeenCount)
            .findFirst()
            .orElseThrow();
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for ChannelMessage from "
            + fromNick
            + " in "
            + channel
            + "; recent events: "
            + summarizeRecentEvents(events, serverId, 16));
    throw new IllegalStateException("unreachable");
  }

  private static <T extends IrcEvent> int countEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    return matchingEvents(events, serverId, eventType).size();
  }

  private static <T extends IrcEvent> T awaitNextEvent(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      Class<T> eventType,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<T> matches = matchingEvents(events, serverId, eventType);
      if (matches.size() > alreadySeenCount) {
        return matches.get(alreadySeenCount);
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for "
            + eventType.getSimpleName()
            + " on server '"
            + serverId
            + "'; recent events: "
            + summarizeRecentEvents(events, serverId, 16));
    throw new IllegalStateException("unreachable");
  }

  private static <T extends IrcEvent> List<T> matchingEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    List<ServerIrcEvent> all = new ArrayList<>(events.values());
    ArrayList<T> out = new ArrayList<>();
    for (ServerIrcEvent serverEvent : all) {
      if (serverEvent == null || !Objects.equals(serverId, serverEvent.serverId())) continue;
      if (!eventType.isInstance(serverEvent.event())) continue;
      out.add(eventType.cast(serverEvent.event()));
    }
    return out;
  }

  private static String summarizeRecentEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, int limit) {
    if (limit <= 0) return "";
    List<ServerIrcEvent> all = new ArrayList<>(events.values());
    ArrayList<String> lines = new ArrayList<>();
    for (ServerIrcEvent serverEvent : all) {
      if (serverEvent == null || !Objects.equals(serverId, serverEvent.serverId())) continue;
      IrcEvent event = serverEvent.event();
      if (event == null) continue;
      String detail =
          switch (event) {
            case IrcEvent.Error err -> "Error(" + Objects.toString(err.message(), "") + ")";
            case IrcEvent.Disconnected disc ->
                "Disconnected(" + Objects.toString(disc.reason(), "") + ")";
            case IrcEvent.ConnectionFeaturesUpdated updated ->
                "Features(" + Objects.toString(updated.source(), "") + ")";
            case IrcEvent.Connected connected ->
                "Connected(" + Objects.toString(connected.nick(), "") + ")";
            case IrcEvent.Connecting ignored -> "Connecting";
            case IrcEvent.ConnectionReady ignored -> "ConnectionReady";
            case IrcEvent.JoinedChannel joined ->
                "Joined(" + Objects.toString(joined.channel(), "") + ")";
            case IrcEvent.ChannelMessage msg ->
                "ChanMsg("
                    + Objects.toString(msg.channel(), "")
                    + ","
                    + Objects.toString(msg.from(), "")
                    + ","
                    + Objects.toString(msg.text(), "")
                    + ")";
            default -> event.toString();
          };
      lines.add(detail);
    }
    if (lines.isEmpty()) return "<none>";
    int start = Math.max(0, lines.size() - limit);
    return String.join(" | ", lines.subList(start, lines.size()));
  }

  private static String firstNonBlank(List<String> values, String fallback) {
    if (values != null) {
      for (String value : values) {
        String candidate = Objects.toString(value, "").trim();
        if (!candidate.isEmpty()) return candidate;
      }
    }
    return Objects.toString(fallback, "").trim();
  }

  private enum ConnectOutcome {
    READY,
    SETUP_REQUIRED
  }

  private record RuntimeCoreConfig(
      String serverId,
      String host,
      int port,
      String login,
      String password,
      String nick,
      String realName,
      boolean tls,
      boolean trustAllCertificates) {
    RuntimeCoreConfig withTlsEndpoint(String host, int port, boolean trustAllCertificates) {
      return new RuntimeCoreConfig(
          serverId, host, port, login, password, nick, realName, true, trustAllCertificates);
    }

    IrcProperties.Server toServer() {
      return IrcPropertiesTestFixtures.serverBuilder(serverId)
          .host(host)
          .port(port)
          .tls(tls)
          .trustAllCertificates(trustAllCertificates)
          .serverPassword(password)
          .nick(nick)
          .login(login)
          .realName(realName)
          .proxy(new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000))
          .backend(IrcProperties.Server.Backend.QUASSEL_CORE)
          .build();
    }
  }

  private record E2eConfig(
      boolean enabled,
      String quasselImage,
      String ircImage,
      long startupTimeoutSeconds,
      String serverId,
      String login,
      String password,
      String nick,
      String realName,
      String ircAlias,
      String botNick,
      String channel,
      String messageText) {
    private static final String DEFAULT_QUASSEL_IMAGE = "linuxserver/quassel-core:0.14.0";
    private static final String DEFAULT_IRC_IMAGE = "linuxserver/ngircd:latest";
    private static final long DEFAULT_STARTUP_TIMEOUT_SECONDS = 180L;
    private static final String DEFAULT_SERVER_ID = "quassel-it-e2e";
    private static final String DEFAULT_LOGIN = "ircafe-it";
    private static final String DEFAULT_PASSWORD = "ircafe-it-password";
    private static final String DEFAULT_REAL_NAME = "IRCafe IT";
    private static final String DEFAULT_IRC_ALIAS = "irc-e2e";
    private static final String DEFAULT_BOT_NICK = "e2ebot";
    private static final String DEFAULT_CHANNEL = "#quassel-e2e";
    private static final String DEFAULT_MESSAGE = "hello-from-e2e-bot";

    static E2eConfig fromSystem() {
      boolean enabled =
          readBoolean(
              "quassel.it.container.e2e.enabled", "QUASSEL_IT_CONTAINER_E2E_ENABLED", false);
      String quasselImage =
          readString(
              "quassel.it.container.e2e.quassel-image",
              "QUASSEL_IT_CONTAINER_E2E_QUASSEL_IMAGE",
              DEFAULT_QUASSEL_IMAGE);
      String ircImage =
          readString(
              "quassel.it.container.e2e.irc-image",
              "QUASSEL_IT_CONTAINER_E2E_IRC_IMAGE",
              DEFAULT_IRC_IMAGE);
      long timeoutSeconds =
          readLong(
              "quassel.it.container.e2e.startup-timeout-seconds",
              "QUASSEL_IT_CONTAINER_E2E_STARTUP_TIMEOUT_SECONDS",
              DEFAULT_STARTUP_TIMEOUT_SECONDS);
      String serverId =
          readString(
              "quassel.it.container.e2e.server-id",
              "QUASSEL_IT_CONTAINER_E2E_SERVER_ID",
              DEFAULT_SERVER_ID);
      String login =
          readString(
              "quassel.it.container.e2e.login", "QUASSEL_IT_CONTAINER_E2E_LOGIN", DEFAULT_LOGIN);
      String password =
          readString(
              "quassel.it.container.e2e.password",
              "QUASSEL_IT_CONTAINER_E2E_PASSWORD",
              DEFAULT_PASSWORD);
      String nick =
          readString("quassel.it.container.e2e.nick", "QUASSEL_IT_CONTAINER_E2E_NICK", login);
      String realName =
          readString(
              "quassel.it.container.e2e.real-name",
              "QUASSEL_IT_CONTAINER_E2E_REAL_NAME",
              DEFAULT_REAL_NAME);
      String ircAlias =
          readString(
              "quassel.it.container.e2e.irc-alias",
              "QUASSEL_IT_CONTAINER_E2E_IRC_ALIAS",
              DEFAULT_IRC_ALIAS);
      String botNick =
          readString(
              "quassel.it.container.e2e.bot-nick",
              "QUASSEL_IT_CONTAINER_E2E_BOT_NICK",
              DEFAULT_BOT_NICK);
      String channel =
          readString(
              "quassel.it.container.e2e.channel",
              "QUASSEL_IT_CONTAINER_E2E_CHANNEL",
              DEFAULT_CHANNEL);
      String messageText =
          readString(
              "quassel.it.container.e2e.message",
              "QUASSEL_IT_CONTAINER_E2E_MESSAGE",
              DEFAULT_MESSAGE);

      return new E2eConfig(
          enabled,
          safeTrim(quasselImage, DEFAULT_QUASSEL_IMAGE),
          safeTrim(ircImage, DEFAULT_IRC_IMAGE),
          Math.max(30L, timeoutSeconds),
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(login, DEFAULT_LOGIN),
          Objects.toString(password, DEFAULT_PASSWORD),
          safeTrim(nick, "ircafe-it"),
          safeTrim(realName, DEFAULT_REAL_NAME),
          safeTrim(ircAlias, DEFAULT_IRC_ALIAS),
          safeTrim(botNick, DEFAULT_BOT_NICK),
          normalizeChannel(channel),
          safeTrim(messageText, DEFAULT_MESSAGE));
    }

    int quasselPort() {
      return 4242;
    }

    int ircPort() {
      return 6667;
    }

    RuntimeCoreConfig toRuntimeConfig(String host, int mappedPort) {
      return new RuntimeCoreConfig(
          serverId, host, mappedPort, login, password, nick, realName, false, false);
    }

    private static String normalizeChannel(String raw) {
      String value = safeTrim(raw, DEFAULT_CHANNEL);
      return value.startsWith("#") ? value : ("#" + value);
    }

    private static String readString(String propName, String envName, String fallback) {
      String prop = System.getProperty(propName);
      if (prop != null) return prop;
      String env = System.getenv(envName);
      if (env != null) return env;
      return fallback;
    }

    private static boolean readBoolean(String propName, String envName, boolean fallback) {
      String raw = readString(propName, envName, Boolean.toString(fallback));
      if (raw == null) return fallback;
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
        case "1", "true", "yes", "y", "on" -> true;
        case "0", "false", "no", "n", "off" -> false;
        default -> fallback;
      };
    }

    private static long readLong(String propName, String envName, long fallback) {
      String raw = readString(propName, envName, Long.toString(fallback)).trim();
      if (raw.isEmpty()) return fallback;
      try {
        return Long.parseLong(raw);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException(
            "Invalid long for " + propName + "/" + envName + ": '" + raw + "'", nfe);
      }
    }

    private static String safeTrim(String value, String fallback) {
      String trimmed = Objects.toString(value, "").trim();
      return trimmed.isEmpty() ? fallback : trimmed;
    }
  }

  private static final class SimpleIrcBot implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String nick;

    private SimpleIrcBot(Socket socket, BufferedReader in, BufferedWriter out, String nick) {
      this.socket = socket;
      this.in = in;
      this.out = out;
      this.nick = nick;
    }

    static SimpleIrcBot connect(String host, int port, String nick) throws Exception {
      String normalizedNick = Objects.toString(nick, "").trim();
      if (normalizedNick.isEmpty()) {
        throw new IllegalArgumentException("bot nick is blank");
      }
      Socket socket = new Socket(host, port);
      socket.setSoTimeout(250);
      BufferedReader in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      BufferedWriter out =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      SimpleIrcBot bot = new SimpleIrcBot(socket, in, out, normalizedNick);
      try {
        bot.sendLine("NICK " + normalizedNick);
        bot.sendLine("USER " + normalizedNick + " 0 * :" + normalizedNick);
        bot.awaitWelcome(IRC_BOT_TIMEOUT);
        return bot;
      } catch (Exception | AssertionError failure) {
        socket.close();
        throw failure;
      }
    }

    void join(String channel, Duration timeout) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      if (chan.isEmpty()) {
        throw new IllegalArgumentException("channel is blank");
      }
      sendLine("JOIN " + chan);
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (line.contains(" JOIN :" + chan) || line.contains(" JOIN " + chan)) {
          return;
        }
      }
      throw new IllegalStateException("timed out waiting bot join ack for " + chan);
    }

    void privmsg(String channel, String text) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String msg = Objects.toString(text, "").strip();
      if (chan.isEmpty() || msg.isEmpty()) {
        throw new IllegalArgumentException("privmsg channel/text is blank");
      }
      sendLine("PRIVMSG " + chan + " :" + msg);
    }

    void awaitMembershipChange(String from, String command, String channel, Duration timeout)
        throws Exception {
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (!line.startsWith(":" + from + "!")) continue;
        int prefixEnd = line.indexOf(' ');
        String payload = line.substring(prefixEnd + 1);
        if (!payload.startsWith(command + " ")) continue;
        if (channel.isEmpty()
            || payload.substring(command.length() + 1).replaceFirst("^:", "").equals(channel)) {
          return;
        }
      }
      fail("Timed out waiting for " + command + " from " + from + " in " + channel);
    }

    void awaitMessage(String from, String command, String target, String text, Duration timeout)
        throws Exception {
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      List<String> recent = new ArrayList<>();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        recent.add(line);
        if (recent.size() > 16) recent.removeFirst();
        if (line.startsWith(":" + from + "!")
            && line.endsWith(" " + command + " " + target + " :" + text)) return;
      }
      fail(
          "Timed out waiting for "
              + command
              + " from "
              + from
              + " to "
              + target
              + " with text '"
              + text
              + "'; recent IRC lines: "
              + recent);
    }

    void awaitMessages(
        String from, Map<String, String> expectedByTarget, List<String> forbidden, Duration timeout)
        throws Exception {
      Map<String, String> pending = new LinkedHashMap<>(expectedByTarget);
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      long quietDeadlineNs = Long.MAX_VALUE;
      List<String> recent = new ArrayList<>();
      while (System.nanoTime() < Math.min(deadlineNs, quietDeadlineNs)) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        recent.add(line);
        if (recent.size() > 16) recent.removeFirst();
        for (String text : forbidden) {
          assertTrue(!line.endsWith(" :" + text), "message leaked to wrong network: " + line);
        }
        if (!line.startsWith(":" + from + "!")) continue;
        pending
            .entrySet()
            .removeIf(
                entry -> line.endsWith(" PRIVMSG " + entry.getKey() + " :" + entry.getValue()));
        if (pending.isEmpty() && quietDeadlineNs == Long.MAX_VALUE) {
          quietDeadlineNs = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        }
      }
      assertTrue(
          pending.isEmpty(),
          "Missing routed messages " + pending + "; recent IRC lines: " + recent);
    }

    private void awaitWelcome(Duration timeout) throws Exception {
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (line.contains(" 001 " + nick + " ")) {
          return;
        }
      }
      throw new IllegalStateException("timed out waiting IRC bot welcome");
    }

    private String readLine() throws IOException {
      try {
        String line = in.readLine();
        if (line == null) throw new IOException("IRC bot connection closed while awaiting a reply");
        return line;
      } catch (java.net.SocketTimeoutException timeout) {
        return null;
      }
    }

    private void sendLine(String line) throws IOException {
      String value = Objects.toString(line, "").strip();
      if (value.isEmpty()) return;
      out.write(value);
      out.write("\r\n");
      out.flush();
    }

    @Override
    public void close() throws Exception {
      try {
        sendLine("QUIT :bye");
      } catch (Exception ignored) {
      }
      try {
        socket.close();
      } catch (Exception ignored) {
      }
    }
  }
}
