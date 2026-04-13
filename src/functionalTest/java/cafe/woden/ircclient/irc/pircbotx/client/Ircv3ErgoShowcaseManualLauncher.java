package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.state.ServerIsupportState;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Launches the real IRCafe window against a temporary Ergo-backed IRCv3 scene so screenshots can be
 * captured manually with the full native window chrome and the system look and feel.
 */
public final class Ircv3ErgoShowcaseManualLauncher {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration APP_JOIN_TIMEOUT = Duration.ofSeconds(90);
  private static final Duration SCENE_TIMEOUT = Duration.ofSeconds(20);
  private static final long POLL_INTERVAL_MS = 50L;
  private static final long APP_SETTLE_DELAY_MS = 1200L;
  private static final long RENDER_SETTLE_DELAY_MS = 1500L;
  private static final int IRC_PORT = 6667;

  private static final String REPLY_ROOT_TEXT =
      "Testing, please reply to this message to test IRCv3 reply capability";
  private static final String REPLY_TEXT = "Reply rendering looks correct here.";
  private static final String REACT_ROOT_TEXT = "Reaction chips should appear under this line.";
  private static final String REDACT_ROOT_TEXT =
      "This message should be redacted from the transcript.";
  private static final String TYPING_CONTEXT_TEXT =
      "Typing indicators should appear above the input area.";

  private Ircv3ErgoShowcaseManualLauncher() {}

  public static void main(String[] args) throws Exception {
    ManualLaunchConfig cfg = ManualLaunchConfig.from(args);
    run(cfg);
  }

  private static void run(ManualLaunchConfig cfg) throws Exception {
    configureHelperClientLogging();
    ensureDockerAvailable();

    Path runDir = createRunDirectory(cfg.baseDir());
    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());

    try (GenericContainer<?> ircServer =
        new GenericContainer<>(ircImage)
            .withExposedPorts(IRC_PORT)
            .withEnv("TZ", "UTC")
            .withEnv("ERGO__HISTORY__RETENTION__ALLOW_INDIVIDUAL_DELETE", "true")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(STARTUP_TIMEOUT)) {
      ircServer.start();

      RuntimeIrcConfig authorCfg =
          new RuntimeIrcConfig(
              "ircv3-author",
              ircServer.getHost(),
              ircServer.getMappedPort(IRC_PORT),
              "",
              cfg.authorNick(),
              cfg.authorNick(),
              "IRCv3 Author");
      RuntimeIrcConfig peerCfg =
          new RuntimeIrcConfig(
              "ircv3-peer",
              ircServer.getHost(),
              ircServer.getMappedPort(IRC_PORT),
              "",
              cfg.peerNick(),
              cfg.peerNick(),
              "IRCv3 Peer");
      RuntimeIrcConfig appCfg =
          new RuntimeIrcConfig(
              "ircv3-showcase",
              ircServer.getHost(),
              ircServer.getMappedPort(IRC_PORT),
              "",
              cfg.appNick(),
              cfg.appNick(),
              "IRCafe Showcase");

      Path runtimeConfigFile = writeRuntimeConfig(runDir, cfg, appCfg);

      try (ServiceFixture author = newService(authorCfg);
          ServiceFixture peer = newService(peerCfg)) {
        CompositeDisposable helperEventLogs = new CompositeDisposable();
        TestSubscriber<ServerIrcEvent> authorEvents = author.service().events().test();
        TestSubscriber<ServerIrcEvent> peerEvents = peer.service().events().test();
        attachHelperEventLogging("author", author.service(), helperEventLogs);
        attachHelperEventLogging("peer", peer.service(), helperEventLogs);

        try {
          connectAndJoin(author.service(), authorCfg.serverId(), cfg.channel(), authorEvents);
          connectAndJoin(peer.service(), peerCfg.serverId(), cfg.channel(), peerEvents);

          int appJoinCount =
              countUserJoinedEvents(
                  authorEvents, authorCfg.serverId(), cfg.channel(), cfg.appNick());
          Process appProcess = launchClientProcess(runtimeConfigFile);
          try {
            System.out.println("Launching IRCafe using temporary config: " + runtimeConfigFile);
            System.out.println(
                "Waiting for "
                    + cfg.appNick()
                    + " to join "
                    + cfg.channel()
                    + " before staging "
                    + cfg.scene().token()
                    + "...");

            awaitUserJoinedChannel(
                authorEvents,
                authorCfg.serverId(),
                cfg.channel(),
                cfg.appNick(),
                appJoinCount,
                APP_JOIN_TIMEOUT,
                appProcess);

            Thread.sleep(APP_SETTLE_DELAY_MS);
            stageScene(
                cfg,
                author.service(),
                authorCfg.serverId(),
                peer.service(),
                peerCfg.serverId(),
                peerEvents);
            Thread.sleep(RENDER_SETTLE_DELAY_MS);

            System.out.println();
            System.out.println(
                "Scene ready: "
                    + cfg.scene().token()
                    + ". Capture the screenshot from the full IRCafe window now.");
            if (cfg.scene() == Scene.TYPING) {
              System.out.println(
                  "Typing indicators are ephemeral. Capture the screenshot promptly.");
            }
            System.out.println("Close the IRCafe window when done.");

            int exitCode = appProcess.waitFor();
            if (exitCode != 0) {
              throw new IllegalStateException("IRCafe exited with code " + exitCode);
            }
          } finally {
            stopProcess(appProcess);
          }
        } finally {
          helperEventLogs.dispose();
          authorEvents.cancel();
          peerEvents.cancel();
        }
      }
    }
  }

  private static void stageScene(
      ManualLaunchConfig cfg,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> peerEvents)
      throws Exception {
    switch (cfg.scene()) {
      case REPLY ->
          stageReply(cfg, authorService, authorServerId, peerService, peerServerId, peerEvents);
      case REACT ->
          stageReaction(cfg, authorService, authorServerId, peerService, peerServerId, peerEvents);
      case REDACT -> stageRedaction(cfg, authorService, authorServerId, peerServerId, peerEvents);
      case TYPING -> stageTyping(cfg, authorService, authorServerId, peerService, peerServerId);
    }
  }

  private static void stageReply(
      ManualLaunchConfig cfg,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> peerEvents)
      throws Exception {
    int rootCount =
        countChannelMessages(
            peerEvents, peerServerId, cfg.channel(), cfg.authorNick(), REPLY_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REPLY_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            peerEvents,
            peerServerId,
            cfg.channel(),
            cfg.authorNick(),
            REPLY_ROOT_TEXT,
            rootCount,
            SCENE_TIMEOUT);

    peerService
        .sendRaw(
            peerServerId,
            "@+draft/reply="
                + rootMessage.messageId()
                + " PRIVMSG "
                + cfg.channel()
                + " :"
                + REPLY_TEXT)
        .blockingAwait();
  }

  private static void stageReaction(
      ManualLaunchConfig cfg,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> peerEvents)
      throws Exception {
    int rootCount =
        countChannelMessages(
            peerEvents, peerServerId, cfg.channel(), cfg.authorNick(), REACT_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REACT_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            peerEvents,
            peerServerId,
            cfg.channel(),
            cfg.authorNick(),
            REACT_ROOT_TEXT,
            rootCount,
            SCENE_TIMEOUT);

    peerService
        .sendRaw(
            peerServerId,
            "@+draft/react=:+1:;+draft/reply="
                + rootMessage.messageId()
                + " TAGMSG "
                + cfg.channel())
        .blockingAwait();
    authorService
        .sendRaw(
            authorServerId,
            "@+draft/react=:eyes:;+draft/reply="
                + rootMessage.messageId()
                + " TAGMSG "
                + cfg.channel())
        .blockingAwait();
  }

  private static void stageRedaction(
      ManualLaunchConfig cfg,
      PircbotxIrcClientService authorService,
      String authorServerId,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> peerEvents)
      throws Exception {
    int rootCount =
        countChannelMessages(
            peerEvents, peerServerId, cfg.channel(), cfg.authorNick(), REDACT_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REDACT_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            peerEvents,
            peerServerId,
            cfg.channel(),
            cfg.authorNick(),
            REDACT_ROOT_TEXT,
            rootCount,
            SCENE_TIMEOUT);

    authorService
        .sendRaw(
            authorServerId,
            "@+draft/delete=" + rootMessage.messageId() + " TAGMSG " + cfg.channel())
        .blockingAwait();
  }

  private static void stageTyping(
      ManualLaunchConfig cfg,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId)
      throws Exception {
    authorService.sendToChannel(authorServerId, cfg.channel(), TYPING_CONTEXT_TEXT).blockingAwait();
    Thread.sleep(400L);
    peerService.sendRaw(peerServerId, "@+typing=active TAGMSG " + cfg.channel()).blockingAwait();
  }

  private static Path writeRuntimeConfig(
      Path runDir, ManualLaunchConfig cfg, RuntimeIrcConfig appCfg) throws IOException {
    LinkedHashMap<String, Object> doc = new LinkedHashMap<>();

    LinkedHashMap<String, Object> irc = new LinkedHashMap<>();
    LinkedHashMap<String, Object> client = new LinkedHashMap<>();
    client.put("version", "IRCafe Showcase");
    irc.put("client", client);
    irc.put("servers", List.of(toServerMap(appCfg, cfg.channel())));
    doc.put("irc", irc);

    LinkedHashMap<String, Object> ircafe = new LinkedHashMap<>();
    LinkedHashMap<String, Object> ui = new LinkedHashMap<>();
    ui.put("theme", cfg.themeId());
    ui.put("chatFontSize", cfg.chatFontSize());
    ui.put("autoConnectOnStart", true);
    ui.put("lastSelectedTarget", Map.of("serverId", appCfg.serverId(), "target", cfg.channel()));
    ui.put("layout", Map.of("preserveDockLayout", false));
    ui.put("tray", Map.of("enabled", false));
    ircafe.put("ui", ui);
    doc.put("ircafe", ircafe);

    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    options.setIndent(2);
    options.setIndicatorIndent(1);
    options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

    Path runtimeConfigFile = runDir.resolve("ircafe-showcase.yml");
    Files.writeString(runtimeConfigFile, new Yaml(options).dump(doc));
    return runtimeConfigFile;
  }

  private static Map<String, Object> toServerMap(RuntimeIrcConfig appCfg, String channel) {
    LinkedHashMap<String, Object> server = new LinkedHashMap<>();
    server.put("id", appCfg.serverId());
    server.put("host", appCfg.host());
    server.put("port", appCfg.port());
    server.put("tls", false);
    server.put("serverPassword", appCfg.serverPassword());
    server.put("nick", appCfg.nick());
    server.put("login", appCfg.login());
    server.put("realName", appCfg.realName());
    server.put("autoJoin", List.of(channel));
    return server;
  }

  private static Process launchClientProcess(Path runtimeConfigFile) throws IOException {
    String javaBin =
        Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
            .toString();
    List<String> command =
        List.of(
            javaBin,
            "-Djava.awt.headless=false",
            "-Dircafe.runtime-config=" + runtimeConfigFile.toAbsolutePath(),
            "-cp",
            System.getProperty("java.class.path"),
            "cafe.woden.ircclient.IrcSwingApp");

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.inheritIO();
    return processBuilder.start();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static void stopProcess(Process process) {
    if (process == null || !process.isAlive()) return;
    process.destroy();
    try {
      if (process.waitFor(5, TimeUnit.SECONDS)) return;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    process.destroyForcibly();
    try {
      process.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private static void ensureDockerAvailable() {
    if (DockerClientFactory.instance().isDockerAvailable()) return;
    throw new IllegalStateException("Docker is required for ircv3Showcase.");
  }

  private static Path createRunDirectory(Path baseDir) throws IOException {
    Files.createDirectories(baseDir);
    return Files.createTempDirectory(baseDir, "run-");
  }

  private static void connectAndJoin(
      PircbotxIrcClientService service,
      String serverId,
      String channel,
      TestSubscriber<ServerIrcEvent> events)
      throws Exception {
    int readyCount = countEvents(events, serverId, IrcEvent.ConnectionReady.class);
    service.connect(serverId).blockingAwait();
    awaitNextEvent(events, serverId, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);

    int joinedCount = countEvents(events, serverId, IrcEvent.JoinedChannel.class);
    service.joinChannel(serverId, channel).blockingAwait();
    awaitNextEvent(events, serverId, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
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
      List<IrcEvent.ChannelMessage> matches =
          matchingEvents(events, serverId, IrcEvent.ChannelMessage.class);
      int index = 0;
      for (IrcEvent.ChannelMessage event : matches) {
        if (!channel.equalsIgnoreCase(Objects.toString(event.channel(), "").trim())) continue;
        if (!fromNick.equalsIgnoreCase(Objects.toString(event.from(), "").trim())) continue;
        if (!Objects.toString(event.text(), "").contains(expectedTextPart)) continue;
        if (index++ >= alreadySeenCount) {
          return event;
        }
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError(
        "Timed out waiting for ChannelMessage from "
            + fromNick
            + " in "
            + channel
            + " containing "
            + expectedTextPart);
  }

  private static int countUserJoinedEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, String channel, String nick) {
    int count = 0;
    for (IrcEvent.UserJoinedChannel event :
        matchingEvents(events, serverId, IrcEvent.UserJoinedChannel.class)) {
      if (!channel.equalsIgnoreCase(Objects.toString(event.channel(), "").trim())) continue;
      if (!nick.equalsIgnoreCase(Objects.toString(event.nick(), "").trim())) continue;
      count++;
    }
    return count;
  }

  private static IrcEvent.UserJoinedChannel awaitUserJoinedChannel(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String channel,
      String nick,
      int alreadySeenCount,
      Duration timeout,
      Process appProcess)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (appProcess != null && !appProcess.isAlive()) {
        throw new IllegalStateException("IRCafe exited before the showcase scene was staged.");
      }
      List<IrcEvent.UserJoinedChannel> matches =
          matchingEvents(events, serverId, IrcEvent.UserJoinedChannel.class);
      int index = 0;
      for (IrcEvent.UserJoinedChannel event : matches) {
        if (!channel.equalsIgnoreCase(Objects.toString(event.channel(), "").trim())) continue;
        if (!nick.equalsIgnoreCase(Objects.toString(event.nick(), "").trim())) continue;
        if (index++ >= alreadySeenCount) {
          return event;
        }
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError(
        "Timed out waiting for " + nick + " to join " + channel + " on " + serverId);
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
    throw new AssertionError(
        "Timed out waiting for "
            + eventType.getSimpleName()
            + " on "
            + serverId
            + " (seen="
            + matchingEvents(events, serverId, eventType).size()
            + ")");
  }

  private static <T extends IrcEvent> List<T> matchingEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    ArrayList<T> matches = new ArrayList<>();
    for (ServerIrcEvent event : new ArrayList<>(events.values())) {
      if (event == null || !Objects.equals(serverId, event.serverId())) continue;
      if (!eventType.isInstance(event.event())) continue;
      matches.add(eventType.cast(event.event()));
    }
    return matches;
  }

  private static ServiceFixture newService(RuntimeIrcConfig cfg) {
    LinkedHashMap<String, IrcProperties.Server> serversById = new LinkedHashMap<>();
    serversById.put(cfg.serverId(), cfg.toServer());

    ServerCatalog serverCatalog = org.mockito.Mockito.mock(ServerCatalog.class);
    org.mockito.Mockito.when(serverCatalog.require(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              IrcProperties.Server server = serversById.get(sid);
              if (server == null) {
                throw new IllegalArgumentException("Unknown server id: " + sid);
              }
              return server;
            });
    org.mockito.Mockito.when(serverCatalog.find(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    serversById.get(Objects.toString(invocation.getArgument(0), "").trim())));
    org.mockito.Mockito.when(serverCatalog.containsId(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation ->
                serversById.containsKey(Objects.toString(invocation.getArgument(0), "").trim()));

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe Showcase",
                new IrcProperties.Reconnect(false, 250, 1_000, 1.5, 0, 3),
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

    var bouncerBackends =
        org.mockito.Mockito.mock(cafe.woden.ircclient.bouncer.BouncerBackendRegistry.class);
    var bouncerDiscoveryEvents =
        org.mockito.Mockito.mock(cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort.class);
    RuntimeConfigStore runtimeConfig = org.mockito.Mockito.mock(RuntimeConfigStore.class);
    org.mockito.Mockito.when(bouncerBackends.backendIds()).thenReturn(Set.of());

    ScheduledExecutorService heartbeatExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("ircv3-showcase-heartbeat"));
    ScheduledExecutorService reconnectExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("ircv3-showcase-reconnect"));
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

  private static void configureHelperClientLogging() {
    setLoggerLevel("org.pircbotx", "WARN");
    setLoggerLevel("org.pircbotx.InputParser", "WARN");
    setLoggerLevel("org.pircbotx.output.OutputRaw", "WARN");
  }

  private static void setLoggerLevel(String loggerName, String levelName) {
    try {
      org.slf4j.Logger logger = LoggerFactory.getLogger(Objects.toString(loggerName, "").trim());
      if (!(logger instanceof ch.qos.logback.classic.Logger classicLogger)) {
        return;
      }
      ch.qos.logback.classic.Level level =
          ch.qos.logback.classic.Level.toLevel(Objects.toString(levelName, "").trim(), null);
      if (level != null) {
        classicLogger.setLevel(level);
      }
    } catch (Exception ignored) {
    }
  }

  private static void attachHelperEventLogging(
      String label, PircbotxIrcClientService service, CompositeDisposable subscriptions) {
    if (service == null || subscriptions == null) return;
    String helperLabel = normalizeHelperLabel(label);
    subscriptions.add(
        service
            .events()
            .subscribe(
                event -> {
                  String rendered = renderHelperEventLog(helperLabel, event);
                  if (!rendered.isBlank()) {
                    System.out.println(rendered);
                  }
                },
                err ->
                    System.out.println(
                        "[" + helperLabel + "] event-stream-error: " + String.valueOf(err))));
  }

  private static String renderHelperEventLog(String label, ServerIrcEvent serverEvent) {
    if (serverEvent == null || serverEvent.event() == null) return "";
    String prefix = "[" + normalizeHelperLabel(label) + "] " + helperEventTime(serverEvent.event());
    return switch (serverEvent.event()) {
      case IrcEvent.MessageReplyObserved ev ->
          prefix
              + " reply-observed from="
              + safeToken(ev.from())
              + " target="
              + safeToken(ev.target())
              + " replyTo="
              + safeToken(ev.replyToMsgId());
      case IrcEvent.MessageReactObserved ev ->
          prefix
              + " react-observed from="
              + safeToken(ev.from())
              + " target="
              + safeToken(ev.target())
              + " messageId="
              + safeToken(ev.messageId())
              + " reaction="
              + safeToken(ev.reaction());
      case IrcEvent.MessageUnreactObserved ev ->
          prefix
              + " unreact-observed from="
              + safeToken(ev.from())
              + " target="
              + safeToken(ev.target())
              + " messageId="
              + safeToken(ev.messageId())
              + " reaction="
              + safeToken(ev.reaction());
      case IrcEvent.MessageRedactionObserved ev ->
          prefix
              + " redaction-observed from="
              + safeToken(ev.from())
              + " target="
              + safeToken(ev.target())
              + " messageId="
              + safeToken(ev.messageId());
      case IrcEvent.UserTypingObserved ev ->
          prefix
              + " typing-observed from="
              + safeToken(ev.from())
              + " target="
              + safeToken(ev.target())
              + " state="
              + safeToken(ev.state());
      default -> "";
    };
  }

  private static String helperEventTime(IrcEvent event) {
    if (event == null) return "";
    Instant at =
        switch (event) {
          case IrcEvent.MessageReplyObserved ev -> ev.at();
          case IrcEvent.MessageReactObserved ev -> ev.at();
          case IrcEvent.MessageUnreactObserved ev -> ev.at();
          case IrcEvent.MessageRedactionObserved ev -> ev.at();
          case IrcEvent.UserTypingObserved ev -> ev.at();
          default -> null;
        };
    if (at == null) return "";
    return at.toString();
  }

  private static String normalizeHelperLabel(String label) {
    String normalized = Objects.toString(label, "").trim();
    return normalized.isEmpty() ? "helper" : normalized;
  }

  private static String safeToken(String value) {
    return Objects.toString(value, "").trim();
  }

  private static ThreadFactory namedDaemonFactory(String name) {
    String threadName = Objects.toString(name, "").trim();
    if (threadName.isEmpty()) threadName = "ircv3-showcase";
    String finalThreadName = threadName;
    return runnable -> {
      Thread thread = new Thread(runnable, finalThreadName);
      thread.setDaemon(true);
      return thread;
    };
  }

  private enum Scene {
    REPLY("reply"),
    REACT("react"),
    REDACT("redact"),
    TYPING("typing");

    private final String token;

    Scene(String token) {
      this.token = token;
    }

    String token() {
      return token;
    }

    static Scene parse(String raw) {
      String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
      if (value.isEmpty()) return REPLY;
      for (Scene scene : values()) {
        if (scene.token.equals(value)) {
          return scene;
        }
      }
      throw new IllegalArgumentException(
          "Unknown IRCv3 showcase scene '" + raw + "'. Expected one of " + allowedTokens() + ".");
    }

    private static String allowedTokens() {
      LinkedHashSet<String> tokens = new LinkedHashSet<>();
      for (Scene scene : values()) {
        tokens.add(scene.token);
      }
      return String.join(", ", tokens);
    }
  }

  private record ManualLaunchConfig(
      Scene scene,
      String themeId,
      int chatFontSize,
      String ircImage,
      String channel,
      String appNick,
      String authorNick,
      String peerNick,
      Path baseDir) {

    private static final String DEFAULT_IRC_IMAGE = "ghcr.io/ergochat/ergo:stable";
    private static final String DEFAULT_CHANNEL = "#ircv3-showcase";
    private static final String DEFAULT_THEME = "blue-dark";
    private static final int DEFAULT_CHAT_FONT_SIZE = 16;
    private static final String DEFAULT_APP_NICK = "ircafe-ui";
    private static final String DEFAULT_AUTHOR_NICK = "alicebot";
    private static final String DEFAULT_PEER_NICK = "bobbot";
    private static final String DEFAULT_BASE_DIR = "build/ircv3-showcase/manual";

    static ManualLaunchConfig from(String[] args) {
      String argScene = args != null && args.length > 0 ? Objects.toString(args[0], "").trim() : "";
      return new ManualLaunchConfig(
          Scene.parse(readString("ircv3.showcase.scene", argScene)),
          readString("ircv3.showcase.theme", DEFAULT_THEME),
          readInt("ircv3.showcase.chat-font-size", DEFAULT_CHAT_FONT_SIZE),
          readString("ircv3.showcase.irc-image", DEFAULT_IRC_IMAGE),
          readString("ircv3.showcase.channel", DEFAULT_CHANNEL),
          readString("ircv3.showcase.app-nick", DEFAULT_APP_NICK),
          readString("ircv3.showcase.author-nick", DEFAULT_AUTHOR_NICK),
          readString("ircv3.showcase.peer-nick", DEFAULT_PEER_NICK),
          Path.of(readString("ircv3.showcase.base-dir", DEFAULT_BASE_DIR)));
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

  private static String readString(String propertyName, String defaultValue) {
    String raw = System.getProperty(propertyName);
    if (raw == null || raw.isBlank()) {
      raw =
          System.getenv(propertyName.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
    }
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return raw.trim();
  }

  private static int readInt(String propertyName, int defaultValue) {
    String raw = readString(propertyName, "");
    if (raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }
}
