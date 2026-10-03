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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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
  void duplicateChannelAndNickNamesStayIsolatedAcrossNetworks() throws Exception {
    withConnectedNetwork(
        session -> {
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
                    secondServer.getHost(),
                    secondServer.getMappedPort(cfg.ircPort()),
                    cfg.botNick())) {
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
                  session.service(),
                  session.serverId(),
                  secondNetwork.networkId(),
                  NETWORK_SYNC_TIMEOUT);

              String firstTarget = cfg.channel() + "{net:" + session.networkName() + "}";
              String secondTarget = cfg.channel() + "{net:" + secondName + "}";
              awaitJoinedChannel(session, secondTarget);
              session.bot().privmsg(cfg.channel(), "first network history");
              secondBot.privmsg(cfg.channel(), "second network history");
              var firstMessage =
                  awaitChannelMessage(
                      session.events(),
                      session.serverId(),
                      firstTarget,
                      cfg.botNick(),
                      "first network history",
                      0,
                      MESSAGE_TIMEOUT);
              var secondMessage =
                  awaitChannelMessage(
                      session.events(),
                      session.serverId(),
                      secondTarget,
                      cfg.botNick(),
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
                      cfg.botNick() + "{net:" + session.networkName() + "}",
                      "first private only")
                  .blockingAwait();
              session
                  .service()
                  .sendPrivateMessage(
                      session.serverId(),
                      cfg.botNick() + "{net:" + secondName + "}",
                      "second private only")
                  .blockingAwait();
              session
                  .bot()
                  .awaitMessages(
                      from,
                      Map.of(
                          cfg.channel(), "first channel only", cfg.botNick(), "first private only"),
                      List.of("second channel only", "second private only"),
                      MESSAGE_TIMEOUT);
              secondBot.awaitMessages(
                  from,
                  Map.of(
                      cfg.channel(), "second channel only", cfg.botNick(), "second private only"),
                  List.of("first channel only", "first private only"),
                  MESSAGE_TIMEOUT);
            } catch (Exception | AssertionError failure) {
              throw new AssertionError(
                  "Second IRC server log tail:\n" + containerLogTail(secondServer), failure);
            }
          }
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
  private interface NetworkScenario {
    void run(ConnectedNetwork session) throws Exception;
  }

  private record ConnectedNetwork(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      SimpleIrcBot bot,
      E2eConfig cfg,
      Network dockerNetwork,
      String networkName,
      AtomicReference<Socket> transportSocket) {
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
      AtomicReference<Socket> transportSocket = new AtomicReference<>();
      QuasselCoreIrcClientService service = newService(runtimeCfg, transportSocket);
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
          createdNetwork = tryAwaitNetworkObserved(service, sid, networkName, NETWORK_SYNC_TIMEOUT);
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
            new ConnectedNetwork(service, events, bot, cfg, network, networkName, transportSocket));
      } catch (Exception | AssertionError failure) {
        throw new AssertionError(
            "Quassel network round trip failed; core log tail:\n"
                + containerLogTail(core)
                + "\nIRC server log tail:\n"
                + containerLogTail(ircServer),
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

  private static String containerLogTail(GenericContainer<?> container) {
    String logs = container.getLogs();
    return logs.substring(Math.max(0, logs.length() - 8_192));
  }

  private static QuasselCoreIrcClientService newService(
      RuntimeCoreConfig cfg, AtomicReference<Socket> transportSocket) throws Exception {
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
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
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
            default -> event.getClass().getSimpleName();
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
      String realName) {
    IrcProperties.Server toServer() {
      return IrcPropertiesTestFixtures.serverBuilder(serverId)
          .host(host)
          .port(port)
          .tls(false)
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
      return new RuntimeCoreConfig(serverId, host, mappedPort, login, password, nick, realName);
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
      bot.sendLine("NICK " + normalizedNick);
      bot.sendLine("USER " + normalizedNick + " 0 * :" + normalizedNick);
      bot.awaitWelcome(IRC_BOT_TIMEOUT);
      return bot;
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
      String msg = Objects.toString(text, "").trim();
      if (chan.isEmpty() || msg.isEmpty()) {
        throw new IllegalArgumentException("privmsg channel/text is blank");
      }
      sendLine("PRIVMSG " + chan + " :" + msg);
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
      String value = Objects.toString(line, "").trim();
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
