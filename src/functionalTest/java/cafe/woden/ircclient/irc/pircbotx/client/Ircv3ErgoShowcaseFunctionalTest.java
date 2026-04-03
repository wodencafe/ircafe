package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.testutil.FunctionalTestWiringSupport;
import cafe.woden.ircclient.testutil.SwingComponentSnapshotSupport;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.backend.BackendUiProfile;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.fold.MessageReactionsComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Container-backed screenshot harness for IRCv3 reply/react/redaction/typing UI against a real IRCd
 * that advertises the required capabilities.
 */
class Ircv3ErgoShowcaseFunctionalTest {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration UI_TIMEOUT = Duration.ofSeconds(20);
  private static final long POLL_INTERVAL_MS = 50L;
  private static final int IRC_PORT = 6667;
  private static final int CAPTURE_WIDTH = 1180;
  private static final int CAPTURE_HEIGHT = 760;

  private static final String REPLY_ROOT_TEXT =
      "Testing, please reply to this message to test IRCv3 reply capability";
  private static final String REPLY_TEXT = "Reply rendering looks correct here.";
  private static final String REACT_ROOT_TEXT = "Reaction chips should appear under this line.";
  private static final String REDACT_ROOT_TEXT =
      "This message should be redacted from the transcript.";
  private static final String TYPING_CONTEXT_TEXT =
      "Typing indicators should appear above the input area.";

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void exportsReplyReactionRedactionAndTypingScreenshotsAgainstErgo() throws Exception {
    ShowcaseConfig cfg = ShowcaseConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "IRCv3 screenshot functional test disabled. Set -Dircv3.it.container.functional.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());
    try (GenericContainer<?> ircServer =
        new GenericContainer<>(ircImage)
            .withExposedPorts(IRC_PORT)
            .withEnv("TZ", "UTC")
            .withEnv("ERGO__HISTORY__RETENTION__ALLOW_INDIVIDUAL_DELETE", "true")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(STARTUP_TIMEOUT)) {
      ircServer.start();

      RuntimeIrcConfig appCfg =
          cfg.appRuntimeConfig(ircServer.getHost(), ircServer.getMappedPort(IRC_PORT));
      RuntimeIrcConfig authorCfg =
          cfg.authorRuntimeConfig(ircServer.getHost(), ircServer.getMappedPort(IRC_PORT));
      RuntimeIrcConfig peerCfg =
          cfg.peerRuntimeConfig(ircServer.getHost(), ircServer.getMappedPort(IRC_PORT));

      try (ServiceFixture runtimeApp = newService(appCfg);
          ServiceFixture runtimeAuthor = newService(authorCfg);
          ServiceFixture runtimePeer = newService(peerCfg);
          ChatFixture chat = newChatFixture(runtimeApp.service());
          UiEventRelay relay =
              new UiEventRelay(runtimeApp.service(), chat.transcripts(), chat.chat())) {
        TestSubscriber<ServerIrcEvent> appEvents = runtimeApp.service().events().test();
        TestSubscriber<ServerIrcEvent> authorEvents = runtimeAuthor.service().events().test();
        TestSubscriber<ServerIrcEvent> peerEvents = runtimePeer.service().events().test();

        try {
          connectAndJoin(runtimeApp.service(), appCfg.serverId(), cfg.channel(), appEvents);
          connectAndJoin(
              runtimeAuthor.service(), authorCfg.serverId(), cfg.channel(), authorEvents);
          connectAndJoin(runtimePeer.service(), peerCfg.serverId(), cfg.channel(), peerEvents);

          TargetRef channelTarget = new TargetRef(appCfg.serverId(), cfg.channel());
          onEdt(
              () -> {
                chat.chat().setSize(CAPTURE_WIDTH, CAPTURE_HEIGHT);
                chat.chat().setActiveTarget(channelTarget);
                chat.chat().setInputEnabled(true);
              });
          flushEdt();

          captureReplyScene(
              cfg.outputDir(),
              channelTarget,
              chat,
              runtimeAuthor.service(),
              authorCfg.serverId(),
              runtimePeer.service(),
              peerCfg.serverId(),
              appEvents,
              appCfg.serverId(),
              cfg);
          captureReactionScene(
              cfg.outputDir(),
              channelTarget,
              chat,
              runtimeAuthor.service(),
              authorCfg.serverId(),
              runtimePeer.service(),
              peerCfg.serverId(),
              appEvents,
              appCfg.serverId(),
              cfg);
          captureRedactionScene(
              cfg.outputDir(),
              channelTarget,
              chat,
              runtimeAuthor.service(),
              authorCfg.serverId(),
              appEvents,
              cfg);
          captureTypingScene(
              cfg.outputDir(),
              channelTarget,
              chat,
              runtimeAuthor.service(),
              authorCfg.serverId(),
              runtimePeer.service(),
              peerCfg.serverId(),
              appEvents,
              appCfg.serverId(),
              cfg);
        } finally {
          appEvents.cancel();
          authorEvents.cancel();
          peerEvents.cancel();
        }
      }
    }
  }

  private static void captureReplyScene(
      Path outputDir,
      TargetRef channelTarget,
      ChatFixture chat,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> appEvents,
      String appServerId,
      ShowcaseConfig cfg)
      throws Exception {
    resetScene(chat, channelTarget);

    int rootCount =
        countChannelMessages(
            appEvents, appServerId, cfg.channel(), cfg.authorNick(), REPLY_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REPLY_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            appEvents,
            appServerId,
            cfg.channel(),
            cfg.authorNick(),
            REPLY_ROOT_TEXT,
            rootCount,
            UI_TIMEOUT);
    assertFalse(
        Objects.toString(rootMessage.messageId(), "").isBlank(),
        "root reply message id should not be blank");

    int replyCount =
        countChannelMessages(appEvents, appServerId, cfg.channel(), cfg.peerNick(), REPLY_TEXT);
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
    awaitChannelMessage(
        appEvents, appServerId, cfg.channel(), cfg.peerNick(), REPLY_TEXT, replyCount, UI_TIMEOUT);

    waitForTranscriptSnippet(
        chat.transcripts(),
        channelTarget,
        "replied to " + rootMessage.messageId(),
        UI_TIMEOUT,
        "reply context line did not render");
    waitForTranscriptSnippet(
        chat.transcripts(), channelTarget, REPLY_TEXT, UI_TIMEOUT, "reply line did not render");
    captureScene(chat.chat(), outputDir.resolve("reply.png"));
  }

  private static void captureReactionScene(
      Path outputDir,
      TargetRef channelTarget,
      ChatFixture chat,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> appEvents,
      String appServerId,
      ShowcaseConfig cfg)
      throws Exception {
    resetScene(chat, channelTarget);

    int rootCount =
        countChannelMessages(
            appEvents, appServerId, cfg.channel(), cfg.authorNick(), REACT_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REACT_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            appEvents,
            appServerId,
            cfg.channel(),
            cfg.authorNick(),
            REACT_ROOT_TEXT,
            rootCount,
            UI_TIMEOUT);
    assertFalse(
        Objects.toString(rootMessage.messageId(), "").isBlank(),
        "reaction root message id should not be blank");

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

    waitForReactionTokens(
        chat.chat(), List.of(":+1:", ":eyes:"), UI_TIMEOUT, "reaction chips did not render");
    captureScene(chat.chat(), outputDir.resolve("react.png"));
  }

  private static void captureRedactionScene(
      Path outputDir,
      TargetRef channelTarget,
      ChatFixture chat,
      PircbotxIrcClientService authorService,
      String authorServerId,
      TestSubscriber<ServerIrcEvent> appEvents,
      ShowcaseConfig cfg)
      throws Exception {
    resetScene(chat, channelTarget);

    int rootCount =
        countChannelMessages(
            appEvents, authorServerId, cfg.channel(), cfg.authorNick(), REDACT_ROOT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), REDACT_ROOT_TEXT).blockingAwait();
    IrcEvent.ChannelMessage rootMessage =
        awaitChannelMessage(
            appEvents,
            authorServerId,
            cfg.channel(),
            cfg.authorNick(),
            REDACT_ROOT_TEXT,
            rootCount,
            UI_TIMEOUT);
    assertFalse(
        Objects.toString(rootMessage.messageId(), "").isBlank(),
        "redaction root message id should not be blank");

    authorService
        .sendRaw(
            authorServerId,
            "@+draft/delete=" + rootMessage.messageId() + " TAGMSG " + cfg.channel())
        .blockingAwait();

    waitForTranscriptSnippet(
        chat.transcripts(),
        channelTarget,
        "[message redacted]",
        UI_TIMEOUT,
        "redacted placeholder did not render");
    captureScene(chat.chat(), outputDir.resolve("redact.png"));
  }

  private static void captureTypingScene(
      Path outputDir,
      TargetRef channelTarget,
      ChatFixture chat,
      PircbotxIrcClientService authorService,
      String authorServerId,
      PircbotxIrcClientService peerService,
      String peerServerId,
      TestSubscriber<ServerIrcEvent> appEvents,
      String appServerId,
      ShowcaseConfig cfg)
      throws Exception {
    resetScene(chat, channelTarget);

    int contextCount =
        countChannelMessages(
            appEvents, appServerId, cfg.channel(), cfg.authorNick(), TYPING_CONTEXT_TEXT);
    authorService.sendToChannel(authorServerId, cfg.channel(), TYPING_CONTEXT_TEXT).blockingAwait();
    awaitChannelMessage(
        appEvents,
        appServerId,
        cfg.channel(),
        cfg.authorNick(),
        TYPING_CONTEXT_TEXT,
        contextCount,
        UI_TIMEOUT);

    peerService.sendRaw(peerServerId, "@+typing=active TAGMSG " + cfg.channel()).blockingAwait();

    waitForVisibleLabel(
        chat.chat(), cfg.peerNick() + " is typing", UI_TIMEOUT, "typing banner did not render");
    captureScene(chat.chat(), outputDir.resolve("typing.png"));
  }

  private static void captureScene(ChatDockable chat, Path outputFile) throws Exception {
    flushEdt();
    var image = onEdtCall(() -> SwingComponentSnapshotSupport.capture(chat));
    SwingComponentSnapshotSupport.writePng(outputFile, image);
  }

  private static void resetScene(ChatFixture chat, TargetRef channelTarget) throws Exception {
    onEdt(
        () -> {
          chat.transcripts().clearTarget(channelTarget);
          chat.inputPanel().clearReplyCompose();
          chat.inputPanel().setDraftText("");
          chat.inputPanel().clearRemoteTypingIndicator();
          chat.chat().setActiveTarget(channelTarget);
          chat.chat().setInputEnabled(true);
        });
    flushEdt();
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

  private static void waitForTranscriptSnippet(
      ChatTranscriptStore transcripts,
      TargetRef target,
      String snippet,
      Duration timeout,
      String timeoutMessage)
      throws Exception {
    awaitCondition(
        () -> transcriptText(transcripts, target).contains(snippet), timeout, timeoutMessage);
  }

  private static String transcriptText(ChatTranscriptStore transcripts, TargetRef target) {
    try {
      StyledDocument doc = transcripts.document(target);
      return doc.getText(0, doc.getLength());
    } catch (Exception ex) {
      return "";
    }
  }

  private static void waitForReactionTokens(
      ChatDockable chat, List<String> expectedTokens, Duration timeout, String timeoutMessage)
      throws Exception {
    awaitCondition(
        () -> visibleReactionTokens(chat).containsAll(expectedTokens), timeout, timeoutMessage);
  }

  private static Set<String> visibleReactionTokens(ChatDockable chat) {
    MessageReactionsComponent component = findFirst(chat, MessageReactionsComponent.class);
    if (component == null) {
      return Set.of();
    }
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    for (Component child : component.getComponents()) {
      if (child instanceof JLabel label && label.isVisible()) {
        tokens.add(Objects.toString(label.getText(), "").trim());
      }
    }
    return Set.copyOf(tokens);
  }

  private static void waitForVisibleLabel(
      ChatDockable chat, String snippet, Duration timeout, String timeoutMessage) throws Exception {
    awaitCondition(
        () -> findVisibleLabelContaining(chat, snippet) != null, timeout, timeoutMessage);
  }

  private static JLabel findVisibleLabelContaining(Component root, String snippet) {
    if (root == null || snippet == null) return null;
    String needle = snippet.trim();
    if (needle.isEmpty()) return null;
    if (root instanceof JLabel label
        && label.isVisible()
        && Objects.toString(label.getText(), "").contains(needle)) {
      return label;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JLabel found = findVisibleLabelContaining(child, needle);
      if (found != null) return found;
    }
    return null;
  }

  private static ChatFixture newChatFixture(IrcClientService irc) throws Exception {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    ChatTranscriptStore transcripts =
        new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null, null);

    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    when(serverTree.managedChannelsChangedByServer())
        .thenReturn(io.reactivex.rxjava3.core.Flowable.never());
    when(serverTree.openChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.managedChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.channelSortModeForServer(anyString()))
        .thenReturn(ServerTreeDockable.ChannelSortMode.CUSTOM);

    NotificationStore notificationStore = new NotificationStore();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    ModeRoutingPort modeRoutingState = mock(ModeRoutingPort.class);
    ServerIsupportStatePort serverIsupportState =
        FunctionalTestWiringSupport.fallbackIsupportState();
    BackendUiProfileProvider backendUiProfileProvider = mock(BackendUiProfileProvider.class);
    when(backendUiProfileProvider.profileForServer(anyString()))
        .thenAnswer(
            invocation ->
                BackendUiProfile.ircOnly(Objects.toString(invocation.getArgument(0), "")));
    MessageActionCapabilityPolicy messageActionCapabilityPolicy =
        mock(MessageActionCapabilityPolicy.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    MonitorListService monitorListService = mock(MonitorListService.class);
    when(monitorListService.changes()).thenReturn(io.reactivex.rxjava3.core.Flowable.never());
    when(monitorListService.listNicks(anyString())).thenReturn(List.of());
    UserListStore userListStore = mock(UserListStore.class);
    when(userListStore.get(anyString(), anyString())).thenReturn(List.of());
    UserListDockable usersDock = mock(UserListDockable.class);
    NickContextMenuFactory nickContextMenuFactory = new NickContextMenuFactory();
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    ChannelMetadataPort channelMetadata = mock(ChannelMetadataPort.class);
    ChatLogViewerService chatLogViewerService = mock(ChatLogViewerService.class);
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(io.reactivex.rxjava3.core.Flowable.never());
    DccTransferStore dccTransferStore = new DccTransferStore();
    TerminalDockable terminalDockable = new TerminalDockable(mock(ConsoleTeeService.class));
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    SpellcheckSettingsBus spellcheckSettingsBus = mock(SpellcheckSettingsBus.class);
    CommandHistoryStore commandHistoryStore = mock(CommandHistoryStore.class);

    ExecutorService logViewerExecutor =
        VirtualThreads.newSingleThreadExecutor("test-ircv3-showcase-log-viewer");
    ExecutorService interceptorRefreshExecutor =
        VirtualThreads.newSingleThreadExecutor("test-ircv3-showcase-interceptor");

    AtomicReference<ChatDockable> chatRef = new AtomicReference<>();
    onEdt(
        () ->
            chatRef.set(
                FunctionalTestWiringSupport.newChatDockable(
                    transcripts,
                    serverTree,
                    notificationStore,
                    activationBus,
                    outboundBus,
                    irc,
                    modeRoutingState,
                    serverIsupportState,
                    backendUiProfileProvider,
                    messageActionCapabilityPolicy,
                    activeInputRouter,
                    ignoreListService,
                    ignoreStatusService,
                    ignoreListDialog,
                    monitorListService,
                    userListStore,
                    usersDock,
                    nickContextMenuFactory,
                    proxyResolver,
                    chatHistoryService,
                    channelMetadata,
                    chatLogViewerService,
                    interceptorStore,
                    dccTransferStore,
                    terminalDockable,
                    null,
                    null,
                    null,
                    settingsBus,
                    spellcheckSettingsBus,
                    commandHistoryStore,
                    logViewerExecutor,
                    interceptorRefreshExecutor)));

    ChatDockable chat = chatRef.get();
    MessageInputPanel inputPanel = onEdtCall(() -> findFirst(chat, MessageInputPanel.class));
    assertNotNull(inputPanel);
    return new ChatFixture(
        chat, transcripts, inputPanel, logViewerExecutor, interceptorRefreshExecutor);
  }

  private static ServiceFixture newService(RuntimeIrcConfig cfg) {
    LinkedHashMap<String, IrcProperties.Server> serversById = new LinkedHashMap<>();
    serversById.put(cfg.serverId(), cfg.toServer());

    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.require(anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              IrcProperties.Server server = serversById.get(sid);
              if (server == null) {
                throw new IllegalArgumentException("Unknown server id: " + sid);
              }
              return server;
            });
    when(serverCatalog.find(anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    serversById.get(Objects.toString(invocation.getArgument(0), "").trim())));
    when(serverCatalog.containsId(anyString()))
        .thenAnswer(
            invocation ->
                serversById.containsKey(Objects.toString(invocation.getArgument(0), "").trim()));

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe Functional",
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

    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(bouncerBackends.backendIds()).thenReturn(Set.of());

    ScheduledExecutorService heartbeatExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("it-ircv3-heartbeat"));
    ScheduledExecutorService reconnectExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("it-ircv3-reconnect"));
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

  private static ThreadFactory namedDaemonFactory(String name) {
    String threadName = Objects.toString(name, "").trim();
    if (threadName.isEmpty()) threadName = "ircv3-functional";
    final String finalThreadName = threadName;
    return runnable -> {
      Thread thread = new Thread(runnable, finalThreadName);
      thread.setDaemon(true);
      return thread;
    };
  }

  private static void awaitCondition(
      BooleanSupplier condition, Duration timeout, String timeoutMessage) throws Exception {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError(timeoutMessage);
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static <T extends Component> T findFirst(Component root, Class<T> type) {
    if (root == null || type == null) return null;
    if (type.isInstance(root)) return type.cast(root);
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findFirst(child, type);
      if (found != null) return found;
    }
    return null;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private static final class UiEventRelay implements AutoCloseable {
    private final ChatTranscriptStore transcripts;
    private final ChatDockable chat;
    private final Disposable subscription;

    private UiEventRelay(
        PircbotxIrcClientService service, ChatTranscriptStore transcripts, ChatDockable chat) {
      this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
      this.chat = Objects.requireNonNull(chat, "chat");
      this.subscription =
          Objects.requireNonNull(service, "service").events().subscribe(this::onEvent, err -> {});
    }

    private void onEvent(ServerIrcEvent event) {
      if (event == null || event.event() == null) return;
      String sid = Objects.toString(event.serverId(), "").trim();
      IrcEvent payload = event.event();
      try {
        onEdt(
            () -> {
              switch (payload) {
                case IrcEvent.JoinedChannel joined -> {
                  transcripts.ensureTargetExists(new TargetRef(sid, joined.channel()));
                }
                case IrcEvent.ChannelMessage message -> {
                  TargetRef target = new TargetRef(sid, message.channel());
                  transcripts.appendChatAt(
                      target,
                      message.from(),
                      message.text(),
                      false,
                      epochMs(message.at()),
                      message.messageId(),
                      safeTags(message.ircv3Tags()));
                }
                case IrcEvent.MessageReactObserved react -> {
                  TargetRef target = new TargetRef(sid, react.target());
                  transcripts.applyMessageReaction(
                      target,
                      react.messageId(),
                      react.reaction(),
                      react.from(),
                      epochMs(react.at()));
                }
                case IrcEvent.MessageRedactionObserved redaction -> {
                  TargetRef target = new TargetRef(sid, redaction.target());
                  transcripts.applyMessageRedaction(
                      target,
                      redaction.messageId(),
                      redaction.from(),
                      epochMs(redaction.at()),
                      "",
                      Map.of("draft/delete", redaction.messageId()));
                }
                case IrcEvent.UserTypingObserved typing -> {
                  TargetRef target = new TargetRef(sid, typing.target());
                  chat.showTypingIndicator(target, typing.from(), typing.state());
                }
                case IrcEvent.Ircv3CapabilityChanged capabilityChanged -> {
                  chat.normalizeIrcv3CapabilityUiState(sid, capabilityChanged.capability());
                }
                default -> {
                  // Other events are not needed for screenshot staging.
                }
              }
            });
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    private static long epochMs(Instant instant) {
      return instant != null ? instant.toEpochMilli() : System.currentTimeMillis();
    }

    private static Map<String, String> safeTags(Map<String, String> tags) {
      if (tags == null || tags.isEmpty()) {
        return Map.of();
      }
      return Map.copyOf(tags);
    }

    @Override
    public void close() {
      try {
        subscription.dispose();
      } catch (Exception ignored) {
      }
    }
  }

  private record ChatFixture(
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      MessageInputPanel inputPanel,
      ExecutorService logViewerExecutor,
      ExecutorService interceptorRefreshExecutor)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      onEdt(() -> shutdownChatDockable(chat));
      flushEdt();
      try {
        logViewerExecutor.shutdownNow();
      } catch (Exception ignored) {
      }
      try {
        interceptorRefreshExecutor.shutdownNow();
      } catch (Exception ignored) {
      }
    }
  }

  private static void shutdownChatDockable(ChatDockable chat)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (chat == null) return;
    var shutdown = ChatDockable.class.getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(chat);
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
  }

  private record ShowcaseConfig(
      boolean enabled,
      String ircImage,
      String channel,
      String appNick,
      String authorNick,
      String peerNick,
      Path outputDir) {

    private static final String DEFAULT_IRC_IMAGE = "ghcr.io/ergochat/ergo:stable";
    private static final String DEFAULT_CHANNEL = "#ircv3-showcase";
    private static final String DEFAULT_APP_NICK = "ircafe-ui";
    private static final String DEFAULT_AUTHOR_NICK = "alicebot";
    private static final String DEFAULT_PEER_NICK = "bobbot";
    private static final String DEFAULT_OUTPUT_DIR = "build/ircv3-showcase";

    static ShowcaseConfig fromSystem() {
      boolean enabled =
          readBoolean(
              "ircv3.it.container.functional.enabled",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_ENABLED",
              false);
      String ircImage =
          readString(
              "ircv3.it.container.functional.irc-image",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_IRC_IMAGE",
              DEFAULT_IRC_IMAGE);
      String channel =
          readString(
              "ircv3.it.container.functional.channel",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_CHANNEL",
              DEFAULT_CHANNEL);
      String appNick =
          readString(
              "ircv3.it.container.functional.app-nick",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_APP_NICK",
              DEFAULT_APP_NICK);
      String authorNick =
          readString(
              "ircv3.it.container.functional.author-nick",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_AUTHOR_NICK",
              DEFAULT_AUTHOR_NICK);
      String peerNick =
          readString(
              "ircv3.it.container.functional.peer-nick",
              "IRCV3_IT_CONTAINER_FUNCTIONAL_PEER_NICK",
              DEFAULT_PEER_NICK);
      Path outputDir =
          Path.of(
              readString(
                  "ircv3.it.container.functional.output-dir",
                  "IRCV3_IT_CONTAINER_FUNCTIONAL_OUTPUT_DIR",
                  DEFAULT_OUTPUT_DIR));
      return new ShowcaseConfig(
          enabled, ircImage, channel, appNick, authorNick, peerNick, outputDir);
    }

    RuntimeIrcConfig appRuntimeConfig(String host, int port) {
      return new RuntimeIrcConfig("ircv3-app", host, port, "", appNick, appNick, "IRCafe UI");
    }

    RuntimeIrcConfig authorRuntimeConfig(String host, int port) {
      return new RuntimeIrcConfig(
          "ircv3-author", host, port, "", authorNick, authorNick, "IRCv3 Author");
    }

    RuntimeIrcConfig peerRuntimeConfig(String host, int port) {
      return new RuntimeIrcConfig("ircv3-peer", host, port, "", peerNick, peerNick, "IRCv3 Peer");
    }
  }

  private static boolean readBoolean(String propertyName, String envName, boolean defaultValue) {
    String raw = System.getProperty(propertyName);
    if (raw == null || raw.isBlank()) {
      raw = System.getenv(envName);
    }
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "1", "true", "yes", "on" -> true;
      case "0", "false", "no", "off" -> false;
      default -> defaultValue;
    };
  }

  private static String readString(String propertyName, String envName, String defaultValue) {
    String raw = System.getProperty(propertyName);
    if (raw == null || raw.isBlank()) {
      raw = System.getenv(envName);
    }
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return raw.trim();
  }
}
