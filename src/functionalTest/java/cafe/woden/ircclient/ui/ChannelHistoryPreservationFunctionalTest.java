package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.logging.LogLineFactory;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
import cafe.woden.ircclient.logging.history.DbChatHistoryService;
import cafe.woden.ircclient.logging.history.LoadOlderControlState;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatHistoryTranscriptPortAdapter;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.ChatThemeSettingsBus;
import cafe.woden.ircclient.ui.shell.StatusBar;
import io.reactivex.rxjava3.core.Completable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledDocument;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChannelHistoryPreservationFunctionalTest {

  @TempDir java.nio.file.Path tempDir;

  @Test
  void mixedCaseHistoryReloadsAfterCloseAndReopen() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef mixedCase = new TargetRef("libera", "#ChanCase");
      TargetRef lowerCase = new TargetRef("libera", "#chancase");
      long ts = 1_770_000_000_000L;

      fixture
          .repoFixture()
          .repo()
          .insert(
              fixture.lineFactory.chatAt(
                  mixedCase, "alice", "history survives close/reopen", false, ts));

      onEdt(() -> fixture.targetCoordinator.onTargetSelected(lowerCase));
      waitFor(
          () ->
              transcriptText(fixture.transcripts.document(lowerCase))
                  .contains("history survives close/reopen"),
          Duration.ofSeconds(3));

      onEdt(() -> fixture.targetCoordinator.closeChannel(lowerCase));
      flushEdt();
      assertEquals("", transcriptText(fixture.transcripts.document(lowerCase)));

      onEdt(() -> fixture.targetCoordinator.onTargetSelected(lowerCase));
      waitFor(
          () ->
              transcriptText(fixture.transcripts.document(lowerCase))
                  .contains("history survives close/reopen"),
          Duration.ofSeconds(3));
      assertTrue(
          transcriptText(fixture.transcripts.document(lowerCase))
              .contains("history survives close/reopen"));
    } finally {
      fixture.shutdown();
    }
  }

  @Test
  void mixedCasePrivateHistoryReloadsAfterCloseAndReopen() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef storedTarget = new TargetRef("libera", "Alice");
      TargetRef selectedTarget = new TargetRef("libera", "alice");
      long ts = 1_770_000_000_100L;

      fixture
          .repoFixture()
          .repo()
          .insert(
              fixture.lineFactory.chatAt(
                  storedTarget, "Alice", "pm history survives close/reopen", false, ts));

      onEdt(() -> fixture.targetCoordinator.onTargetSelected(selectedTarget));
      waitFor(
          () ->
              transcriptText(fixture.transcripts.document(selectedTarget))
                  .contains("pm history survives close/reopen"),
          Duration.ofSeconds(3));

      onEdt(() -> fixture.targetCoordinator.closeTarget(selectedTarget));
      flushEdt();
      assertEquals("", transcriptText(fixture.transcripts.document(selectedTarget)));

      onEdt(
          () ->
              fixture.targetCoordinator.openPrivateConversation(
                  new PrivateMessageRequest("libera", "alice")));
      onEdt(() -> fixture.targetCoordinator.onTargetSelected(selectedTarget));
      waitFor(
          () ->
              transcriptText(fixture.transcripts.document(selectedTarget))
                  .contains("pm history survives close/reopen"),
          Duration.ofSeconds(3));
      assertTrue(
          transcriptText(fixture.transcripts.document(selectedTarget))
              .contains("pm history survives close/reopen"));
    } finally {
      fixture.shutdown();
    }
  }

  private Fixture createFixture() throws Exception {
    IrcProperties props = new IrcProperties(null, List.of(server("libera")));
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(tempDir.resolve("ircafe-runtime.yml").toString(), props);
    ServerRegistry serverRegistry = new ServerRegistry(props, runtimeConfig);
    ServerCatalog serverCatalog = new ServerCatalog(serverRegistry, new EphemeralServerRegistry());

    ServerTreeDockable serverTree =
        onEdtCall(
            () ->
                new ServerTreeDockable(
                    serverCatalog,
                    runtimeConfig,
                    new LogProperties(true, true, true, true, true, 0, null, null, null),
                    null,
                    null,
                    new ConnectButton(),
                    new DisconnectButton(),
                    new NotificationStore(),
                    null,
                    null,
                    null));

    ChatThemeSettingsBus chatThemeBus = new ChatThemeSettingsBus(null);
    ChatStyles chatStyles = new ChatStyles(chatThemeBus);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, chatStyles, null);
    ChatTranscriptStore transcripts =
        new ChatTranscriptStore(chatStyles, renderer, null, null, null, null, null, null, null);

    ChatDockable chat = mock(ChatDockable.class);
    MentionPatternRegistry mentions = mock(MentionPatternRegistry.class);
    NotificationStore notifications = mock(NotificationStore.class);
    UserListDockable users = mock(UserListDockable.class);
    StatusBar statusBar = mock(StatusBar.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    ConnectButton connectBtn = mock(ConnectButton.class);
    DisconnectButton disconnectBtn = mock(DisconnectButton.class);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mentions,
            notifications,
            users,
            statusBar,
            connectBtn,
            disconnectBtn,
            activationBus,
            outboundBus,
            chatDockManager,
            activeInputRouter);

    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick(anyString())).thenReturn(Optional.empty());
    when(irc.requestNames(anyString(), anyString())).thenReturn(Completable.complete());

    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    when(connectionCoordinator.isConnected(anyString())).thenReturn(false);

    RepoFixture repoFixture = openRepoFixture(tempDir.resolve("history-functional-db"));
    ChatLogRepository repo = repoFixture.repo;

    ExecutorService historyExec = Executors.newSingleThreadExecutor();
    DbChatHistoryService historyService =
        new DbChatHistoryService(
            repo,
            new LogProperties(true, true, true, true, true, 0, null, null, null),
            new FixedHistoryTranscriptPort(transcripts, 100, 200),
            null,
            null,
            historyExec);

    TargetChatHistoryPort historyPort =
        new TargetChatHistoryPort() {
          @Override
          public void onTargetSelected(TargetRef target) {
            historyService.onTargetSelected(target);
          }

          @Override
          public void reset(TargetRef target) {
            historyService.reset(target);
          }
        };

    TargetCoordinator targetCoordinator =
        new TargetCoordinator(
            ui,
            new UserListStore(),
            irc,
            serverRegistry,
            runtimeConfig,
            connectionCoordinator,
            mock(IgnoreListService.class),
            mock(UserhostQueryService.class),
            mock(UserInfoEnrichmentService.class),
            historyPort,
            mock(TargetLogMaintenancePort.class),
            mock(ExecutorService.class),
            mock(ScheduledExecutorService.class));

    return new Fixture(
        serverTree,
        transcripts,
        targetCoordinator,
        historyExec,
        repoFixture,
        new LogLineFactory(Clock.systemUTC()));
  }

  private static RepoFixture openRepoFixture(java.nio.file.Path basePath) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
    ds.setUrl("jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc");
    ds.setUsername("SA");
    ds.setPassword("");

    Flyway.configure().dataSource(ds).locations("classpath:db/migration/chatlog").load().migrate();

    JdbcTemplate jdbc = new JdbcTemplate(ds);
    return new RepoFixture(jdbc, new ChatLogRepository(jdbc));
  }

  private static IrcProperties.Server server(String id) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable r) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    final java.util.concurrent.atomic.AtomicReference<T> out =
        new java.util.concurrent.atomic.AtomicReference<>();
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

  private static String transcriptText(StyledDocument doc) {
    if (doc == null) return "";
    try {
      return doc.getText(0, doc.getLength());
    } catch (Exception e) {
      return "";
    }
  }

  private record RepoFixture(JdbcTemplate jdbc, ChatLogRepository repo) implements AutoCloseable {
    @Override
    public void close() {
      try {
        jdbc.execute("SHUTDOWN");
      } catch (Exception ignored) {
      }
    }
  }

  private record Fixture(
      ServerTreeDockable serverTree,
      ChatTranscriptStore transcripts,
      TargetCoordinator targetCoordinator,
      ExecutorService historyExec,
      RepoFixture repoFixture,
      LogLineFactory lineFactory) {
    void shutdown() throws Exception {
      historyExec.shutdownNow();
      historyExec.awaitTermination(2, TimeUnit.SECONDS);
      repoFixture.close();
      onEdt(serverTree::shutdown);
      flushEdt();
    }
  }

  private static final class FixedHistoryTranscriptPort implements ChatHistoryTranscriptPort {
    private final ChatHistoryTranscriptPortAdapter delegate;
    private final int initialLoadLines;
    private final int pageSize;

    private FixedHistoryTranscriptPort(
        ChatTranscriptStore transcripts, int initialLoadLines, int pageSize) {
      this.delegate = new ChatHistoryTranscriptPortAdapter(transcripts, null, null);
      this.initialLoadLines = initialLoadLines;
      this.pageSize = pageSize;
    }

    @Override
    public StyledDocument document(TargetRef ref) {
      return delegate.document(ref);
    }

    @Override
    public java.util.OptionalLong earliestTimestampEpochMs(TargetRef ref) {
      return delegate.earliestTimestampEpochMs(ref);
    }

    @Override
    public java.awt.Component ensureLoadOlderMessagesControl(TargetRef ref) {
      return delegate.ensureLoadOlderMessagesControl(ref);
    }

    @Override
    public void setLoadOlderMessagesControlState(TargetRef ref, LoadOlderControlState state) {
      delegate.setLoadOlderMessagesControlState(ref, state);
    }

    @Override
    public void setLoadOlderMessagesControlHandler(
        TargetRef ref, java.util.function.BooleanSupplier onLoad) {
      delegate.setLoadOlderMessagesControlHandler(ref, onLoad);
    }

    @Override
    public void beginHistoryInsertBatch(TargetRef ref) {
      delegate.beginHistoryInsertBatch(ref);
    }

    @Override
    public void endHistoryInsertBatch(TargetRef ref) {
      delegate.endHistoryInsertBatch(ref);
    }

    @Override
    public int loadOlderInsertOffset(TargetRef ref) {
      return delegate.loadOlderInsertOffset(ref);
    }

    @Override
    public boolean hasContentAfterOffset(TargetRef ref, int offset) {
      return delegate.hasContentAfterOffset(ref, offset);
    }

    @Override
    public void ensureHistoryDivider(TargetRef ref, int insertAt, String labelText) {
      delegate.ensureHistoryDivider(ref, insertAt, labelText);
    }

    @Override
    public void markHistoryDividerPending(TargetRef ref, String labelText) {
      delegate.markHistoryDividerPending(ref, labelText);
    }

    @Override
    public int insertChatFromHistoryAt(
        TargetRef ref,
        int insertAt,
        String from,
        String text,
        boolean outgoingLocalEcho,
        long tsEpochMs) {
      return delegate.insertChatFromHistoryAt(
          ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
    }

    @Override
    public int insertActionFromHistoryAt(
        TargetRef ref,
        int insertAt,
        String from,
        String text,
        boolean outgoingLocalEcho,
        long tsEpochMs) {
      return delegate.insertActionFromHistoryAt(
          ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
    }

    @Override
    public int insertNoticeFromHistoryAt(
        TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
      return delegate.insertNoticeFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
    }

    @Override
    public int insertStatusFromHistoryAt(
        TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
      return delegate.insertStatusFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
    }

    @Override
    public int insertErrorFromHistoryAt(
        TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
      return delegate.insertErrorFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
    }

    @Override
    public int insertPresenceFromHistoryAt(
        TargetRef ref, int insertAt, String text, long tsEpochMs) {
      return delegate.insertPresenceFromHistoryAt(ref, insertAt, text, tsEpochMs);
    }

    @Override
    public int insertSpoilerChatFromHistoryAt(
        TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
      return delegate.insertSpoilerChatFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
    }

    @Override
    public void appendChatFromHistory(
        TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
      delegate.appendChatFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
    }

    @Override
    public void appendActionFromHistory(
        TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
      delegate.appendActionFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
    }

    @Override
    public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
      delegate.appendNoticeFromHistory(ref, from, text, tsEpochMs);
    }

    @Override
    public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
      delegate.appendStatusFromHistory(ref, from, text, tsEpochMs);
    }

    @Override
    public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
      delegate.appendErrorFromHistory(ref, from, text, tsEpochMs);
    }

    @Override
    public void appendPresenceFromHistory(TargetRef ref, String text, long tsEpochMs) {
      delegate.appendPresenceFromHistory(ref, text, tsEpochMs);
    }

    @Override
    public void appendSpoilerChatFromHistory(
        TargetRef ref, String from, String text, long tsEpochMs) {
      delegate.appendSpoilerChatFromHistory(ref, from, text, tsEpochMs);
    }

    @Override
    public int chatHistoryInitialLoadLines() {
      return initialLoadLines;
    }

    @Override
    public int chatHistoryPageSize() {
      return pageSize;
    }

    @Override
    public int chatHistoryAutoLoadWheelDebounceMs() {
      return 2000;
    }

    @Override
    public int chatHistoryLoadOlderChunkSize() {
      return 20;
    }

    @Override
    public int chatHistoryLoadOlderChunkDelayMs() {
      return 10;
    }

    @Override
    public int chatHistoryLoadOlderChunkEdtBudgetMs() {
      return 6;
    }

    @Override
    public boolean chatHistoryLockViewportDuringLoadOlder() {
      return true;
    }

    @Override
    public int chatHistoryRemoteRequestTimeoutSeconds() {
      return 6;
    }

    @Override
    public int chatHistoryRemoteZncPlaybackTimeoutSeconds() {
      return 18;
    }

    @Override
    public int chatHistoryRemoteZncPlaybackWindowMinutes() {
      return 360;
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
