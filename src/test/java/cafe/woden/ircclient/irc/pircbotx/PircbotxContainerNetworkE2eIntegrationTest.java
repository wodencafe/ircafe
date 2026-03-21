package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Containerized IRC E2E coverage for the direct IRC backend path (Pircbotx + real ircd).
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * ./gradlew integrationTest --tests '*PircbotxContainerNetworkE2eIntegrationTest' \
 *   -Dirc.it.container.e2e.enabled=true
 * </pre>
 *
 * <p>Optional properties/env vars:
 *
 * <ul>
 *   <li>{@code irc.it.container.e2e.irc-image} / {@code IRC_IT_CONTAINER_E2E_IRC_IMAGE}
 *   <li>{@code irc.it.container.e2e.channel} / {@code IRC_IT_CONTAINER_E2E_CHANNEL}
 *   <li>{@code irc.it.container.e2e.bot-message} / {@code IRC_IT_CONTAINER_E2E_BOT_MESSAGE}
 *   <li>{@code irc.it.container.e2e.app-message} / {@code IRC_IT_CONTAINER_E2E_APP_MESSAGE}
 * </ul>
 */
class PircbotxContainerNetworkE2eIntegrationTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration LAG_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration IRC_BOT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration RECONNECT_READY_TIMEOUT = Duration.ofSeconds(120);
  private static final long POLL_INTERVAL_MS = 50L;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void ircBackendCanConnectJoinExchangeMessagesAndReconnectAgainstContainerIrcd() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());

    try (GenericContainer<?> ircServer =
        new GenericContainer<>(ircImage)
            .withExposedPorts(cfg.ircPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      ircServer.start();

      RuntimeIrcConfig runtimeCfg =
          cfg.toRuntimeConfig(ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()));
      try (ServiceFixture fixture = newService(runtimeCfg);
          SimpleIrcBot bot =
              SimpleIrcBot.connect(
                  ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), cfg.botNick())) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try {
          String sid = runtimeCfg.serverId();

          int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
          service.connect(sid).blockingAwait();
          awaitNextEvent(events, sid, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
          assertTrue(
              service.currentNick(sid).isPresent(), "currentNick should be set after connect");

          int joinedCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
          service.joinChannel(sid, cfg.channel()).blockingAwait();
          IrcEvent.JoinedChannel joined =
              awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
          assertEquals(cfg.channel(), joined.channel());

          bot.join(cfg.channel(), JOIN_TIMEOUT);

          int inboundCount =
              countChannelMessages(events, sid, cfg.channel(), cfg.botNick(), cfg.botMessage());
          bot.privmsg(cfg.channel(), cfg.botMessage());
          awaitChannelMessage(
              events,
              sid,
              cfg.channel(),
              cfg.botNick(),
              cfg.botMessage(),
              inboundCount,
              MESSAGE_TIMEOUT);

          service.sendToChannel(sid, cfg.channel(), cfg.appMessage()).blockingAwait();
          bot.awaitChannelPrivmsg(cfg.channel(), cfg.appMessage(), MESSAGE_TIMEOUT);

          service.requestLagProbe(sid).blockingAwait();
          OptionalLong lag = awaitLagSample(service, sid, LAG_TIMEOUT);
          assertTrue(lag.isPresent(), "expected lag sample after explicit lag probe");
          assertTrue(lag.orElseThrow() >= 0L);

          int disconnectedCount = countEvents(events, sid, IrcEvent.Disconnected.class);
          service.disconnect(sid, "container ircd e2e disconnect").blockingAwait();
          awaitNextEvent(
              events, sid, IrcEvent.Disconnected.class, disconnectedCount, CONNECT_TIMEOUT);

          int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
          service.connect(sid).blockingAwait();
          awaitNextEvent(
              events, sid, IrcEvent.ConnectionReady.class, reconnectReadyCount, CONNECT_TIMEOUT);

          int rejoinCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
          service.joinChannel(sid, cfg.channel()).blockingAwait();
          awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, rejoinCount, JOIN_TIMEOUT);
        } finally {
          try {
            service
                .disconnect(runtimeCfg.serverId(), "container ircd e2e shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendRecoversFromContainerRestartViaAutoReconnect() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    try (GenericContainer<?> ircServer = newIrcContainer(cfg)) {
      ircServer.start();

      String host = ircServer.getHost();
      int mappedPort = ircServer.getMappedPort(cfg.ircPort());
      RuntimeIrcConfig runtimeCfg = cfg.toRuntimeConfig(host, mappedPort);
      try (ServiceFixture fixture = newService(runtimeCfg);
          SimpleIrcBot bot = SimpleIrcBot.connect(host, mappedPort, cfg.botNick())) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try {
          String sid = runtimeCfg.serverId();
          int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
          service.connect(sid).blockingAwait();
          awaitNextEvent(events, sid, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);

          int joinedCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
          service.joinChannel(sid, cfg.channel()).blockingAwait();
          awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
          bot.join(cfg.channel(), JOIN_TIMEOUT);

          int baseMessageCount =
              countChannelMessages(events, sid, cfg.channel(), cfg.botNick(), cfg.botMessage());
          bot.privmsg(cfg.channel(), cfg.botMessage());
          awaitChannelMessage(
              events,
              sid,
              cfg.channel(),
              cfg.botNick(),
              cfg.botMessage(),
              baseMessageCount,
              MESSAGE_TIMEOUT);

          int disconnectedCount = countEvents(events, sid, IrcEvent.Disconnected.class);
          int reconnectingCount = countEvents(events, sid, IrcEvent.Reconnecting.class);
          int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);

          restartIrcDaemon(ircServer);
          awaitIrcListening(host, mappedPort, Duration.ofSeconds(40));

          awaitNextEvent(
              events, sid, IrcEvent.Disconnected.class, disconnectedCount, CONNECT_TIMEOUT);
          awaitNextEvent(
              events, sid, IrcEvent.Reconnecting.class, reconnectingCount, CONNECT_TIMEOUT);
          awaitNextEvent(
              events,
              sid,
              IrcEvent.ConnectionReady.class,
              reconnectReadyCount,
              RECONNECT_READY_TIMEOUT);

          int rejoinCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
          service.joinChannel(sid, cfg.channel()).blockingAwait();
          awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, rejoinCount, JOIN_TIMEOUT);
        } finally {
          try {
            service
                .disconnect(runtimeCfg.serverId(), "container ircd restart test shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendRejectsWrongServerPasswordAgainstPasswordProtectedContainer() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    String requiredPassword = "it-pass-" + Long.toUnsignedString(System.nanoTime(), 36);
    try (GenericContainer<?> ircServer = newIrcContainer(cfg, requiredPassword)) {
      ircServer.start();

      String host = ircServer.getHost();
      int mappedPort = ircServer.getMappedPort(cfg.ircPort());

      RuntimeIrcConfig validCfg =
          cfg.toRuntimeConfig(host, mappedPort).withServerPassword(requiredPassword);
      try (ServiceFixture fixture = newService(validCfg, false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();
        try {
          int readyCount = countEvents(events, validCfg.serverId(), IrcEvent.ConnectionReady.class);
          service.connect(validCfg.serverId()).blockingAwait();
          awaitNextEvent(
              events,
              validCfg.serverId(),
              IrcEvent.ConnectionReady.class,
              readyCount,
              CONNECT_TIMEOUT);
        } finally {
          try {
            service
                .disconnect(validCfg.serverId(), "container ircd auth baseline shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }

      RuntimeIrcConfig wrongCfg =
          cfg.toRuntimeConfig(host, mappedPort)
              .withServerId(validCfg.serverId() + "-wrong")
              .withServerPassword(requiredPassword + "-wrong");
      try (ServiceFixture fixture = newService(wrongCfg, false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();
        try {
          service.connect(wrongCfg.serverId()).blockingAwait();
          IrcEvent.Disconnected disconnected =
              awaitNextEvent(
                  events, wrongCfg.serverId(), IrcEvent.Disconnected.class, 0, CONNECT_TIMEOUT);
          String combined = (Objects.toString(disconnected.reason(), "")).toLowerCase(Locale.ROOT);
          assertEquals(0, countEvents(events, wrongCfg.serverId(), IrcEvent.ConnectionReady.class));
          assertTrue(!combined.isBlank(), "disconnect reason should not be blank");
        } finally {
          try {
            service
                .disconnect(wrongCfg.serverId(), "container ircd auth failure shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendAutoNickChangesWhenPreferredNickAlreadyInUse() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    try (GenericContainer<?> ircServer = newIrcContainer(cfg)) {
      ircServer.start();
      String host = ircServer.getHost();
      int mappedPort = ircServer.getMappedPort(cfg.ircPort());

      String preferredNick = "col";
      RuntimeIrcConfig runtimeCfg =
          cfg.toRuntimeConfig(host, mappedPort)
              .withServerId(cfg.serverId() + "-nick-collision")
              .withNickAndLogin(preferredNick, preferredNick);
      try (SimpleIrcBot existing = SimpleIrcBot.connect(host, mappedPort, preferredNick);
          ServiceFixture fixture = newService(runtimeCfg, false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();
        try {
          int readyCount =
              countEvents(events, runtimeCfg.serverId(), IrcEvent.ConnectionReady.class);
          service.connect(runtimeCfg.serverId()).blockingAwait();
          awaitNextEvent(
              events,
              runtimeCfg.serverId(),
              IrcEvent.ConnectionReady.class,
              readyCount,
              CONNECT_TIMEOUT);

          String actualNick = service.currentNick(runtimeCfg.serverId()).orElse("");
          assertTrue(!actualNick.isBlank(), "currentNick should be available after connect");
          assertTrue(
              !preferredNick.equalsIgnoreCase(actualNick),
              "expected auto-nick-change away from '"
                  + preferredNick
                  + "' but got '"
                  + actualNick
                  + "'");
        } finally {
          try {
            service
                .disconnect(runtimeCfg.serverId(), "container ircd nick collision shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendEmitsChannelLifecycleEventsFromRealIrcdFlow() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    try (GenericContainer<?> ircServer = newIrcContainer(cfg)) {
      ircServer.start();
      String host = ircServer.getHost();
      int mappedPort = ircServer.getMappedPort(cfg.ircPort());

      RuntimeIrcConfig runtimeCfg =
          cfg.toRuntimeConfig(host, mappedPort)
              .withServerId(cfg.serverId() + "-lifecycle")
              .withNickAndLogin("lifeapp", "lifeapp");
      String opNick = "op-bot";
      String partNick = "part-bot";

      try (SimpleIrcBot opBot = SimpleIrcBot.connect(host, mappedPort, opNick);
          SimpleIrcBot partBot = SimpleIrcBot.connect(host, mappedPort, partNick);
          ServiceFixture fixture = newService(runtimeCfg, false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try {
          opBot.join(cfg.channel(), JOIN_TIMEOUT);

          int readyCount =
              countEvents(events, runtimeCfg.serverId(), IrcEvent.ConnectionReady.class);
          service.connect(runtimeCfg.serverId()).blockingAwait();
          awaitNextEvent(
              events,
              runtimeCfg.serverId(),
              IrcEvent.ConnectionReady.class,
              readyCount,
              CONNECT_TIMEOUT);

          int joinedCount =
              countEvents(events, runtimeCfg.serverId(), IrcEvent.JoinedChannel.class);
          service.joinChannel(runtimeCfg.serverId(), cfg.channel()).blockingAwait();
          awaitNextEvent(
              events,
              runtimeCfg.serverId(),
              IrcEvent.JoinedChannel.class,
              joinedCount,
              JOIN_TIMEOUT);

          partBot.join(cfg.channel(), JOIN_TIMEOUT);
          int partCount =
              countEventsWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.UserPartedChannel.class,
                  e ->
                      cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && partNick.equalsIgnoreCase(Objects.toString(e.nick(), "").trim()));
          partBot.part(cfg.channel(), "bye");
          IrcEvent.UserPartedChannel parted =
              awaitNextEventWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.UserPartedChannel.class,
                  e ->
                      cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && partNick.equalsIgnoreCase(Objects.toString(e.nick(), "").trim()),
                  partCount,
                  MESSAGE_TIMEOUT);
          assertEquals(cfg.channel(), parted.channel());
          assertEquals(partNick, parted.nick());

          String topic = "it-topic-" + Long.toUnsignedString(System.nanoTime(), 36);
          int topicCount =
              countEventsWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.ChannelTopicUpdated.class,
                  e ->
                      cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && topic.equals(Objects.toString(e.topic(), "").trim()));
          opBot.topic(cfg.channel(), topic);
          IrcEvent.ChannelTopicUpdated topicUpdated =
              awaitNextEventWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.ChannelTopicUpdated.class,
                  e ->
                      cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && topic.equals(Objects.toString(e.topic(), "").trim()),
                  topicCount,
                  MESSAGE_TIMEOUT);
          assertEquals(topic, topicUpdated.topic());

          int modeCount =
              countEventsWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.ChannelModeObserved.class,
                  e ->
                      cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && Objects.toString(e.details(), "").contains("+m"));
          opBot.mode(cfg.channel(), "+m");
          awaitNextEventWhere(
              events,
              runtimeCfg.serverId(),
              IrcEvent.ChannelModeObserved.class,
              e ->
                  cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                      && Objects.toString(e.details(), "").contains("+m"),
              modeCount,
              MESSAGE_TIMEOUT);

          String appNick = service.currentNick(runtimeCfg.serverId()).orElse(runtimeCfg.nick());
          int kickedCount =
              countEventsWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.KickedFromChannel.class,
                  e -> cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim()));
          opBot.kick(cfg.channel(), appNick, "it-kick");
          IrcEvent.KickedFromChannel kicked =
              awaitNextEventWhere(
                  events,
                  runtimeCfg.serverId(),
                  IrcEvent.KickedFromChannel.class,
                  e -> cfg.channel().equalsIgnoreCase(Objects.toString(e.channel(), "").trim()),
                  kickedCount,
                  MESSAGE_TIMEOUT);
          assertEquals(cfg.channel(), kicked.channel());
        } finally {
          try {
            service
                .disconnect(runtimeCfg.serverId(), "container ircd lifecycle shutdown")
                .blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendKeepsEventsIsolatedAcrossTwoServerConnections() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    String sidOne = cfg.serverId() + "-isolation-one";
    String sidTwo = cfg.serverId() + "-isolation-two";
    String channelOne = "#iso-one";
    String channelTwo = "#iso-two";
    String inboundOne = "iso-one-from-bot";
    String inboundTwo = "iso-two-from-bot";
    String outboundOne = "iso-one-from-app";
    String outboundTwo = "iso-two-from-app";

    try (GenericContainer<?> ircOne = newIrcContainer(cfg);
        GenericContainer<?> ircTwo = newIrcContainer(cfg)) {
      ircOne.start();
      ircTwo.start();

      RuntimeIrcConfig cfgOne =
          cfg.toRuntimeConfig(ircOne.getHost(), ircOne.getMappedPort(cfg.ircPort()))
              .withServerId(sidOne)
              .withNickAndLogin("appa", "appa");
      RuntimeIrcConfig cfgTwo =
          cfg.toRuntimeConfig(ircTwo.getHost(), ircTwo.getMappedPort(cfg.ircPort()))
              .withServerId(sidTwo)
              .withNickAndLogin("appb", "appb");

      try (SimpleIrcBot botOne =
              SimpleIrcBot.connect(ircOne.getHost(), ircOne.getMappedPort(cfg.ircPort()), "bota");
          SimpleIrcBot botTwo =
              SimpleIrcBot.connect(ircTwo.getHost(), ircTwo.getMappedPort(cfg.ircPort()), "botb");
          ServiceFixture fixture = newService(List.of(cfgOne, cfgTwo), false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try {
          int readyOne = countEvents(events, sidOne, IrcEvent.ConnectionReady.class);
          service.connect(sidOne).blockingAwait();
          awaitNextEvent(events, sidOne, IrcEvent.ConnectionReady.class, readyOne, CONNECT_TIMEOUT);

          int readyTwo = countEvents(events, sidTwo, IrcEvent.ConnectionReady.class);
          service.connect(sidTwo).blockingAwait();
          awaitNextEvent(events, sidTwo, IrcEvent.ConnectionReady.class, readyTwo, CONNECT_TIMEOUT);

          int joinedOne = countEvents(events, sidOne, IrcEvent.JoinedChannel.class);
          service.joinChannel(sidOne, channelOne).blockingAwait();
          awaitNextEvent(events, sidOne, IrcEvent.JoinedChannel.class, joinedOne, JOIN_TIMEOUT);

          int joinedTwo = countEvents(events, sidTwo, IrcEvent.JoinedChannel.class);
          service.joinChannel(sidTwo, channelTwo).blockingAwait();
          awaitNextEvent(events, sidTwo, IrcEvent.JoinedChannel.class, joinedTwo, JOIN_TIMEOUT);

          botOne.join(channelOne, JOIN_TIMEOUT);
          botTwo.join(channelTwo, JOIN_TIMEOUT);

          int oneBefore = countChannelMessages(events, sidOne, channelOne, "bota", inboundOne);
          int twoBeforeOnOne = countChannelMessages(events, sidTwo, channelOne, "bota", inboundOne);
          botOne.privmsg(channelOne, inboundOne);
          awaitChannelMessage(
              events, sidOne, channelOne, "bota", inboundOne, oneBefore, MESSAGE_TIMEOUT);
          Thread.sleep(250L);
          int twoAfterOnOne = countChannelMessages(events, sidTwo, channelOne, "bota", inboundOne);
          assertEquals(
              twoBeforeOnOne,
              twoAfterOnOne,
              "server two should not receive channel message from server one");

          int twoBefore = countChannelMessages(events, sidTwo, channelTwo, "botb", inboundTwo);
          int oneBeforeOnTwo = countChannelMessages(events, sidOne, channelTwo, "botb", inboundTwo);
          botTwo.privmsg(channelTwo, inboundTwo);
          awaitChannelMessage(
              events, sidTwo, channelTwo, "botb", inboundTwo, twoBefore, MESSAGE_TIMEOUT);
          Thread.sleep(250L);
          int oneAfterOnTwo = countChannelMessages(events, sidOne, channelTwo, "botb", inboundTwo);
          assertEquals(
              oneBeforeOnTwo,
              oneAfterOnTwo,
              "server one should not receive channel message from server two");

          service.sendToChannel(sidOne, channelOne, outboundOne).blockingAwait();
          botOne.awaitPrivmsg(channelOne, outboundOne, MESSAGE_TIMEOUT);

          service.sendToChannel(sidTwo, channelTwo, outboundTwo).blockingAwait();
          botTwo.awaitPrivmsg(channelTwo, outboundTwo, MESSAGE_TIMEOUT);
        } finally {
          try {
            service.disconnect(sidOne, "multi-server isolation shutdown sid1").blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            service.disconnect(sidTwo, "multi-server isolation shutdown sid2").blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  @Test
  void ircBackendSupportsMessageSurfaceAndReasonedPartKickFlows() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container IRC backend E2E test disabled. Set -Dirc.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    String serverId = cfg.serverId() + "-surface";
    String msgChannel = "#surface-msg";
    String partChannel = "#surface-part";
    String opKickChannel = "#surface-opkick";
    String inboundPm = "pm-from-peer";
    String outboundPm = "pm-from-app";
    String inboundNotice = "notice-from-peer";
    String outboundNotice = "notice-from-app";
    String inboundAction = "waves-from-peer";
    String outboundAction = "waves-from-app";
    String inboundPartReason = "peer-part-reason";
    String outboundPartReason = "app-part-reason";
    String outboundKickReason = "app-kick-reason";
    String inboundKickReason = "op-kick-reason";

    try (GenericContainer<?> ircServer = newIrcContainer(cfg)) {
      ircServer.start();
      RuntimeIrcConfig runtimeCfg =
          cfg.toRuntimeConfig(ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()))
              .withServerId(serverId)
              .withNickAndLogin("surfapp", "surfapp");

      try (SimpleIrcBot peerBot =
              SimpleIrcBot.connect(
                  ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), "peerbot");
          SimpleIrcBot opBot =
              SimpleIrcBot.connect(
                  ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), "opbot");
          ServiceFixture fixture = newService(runtimeCfg, false)) {
        PircbotxIrcClientService service = fixture.service();
        TestSubscriber<ServerIrcEvent> events = service.events().test();

        try {
          int readyCount = countEvents(events, serverId, IrcEvent.ConnectionReady.class);
          service.connect(serverId).blockingAwait();
          awaitNextEvent(
              events, serverId, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
          String appNick = service.currentNick(serverId).orElseThrow();

          int msgJoinCount = countEvents(events, serverId, IrcEvent.JoinedChannel.class);
          service.joinChannel(serverId, msgChannel).blockingAwait();
          awaitNextEvent(
              events, serverId, IrcEvent.JoinedChannel.class, msgJoinCount, JOIN_TIMEOUT);

          int partJoinCount = countEvents(events, serverId, IrcEvent.JoinedChannel.class);
          service.joinChannel(serverId, partChannel).blockingAwait();
          awaitNextEvent(
              events, serverId, IrcEvent.JoinedChannel.class, partJoinCount, JOIN_TIMEOUT);

          opBot.join(opKickChannel, JOIN_TIMEOUT);
          int opKickJoinCount = countEvents(events, serverId, IrcEvent.JoinedChannel.class);
          service.joinChannel(serverId, opKickChannel).blockingAwait();
          awaitNextEvent(
              events, serverId, IrcEvent.JoinedChannel.class, opKickJoinCount, JOIN_TIMEOUT);

          peerBot.join(msgChannel, JOIN_TIMEOUT);
          opBot.join(msgChannel, JOIN_TIMEOUT);

          int pmCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.PrivateMessage.class,
                  e ->
                      "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                          && Objects.toString(e.text(), "").contains(inboundPm));
          peerBot.privmsg(appNick, inboundPm);
          IrcEvent.PrivateMessage pm =
              awaitNextEventWhere(
                  events,
                  serverId,
                  IrcEvent.PrivateMessage.class,
                  e ->
                      "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                          && Objects.toString(e.text(), "").contains(inboundPm),
                  pmCount,
                  MESSAGE_TIMEOUT);
          assertEquals("peerbot", pm.from());

          service.sendPrivateMessage(serverId, "peerbot", outboundPm).blockingAwait();
          peerBot.awaitPrivmsg("peerbot", outboundPm, MESSAGE_TIMEOUT);

          int noticeCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.Notice.class,
                  e ->
                      "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                          && Objects.toString(e.text(), "").contains(inboundNotice));
          peerBot.notice(appNick, inboundNotice);
          awaitNextEventWhere(
              events,
              serverId,
              IrcEvent.Notice.class,
              e ->
                  "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                      && Objects.toString(e.text(), "").contains(inboundNotice),
              noticeCount,
              MESSAGE_TIMEOUT);

          service.sendNoticePrivate(serverId, "peerbot", outboundNotice).blockingAwait();
          peerBot.awaitNotice("peerbot", outboundNotice, MESSAGE_TIMEOUT);

          int actionCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.ChannelAction.class,
                  e ->
                      msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                          && Objects.toString(e.action(), "").contains(inboundAction));
          peerBot.action(msgChannel, inboundAction);
          awaitNextEventWhere(
              events,
              serverId,
              IrcEvent.ChannelAction.class,
              e ->
                  msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                      && "peerbot".equalsIgnoreCase(Objects.toString(e.from(), "").trim())
                      && Objects.toString(e.action(), "").contains(inboundAction),
              actionCount,
              MESSAGE_TIMEOUT);

          service.sendAction(serverId, msgChannel, outboundAction).blockingAwait();
          peerBot.awaitAction(msgChannel, outboundAction, MESSAGE_TIMEOUT);

          int inboundPartCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.UserPartedChannel.class,
                  e ->
                      msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && "peerbot".equalsIgnoreCase(Objects.toString(e.nick(), "").trim())
                          && Objects.toString(e.reason(), "").contains(inboundPartReason));
          peerBot.part(msgChannel, inboundPartReason);
          IrcEvent.UserPartedChannel inboundPart =
              awaitNextEventWhere(
                  events,
                  serverId,
                  IrcEvent.UserPartedChannel.class,
                  e ->
                      msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && "peerbot".equalsIgnoreCase(Objects.toString(e.nick(), "").trim())
                          && Objects.toString(e.reason(), "").contains(inboundPartReason),
                  inboundPartCount,
                  MESSAGE_TIMEOUT);
          assertTrue(
              Objects.toString(inboundPart.reason(), "").contains(inboundPartReason),
              "expected inbound part reason to be preserved");
          peerBot.join(msgChannel, JOIN_TIMEOUT);

          int outboundPartCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.LeftChannel.class,
                  e ->
                      partChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && Objects.toString(e.reason(), "").contains(outboundPartReason));
          service.partChannel(serverId, partChannel, outboundPartReason).blockingAwait();
          IrcEvent.LeftChannel outboundPart =
              awaitNextEventWhere(
                  events,
                  serverId,
                  IrcEvent.LeftChannel.class,
                  e ->
                      partChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && Objects.toString(e.reason(), "").contains(outboundPartReason),
                  outboundPartCount,
                  MESSAGE_TIMEOUT);
          assertTrue(
              Objects.toString(outboundPart.reason(), "").contains(outboundPartReason),
              "expected outbound part reason to be preserved");

          int outboundKickCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.UserKickedFromChannel.class,
                  e ->
                      msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && "peerbot".equalsIgnoreCase(Objects.toString(e.nick(), "").trim())
                          && Objects.toString(e.reason(), "").contains(outboundKickReason));
          service
              .sendRaw(serverId, "KICK " + msgChannel + " peerbot :" + outboundKickReason)
              .blockingAwait();
          IrcEvent.UserKickedFromChannel outboundKick =
              awaitNextEventWhere(
                  events,
                  serverId,
                  IrcEvent.UserKickedFromChannel.class,
                  e ->
                      msgChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && "peerbot".equalsIgnoreCase(Objects.toString(e.nick(), "").trim())
                          && Objects.toString(e.reason(), "").contains(outboundKickReason),
                  outboundKickCount,
                  MESSAGE_TIMEOUT);
          assertTrue(
              appNick.equalsIgnoreCase(Objects.toString(outboundKick.by(), "").trim()),
              "expected local nick as outbound kick initiator");
          peerBot.awaitKick(msgChannel, "peerbot", outboundKickReason, MESSAGE_TIMEOUT);

          int inboundKickCount =
              countEventsWhere(
                  events,
                  serverId,
                  IrcEvent.KickedFromChannel.class,
                  e ->
                      opKickChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && Objects.toString(e.reason(), "").contains(inboundKickReason)
                          && "opbot".equalsIgnoreCase(Objects.toString(e.by(), "").trim()));
          opBot.kick(opKickChannel, appNick, inboundKickReason);
          IrcEvent.KickedFromChannel inboundKick =
              awaitNextEventWhere(
                  events,
                  serverId,
                  IrcEvent.KickedFromChannel.class,
                  e ->
                      opKickChannel.equalsIgnoreCase(Objects.toString(e.channel(), "").trim())
                          && Objects.toString(e.reason(), "").contains(inboundKickReason)
                          && "opbot".equalsIgnoreCase(Objects.toString(e.by(), "").trim()),
                  inboundKickCount,
                  MESSAGE_TIMEOUT);
          assertTrue(
              Objects.toString(inboundKick.reason(), "").contains(inboundKickReason),
              "expected inbound kick reason to be preserved");
        } finally {
          try {
            service.disconnect(serverId, "message surface shutdown").blockingAwait();
          } catch (Exception ignored) {
          }
          try {
            events.cancel();
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  private static ServiceFixture newService(RuntimeIrcConfig cfg) {
    return newService(cfg, true);
  }

  private static ServiceFixture newService(RuntimeIrcConfig cfg, boolean reconnectEnabled) {
    return newService(List.of(cfg), reconnectEnabled);
  }

  private static ServiceFixture newService(List<RuntimeIrcConfig> cfgs, boolean reconnectEnabled) {
    List<RuntimeIrcConfig> configs = cfgs == null ? List.of() : List.copyOf(cfgs);
    if (configs.isEmpty()) {
      throw new IllegalArgumentException("at least one runtime config is required");
    }

    LinkedHashMap<String, IrcProperties.Server> serversById = new LinkedHashMap<>();
    for (RuntimeIrcConfig cfg : configs) {
      if (cfg == null) continue;
      IrcProperties.Server server = cfg.toServer();
      serversById.put(cfg.serverId(), server);
    }
    if (serversById.isEmpty()) {
      throw new IllegalArgumentException("no valid runtime configs");
    }

    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.require(ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              IrcProperties.Server server = serversById.get(sid);
              if (server == null) {
                throw new IllegalArgumentException("Unknown server id: " + sid);
              }
              return server;
            });
    when(serverCatalog.find(ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              return Optional.ofNullable(serversById.get(sid));
            });
    when(serverCatalog.containsId(ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              return serversById.containsKey(sid);
            });

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe IT",
                new IrcProperties.Reconnect(reconnectEnabled, 250, 1_000, 1.5, 0, 3),
                null,
                null,
                null),
            List.copyOf(serversById.values()));

    Ircv3StsPolicyService stsPolicies = new Ircv3StsPolicyService();
    PircbotxInputParserHookInstaller hookInstaller =
        new PircbotxInputParserHookInstaller(stsPolicies);

    SojuProperties sojuProps = new SojuProperties(Map.of(), new SojuProperties.Discovery(false));
    ZncProperties zncProps = new ZncProperties(Map.of(), new ZncProperties.Discovery(false));

    ServerProxyResolver proxyResolver = new ServerProxyResolver(serverCatalog);
    PircbotxBotFactory botFactory = new PircbotxBotFactory(proxyResolver, sojuProps, null);

    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(bouncerBackends.backendIds()).thenReturn(Set.of());

    ScheduledExecutorService heartbeatExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("it-pircbotx-heartbeat"));
    ScheduledExecutorService reconnectExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("it-pircbotx-reconnect"));
    PircbotxConnectionTimersRx timers =
        new PircbotxConnectionTimersRx(props, serverCatalog, heartbeatExec, reconnectExec);
    ServerIsupportState serverIsupportState = new ServerIsupportState();
    PircbotxBridgeListenerFactory bridgeListenerFactory =
        new PircbotxBridgeListenerFactory(
            bouncerBackends,
            bouncerDiscoveryEvents,
            new NoOpPlaybackCursorProvider(),
            serverIsupportState,
            sojuProps,
            zncProps);

    PircbotxIrcClientService service =
        new PircbotxIrcClientService(
            props,
            serverCatalog,
            hookInstaller,
            botFactory,
            bridgeListenerFactory,
            (CtcpReplyRuntimeConfigPort) runtimeConfig,
            (ChatCommandRuntimeConfigPort) runtimeConfig,
            stsPolicies,
            bouncerBackends,
            bouncerDiscoveryEvents,
            timers,
            serverIsupportState);

    return new ServiceFixture(service, timers, heartbeatExec, reconnectExec);
  }

  private static GenericContainer<?> newIrcContainer(E2eConfig cfg) {
    return newIrcContainer(cfg, "");
  }

  private static GenericContainer<?> newIrcContainer(E2eConfig cfg, String requiredPassword) {
    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());
    GenericContainer<?> container =
        new GenericContainer<>(ircImage)
            .withExposedPorts(cfg.ircPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
    String password = Objects.toString(requiredPassword, "").trim();
    if (!password.isEmpty()) {
      try {
        Path conf = Files.createTempFile("ircafe-ngircd-password-", ".conf");
        Files.writeString(conf, passwordProtectedConfig(password), StandardCharsets.UTF_8);
        conf.toFile().deleteOnExit();
        container.withCopyFileToContainer(MountableFile.forHostPath(conf), "/config/ngircd.conf");
      } catch (IOException io) {
        throw new IllegalStateException("failed to prepare password-protected ngircd config", io);
      }
    }
    return container;
  }

  private static String passwordProtectedConfig(String password) {
    String pass = Objects.toString(password, "").trim();
    if (pass.isEmpty()) {
      throw new IllegalArgumentException("password is blank");
    }
    return String.join(
            "\n",
            "[Global]",
            "Name = irc.example.net",
            "Info = IRCafe test server",
            "MotdPhrase = IRCafe IT",
            "Password = " + pass,
            "PidFile = /var/run/ngircd/ngircd.pid",
            "ServerUID = abc",
            "ServerGID = abc",
            "",
            "[Limits]",
            "MaxConnections = 0",
            "PingTimeout = 120",
            "PongTimeout = 20",
            "",
            "[Options]",
            "OperCanUseMode = yes",
            "PAM = no",
            "")
        .trim()
        .concat("\n");
  }

  private static ThreadFactory namedDaemonFactory(String name) {
    String threadName = Objects.toString(name, "").trim();
    if (threadName.isEmpty()) threadName = "irc-e2e";
    final String finalThreadName = threadName;
    return runnable -> {
      Thread thread = new Thread(runnable, finalThreadName);
      thread.setDaemon(true);
      return thread;
    };
  }

  private static OptionalLong awaitLagSample(
      PircbotxIrcClientService service, String serverId, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      OptionalLong lag = service.lastMeasuredLagMs(serverId);
      if (lag.isPresent()) return lag;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return OptionalLong.empty();
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

  private static void awaitChannelMessage(
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
        return;
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
  }

  private static <T extends IrcEvent> int countEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    return matchingEvents(events, serverId, eventType).size();
  }

  private static <T extends IrcEvent> int countEventsWhere(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      Class<T> eventType,
      Predicate<T> predicate) {
    Predicate<T> filter = predicate == null ? (ignored) -> true : predicate;
    int count = 0;
    for (T event : matchingEvents(events, serverId, eventType)) {
      if (!filter.test(event)) continue;
      count++;
    }
    return count;
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

  private static <T extends IrcEvent> T awaitNextEventWhere(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      Class<T> eventType,
      Predicate<T> predicate,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    Predicate<T> filter = predicate == null ? (ignored) -> true : predicate;
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      int seen = 0;
      for (T event : matchingEvents(events, serverId, eventType)) {
        if (!filter.test(event)) continue;
        if (seen == alreadySeenCount) {
          return event;
        }
        seen++;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for "
            + eventType.getSimpleName()
            + " (filtered) on server '"
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

  private record ServiceFixture(
      PircbotxIrcClientService service,
      PircbotxConnectionTimersRx timers,
      ScheduledExecutorService heartbeatExec,
      ScheduledExecutorService reconnectExec)
      implements AutoCloseable {

    @Override
    public void close() {
      if (service != null) {
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
      if (timers != null) {
        try {
          timers.shutdown();
        } catch (Exception ignored) {
        }
      }
      shutdownExecutor(heartbeatExec);
      shutdownExecutor(reconnectExec);
    }

    private static void shutdownExecutor(ScheduledExecutorService executor) {
      if (executor == null) return;
      try {
        executor.shutdownNow();
      } catch (Exception ignored) {
      }
      try {
        executor.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
      }
    }
  }

  private record RuntimeIrcConfig(
      String serverId,
      String host,
      int port,
      String serverPassword,
      String nick,
      String login,
      String realName) {

    IrcProperties.Server toServer() {
      return new IrcProperties.Server(
          serverId,
          host,
          port,
          false,
          serverPassword,
          nick,
          login,
          realName,
          null,
          null,
          List.of(),
          List.of(),
          new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000),
          IrcProperties.Server.Backend.IRC);
    }

    RuntimeIrcConfig withServerId(String nextServerId) {
      return new RuntimeIrcConfig(nextServerId, host, port, serverPassword, nick, login, realName);
    }

    RuntimeIrcConfig withServerPassword(String nextServerPassword) {
      return new RuntimeIrcConfig(serverId, host, port, nextServerPassword, nick, login, realName);
    }

    RuntimeIrcConfig withNickAndLogin(String nextNick, String nextLogin) {
      return new RuntimeIrcConfig(
          serverId, host, port, serverPassword, nextNick, nextLogin, realName);
    }
  }

  private record E2eConfig(
      boolean enabled,
      String ircImage,
      long startupTimeoutSeconds,
      String serverId,
      String nick,
      String login,
      String realName,
      String botNick,
      String channel,
      String botMessage,
      String appMessage) {

    private static final String DEFAULT_IRC_IMAGE = "linuxserver/ngircd:latest";
    private static final long DEFAULT_STARTUP_TIMEOUT_SECONDS = 180L;
    private static final String DEFAULT_SERVER_ID = "irc-it-e2e";
    private static final String DEFAULT_NICK = "ircafe-it";
    private static final String DEFAULT_REAL_NAME = "IRCafe IT";
    private static final String DEFAULT_BOT_NICK = "e2ebot";
    private static final String DEFAULT_CHANNEL = "#irc-e2e";
    private static final String DEFAULT_BOT_MESSAGE = "hello-from-ircd-e2e-bot";
    private static final String DEFAULT_APP_MESSAGE = "hello-from-ircd-e2e-app";

    static E2eConfig fromSystem() {
      boolean enabled =
          readBoolean("irc.it.container.e2e.enabled", "IRC_IT_CONTAINER_E2E_ENABLED", false);
      String ircImage =
          readString(
              "irc.it.container.e2e.irc-image",
              "IRC_IT_CONTAINER_E2E_IRC_IMAGE",
              DEFAULT_IRC_IMAGE);
      long startupTimeoutSeconds =
          readLong(
              "irc.it.container.e2e.startup-timeout-seconds",
              "IRC_IT_CONTAINER_E2E_STARTUP_TIMEOUT_SECONDS",
              DEFAULT_STARTUP_TIMEOUT_SECONDS);
      String serverId =
          readString(
              "irc.it.container.e2e.server-id",
              "IRC_IT_CONTAINER_E2E_SERVER_ID",
              DEFAULT_SERVER_ID);
      String nick =
          readString("irc.it.container.e2e.nick", "IRC_IT_CONTAINER_E2E_NICK", DEFAULT_NICK);
      String login = readString("irc.it.container.e2e.login", "IRC_IT_CONTAINER_E2E_LOGIN", nick);
      String realName =
          readString(
              "irc.it.container.e2e.real-name",
              "IRC_IT_CONTAINER_E2E_REAL_NAME",
              DEFAULT_REAL_NAME);
      String botNick =
          readString(
              "irc.it.container.e2e.bot-nick", "IRC_IT_CONTAINER_E2E_BOT_NICK", DEFAULT_BOT_NICK);
      String channel =
          readString(
              "irc.it.container.e2e.channel", "IRC_IT_CONTAINER_E2E_CHANNEL", DEFAULT_CHANNEL);
      String botMessage =
          readString(
              "irc.it.container.e2e.bot-message",
              "IRC_IT_CONTAINER_E2E_BOT_MESSAGE",
              DEFAULT_BOT_MESSAGE);
      String appMessage =
          readString(
              "irc.it.container.e2e.app-message",
              "IRC_IT_CONTAINER_E2E_APP_MESSAGE",
              DEFAULT_APP_MESSAGE);

      return new E2eConfig(
          enabled,
          safeTrim(ircImage, DEFAULT_IRC_IMAGE),
          Math.max(30L, startupTimeoutSeconds),
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(nick, DEFAULT_NICK),
          safeTrim(login, safeTrim(nick, DEFAULT_NICK)),
          safeTrim(realName, DEFAULT_REAL_NAME),
          safeTrim(botNick, DEFAULT_BOT_NICK),
          normalizeChannel(channel),
          safeTrim(botMessage, DEFAULT_BOT_MESSAGE),
          safeTrim(appMessage, DEFAULT_APP_MESSAGE));
    }

    int ircPort() {
      return 6667;
    }

    RuntimeIrcConfig toRuntimeConfig(String host, int mappedPort) {
      return new RuntimeIrcConfig(serverId, host, mappedPort, "", nick, login, realName);
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
      socket.setSoTimeout((int) IRC_BOT_TIMEOUT.toMillis());
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

    void notice(String target, String text) throws Exception {
      String dest = Objects.toString(target, "").trim();
      String msg = Objects.toString(text, "").trim();
      if (dest.isEmpty() || msg.isEmpty()) {
        throw new IllegalArgumentException("notice target/text is blank");
      }
      sendLine("NOTICE " + dest + " :" + msg);
    }

    void action(String target, String text) throws Exception {
      String dest = Objects.toString(target, "").trim();
      String msg = Objects.toString(text, "").trim();
      if (dest.isEmpty() || msg.isEmpty()) {
        throw new IllegalArgumentException("action target/text is blank");
      }
      sendLine("PRIVMSG " + dest + " :\u0001ACTION " + msg + "\u0001");
    }

    void part(String channel, String reason) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String msg = Objects.toString(reason, "").trim();
      if (chan.isEmpty()) {
        throw new IllegalArgumentException("channel is blank");
      }
      if (msg.isEmpty()) {
        sendLine("PART " + chan);
      } else {
        sendLine("PART " + chan + " :" + msg);
      }
    }

    void topic(String channel, String topic) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String value = Objects.toString(topic, "").trim();
      if (chan.isEmpty() || value.isEmpty()) {
        throw new IllegalArgumentException("topic channel/value is blank");
      }
      sendLine("TOPIC " + chan + " :" + value);
    }

    void mode(String channel, String mode) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String modeSpec = Objects.toString(mode, "").trim();
      if (chan.isEmpty() || modeSpec.isEmpty()) {
        throw new IllegalArgumentException("mode channel/spec is blank");
      }
      sendLine("MODE " + chan + " " + modeSpec);
    }

    void kick(String channel, String nick, String reason) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String targetNick = Objects.toString(nick, "").trim();
      String msg = Objects.toString(reason, "").trim();
      if (chan.isEmpty() || targetNick.isEmpty()) {
        throw new IllegalArgumentException("kick channel/nick is blank");
      }
      if (msg.isEmpty()) {
        sendLine("KICK " + chan + " " + targetNick);
      } else {
        sendLine("KICK " + chan + " " + targetNick + " :" + msg);
      }
    }

    void awaitChannelPrivmsg(String channel, String expectedTextPart, Duration timeout)
        throws Exception {
      awaitPrivmsg(channel, expectedTextPart, timeout);
    }

    void awaitPrivmsg(String target, String expectedTextPart, Duration timeout) throws Exception {
      String dest = Objects.toString(target, "").trim();
      String expected = Objects.toString(expectedTextPart, "").trim();
      if (dest.isEmpty() || expected.isEmpty()) {
        throw new IllegalArgumentException("target/expected text is blank");
      }

      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        ParsedPrivmsg privmsg = parsePrivmsg(line);
        if (privmsg == null) continue;
        if (!dest.equalsIgnoreCase(privmsg.target().trim())) continue;
        if (!privmsg.text().contains(expected)) continue;
        return;
      }
      throw new IllegalStateException(
          "timed out waiting for PRIVMSG to " + dest + " containing: " + expected);
    }

    void awaitNotice(String target, String expectedTextPart, Duration timeout) throws Exception {
      String chan = Objects.toString(target, "").trim();
      String expected = Objects.toString(expectedTextPart, "").trim();
      if (chan.isEmpty() || expected.isEmpty()) {
        throw new IllegalArgumentException("target/expected text is blank");
      }

      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        ParsedNotice notice = parseNotice(line);
        if (notice == null) continue;
        if (!chan.equalsIgnoreCase(notice.target().trim())) continue;
        if (!notice.text().contains(expected)) continue;
        return;
      }
      throw new IllegalStateException(
          "timed out waiting for NOTICE to " + chan + " containing: " + expected);
    }

    void awaitAction(String target, String expectedActionText, Duration timeout) throws Exception {
      String dest = Objects.toString(target, "").trim();
      String expected = Objects.toString(expectedActionText, "").trim();
      if (dest.isEmpty() || expected.isEmpty()) {
        throw new IllegalArgumentException("target/expected action is blank");
      }

      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        ParsedPrivmsg privmsg = parsePrivmsg(line);
        if (privmsg == null) continue;
        if (!dest.equalsIgnoreCase(privmsg.target().trim())) continue;
        String action = parseCtcpAction(privmsg.text());
        if (action == null || action.isBlank()) continue;
        if (!action.contains(expected)) continue;
        return;
      }
      throw new IllegalStateException(
          "timed out waiting for ACTION to " + dest + " containing: " + expected);
    }

    void awaitKick(String channel, String targetNick, String reasonPart, Duration timeout)
        throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String nick = Objects.toString(targetNick, "").trim();
      String reason = Objects.toString(reasonPart, "").trim();
      if (chan.isEmpty() || nick.isEmpty() || reason.isEmpty()) {
        throw new IllegalArgumentException("kick channel/nick/reason is blank");
      }

      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        ParsedKick kick = parseKick(line);
        if (kick == null) continue;
        if (!chan.equalsIgnoreCase(kick.channel().trim())) continue;
        if (!nick.equalsIgnoreCase(kick.target().trim())) continue;
        if (!kick.reason().contains(reason)) continue;
        return;
      }
      throw new IllegalStateException(
          "timed out waiting for KICK " + chan + " " + nick + " containing: " + reason);
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

    private static ParsedPrivmsg parsePrivmsg(String rawLine) {
      ParsedIrcLine parsed = parseIrcLine(rawLine);
      if (parsed == null) return null;
      if (!"PRIVMSG".equalsIgnoreCase(parsed.command())) return null;
      if (parsed.target().isBlank()) return null;
      return new ParsedPrivmsg(parsed.fromNick(), parsed.target(), parsed.trailing());
    }

    private static ParsedNotice parseNotice(String rawLine) {
      ParsedIrcLine parsed = parseIrcLine(rawLine);
      if (parsed == null) return null;
      if (!"NOTICE".equalsIgnoreCase(parsed.command())) return null;
      if (parsed.target().isBlank()) return null;
      return new ParsedNotice(parsed.fromNick(), parsed.target(), parsed.trailing());
    }

    private static ParsedKick parseKick(String rawLine) {
      ParsedIrcLine parsed = parseIrcLine(rawLine);
      if (parsed == null) return null;
      if (!"KICK".equalsIgnoreCase(parsed.command())) return null;
      String[] parts = parsed.preTrailing().split("\\s+");
      if (parts.length < 3) return null;
      String channel = parts[1];
      String target = parts[2];
      return new ParsedKick(parsed.fromNick(), channel, target, parsed.trailing());
    }

    private static ParsedIrcLine parseIrcLine(String rawLine) {
      String line = Objects.toString(rawLine, "");
      if (line.isEmpty()) return null;

      if (line.startsWith("@")) {
        int firstSpace = line.indexOf(' ');
        if (firstSpace <= 0 || firstSpace + 1 >= line.length()) return null;
        line = line.substring(firstSpace + 1);
      }

      String prefix = "";
      if (line.startsWith(":")) {
        int firstSpace = line.indexOf(' ');
        if (firstSpace <= 1 || firstSpace + 1 >= line.length()) return null;
        prefix = line.substring(1, firstSpace);
        line = line.substring(firstSpace + 1);
      }

      line = trimAsciiSpaces(line);
      if (line.isEmpty()) return null;

      String trailing = "";
      int trailingIdx = line.indexOf(" :");
      if (trailingIdx >= 0) {
        trailing = line.substring(trailingIdx + 2);
      }
      String preTrailing = trailingIdx >= 0 ? line.substring(0, trailingIdx) : line;
      String commandSource = trimAsciiSpaces(preTrailing);
      if (commandSource.isEmpty()) return null;
      String[] parts = commandSource.split("\\s+");
      String command = parts.length > 0 ? parts[0] : "";
      String target = parts.length > 1 ? parts[1] : "";
      String from = prefix;
      int bang = from.indexOf('!');
      if (bang > 0) from = from.substring(0, bang);
      return new ParsedIrcLine(from, command, target, trailing, commandSource);
    }

    private static String trimAsciiSpaces(String value) {
      String raw = Objects.toString(value, "");
      if (raw.isEmpty()) return raw;
      int start = 0;
      int end = raw.length();
      while (start < end && raw.charAt(start) == ' ') {
        start++;
      }
      while (end > start && raw.charAt(end - 1) == ' ') {
        end--;
      }
      return raw.substring(start, end);
    }

    private static String parseCtcpAction(String text) {
      String raw = Objects.toString(text, "");
      if (raw.length() < 2) return null;
      if (raw.charAt(0) != 0x01 || raw.charAt(raw.length() - 1) != 0x01) return null;
      String inner = raw.substring(1, raw.length() - 1).trim();
      if (!inner.toUpperCase(Locale.ROOT).startsWith("ACTION ")) return null;
      return inner.substring("ACTION ".length()).trim();
    }

    private String readLine() throws IOException {
      try {
        return in.readLine();
      } catch (java.net.SocketTimeoutException timeout) {
        return null;
      }
    }

    private void sendLine(String line) throws IOException {
      String value = Objects.toString(line, "");
      if (value.isBlank()) return;
      if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
        throw new IllegalArgumentException("line contains CR/LF");
      }
      out.write(value);
      out.write("\r\n");
      out.flush();
    }

    @Override
    public void close() {
      try {
        sendLine("QUIT :bye");
      } catch (Exception ignored) {
      }
      try {
        socket.close();
      } catch (Exception ignored) {
      }
    }

    private record ParsedPrivmsg(String from, String target, String text) {}

    private record ParsedNotice(String from, String target, String text) {}

    private record ParsedKick(String by, String channel, String target, String reason) {}

    private record ParsedIrcLine(
        String fromNick, String command, String target, String trailing, String preTrailing) {}
  }

  private static void restartIrcDaemon(GenericContainer<?> container) throws Exception {
    container.execInContainer("sh", "-lc", "pkill -f '[n]gircd' || true");
  }

  private static void awaitIrcListening(String host, int port, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), (int) Math.min(1500, timeout.toMillis()));
        return;
      } catch (IOException ignored) {
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail("Timed out waiting for ircd listening on " + host + ":" + port);
  }
}
