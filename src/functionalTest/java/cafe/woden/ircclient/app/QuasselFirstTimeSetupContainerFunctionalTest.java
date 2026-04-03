package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.quassel.QuasselCoreAuthHandshake;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec;
import cafe.woden.ircclient.irc.quassel.QuasselCoreIrcClientService;
import cafe.woden.ircclient.irc.quassel.QuasselCoreProtocolProbe;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSocketConnector;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.testutil.FunctionalTestWiringSupport;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.SwingUiPort;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Container-backed functional UI flow for first-time Quassel setup through IRCafe coordinator
 * logic.
 */
class QuasselFirstTimeSetupContainerFunctionalTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(120);
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);
  private static final Duration NETWORK_SYNC_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration IRC_BOT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration UI_DIALOG_TIMEOUT = Duration.ofSeconds(15);
  private static final long POLL_INTERVAL_MS = 50L;
  private static final long DIALOG_POLL_MS = 25L;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void firstTimeSetupThroughUiDialogConnectsAndReconnectsWithoutErrors() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "UI+container Quassel functional test disabled. Set"
            + " -Dquassel.it.container.ui.functional.enabled=true.");
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Swing dialogs require a display");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    try (GenericContainer<?> core =
        new GenericContainer<>(image)
            .withExposedPorts(cfg.containerPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(STARTUP_TIMEOUT)) {
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.containerPort()));
      IrcProperties.Server server = runtimeCfg.toServer();

      ServerCatalog serverCatalog = mock(ServerCatalog.class);
      when(serverCatalog.require(runtimeCfg.serverId())).thenReturn(server);
      when(serverCatalog.find(runtimeCfg.serverId())).thenReturn(Optional.of(server));
      when(serverCatalog.containsId(runtimeCfg.serverId())).thenReturn(true);

      QuasselCoreIrcClientService service = newService(runtimeCfg, serverCatalog);

      ServerRegistry serverRegistry = mock(ServerRegistry.class);
      when(serverRegistry.serverIds()).thenReturn(Set.of(runtimeCfg.serverId()));
      when(serverRegistry.servers()).thenReturn(List.of(server));

      ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
      when(runtimeConfig.readServerAutoConnectOnStartByServer()).thenReturn(Map.of());
      when(runtimeConfig.readPrivateMessageTargets(anyString())).thenReturn(List.of());
      when(runtimeConfig.readKnownChannels(anyString())).thenReturn(List.of());

      ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
      List<String> errors = new CopyOnWriteArrayList<>();
      List<String> statuses = new CopyOnWriteArrayList<>();
      doAnswer(
              inv -> {
                String from = Objects.toString(inv.getArgument(1), "");
                String text = Objects.toString(inv.getArgument(2), "");
                errors.add(from + ":" + text);
                return null;
              })
          .when(transcripts)
          .appendError(any(TargetRef.class), anyString(), anyString());
      doAnswer(
              inv -> {
                String from = Objects.toString(inv.getArgument(1), "");
                String text = Objects.toString(inv.getArgument(2), "");
                statuses.add(from + ":" + text);
                return null;
              })
          .when(transcripts)
          .appendStatus(any(TargetRef.class), anyString(), anyString());
      doAnswer(
              inv -> {
                String from = Objects.toString(inv.getArgument(1), "");
                String text = Objects.toString(inv.getArgument(2), "");
                statuses.add(from + ":" + text);
                return null;
              })
          .when(transcripts)
          .appendStatusAt(any(TargetRef.class), anyString(), anyString(), anyLong());

      SwingUiPort ui = newUi(transcripts);
      LogProperties logProps =
          new LogProperties(false, true, false, true, true, true, 0, 50_000, 250, null);
      TrayNotificationsPort trayNotifications = mock(TrayNotificationsPort.class);

      ConnectionCoordinator coordinator =
          FunctionalTestWiringSupport.newConnectionCoordinator(
              IrcConnectionLifecyclePort.from(service),
              IrcBackendAvailabilityPort.from(service),
              QuasselCoreControlPort.from(service),
              ui,
              serverRegistry,
              serverCatalog,
              runtimeConfig,
              logProps,
              trayNotifications);

      Disposable relay =
          service
              .events()
              .subscribe(
                  se -> coordinator.handleConnectivityEvent(se.serverId(), se.event(), null));

      AtomicBoolean runDialogDriver = new AtomicBoolean(true);
      AtomicInteger setupDialogsHandled = new AtomicInteger();
      Thread dialogDriver =
          new Thread(
              () ->
                  driveQuasselSetupDialog(
                      runtimeCfg.serverId(),
                      runtimeCfg.login(),
                      runtimeCfg.password(),
                      runDialogDriver,
                      setupDialogsHandled),
              "quassel-setup-dialog-driver");
      dialogDriver.setDaemon(true);
      dialogDriver.start();

      try {
        coordinator.connectOne(runtimeCfg.serverId());

        awaitCondition(
            () -> setupDialogsHandled.get() >= 1,
            CONNECT_TIMEOUT,
            "Timed out waiting for first-time Quassel setup dialog");
        awaitCondition(
            () -> coordinator.isConnected(runtimeCfg.serverId()),
            CONNECT_TIMEOUT,
            "Timed out waiting for first-time Quassel connect");

        assertEquals(
            1, setupDialogsHandled.get(), "first-time setup should show exactly one dialog");
        assertTrue(containsNoConnectionErrors(errors), "unexpected connection errors: " + errors);
        assertTrue(
            statuses.stream().anyMatch(s -> s.contains("Connection ready")),
            "expected Connection ready status, got: " + statuses);

        coordinator.disconnectOne(runtimeCfg.serverId(), "functional setup flow disconnect");
        awaitCondition(
            () -> !coordinator.isConnected(runtimeCfg.serverId()),
            CONNECT_TIMEOUT,
            "Timed out waiting for disconnect before reconnect");

        coordinator.connectOne(runtimeCfg.serverId());
        awaitCondition(
            () -> coordinator.isConnected(runtimeCfg.serverId()),
            CONNECT_TIMEOUT,
            "Timed out waiting for reconnect");
        Thread.sleep(500);

        assertEquals(
            1,
            setupDialogsHandled.get(),
            "reconnect should not reopen Quassel setup dialog after initial setup");
        assertTrue(containsNoConnectionErrors(errors), "unexpected connection errors: " + errors);
      } finally {
        runDialogDriver.set(false);
        dialogDriver.join(Duration.ofSeconds(5));
        relay.dispose();
        try {
          coordinator.disconnectOne(runtimeCfg.serverId(), "functional setup flow cleanup");
        } catch (Exception ignored) {
        }
        try {
          service
              .disconnect(runtimeCfg.serverId(), "functional setup flow shutdown")
              .blockingAwait();
        } catch (Exception ignored) {
        }
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @Test
  void firstTimeSetupCanAddNetworkViaUiAndReceiveIrcThroughQuassel() throws Exception {
    NetworkUiConfig cfg = NetworkUiConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Quassel+IRCd UI functional test disabled. Set"
            + " -Dquassel.it.container.ui.network.functional.enabled=true.");
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Swing dialogs require a display");
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
                .withStartupTimeout(STARTUP_TIMEOUT);
        GenericContainer<?> core =
            new GenericContainer<>(quasselImage)
                .withNetwork(network)
                .withExposedPorts(cfg.quasselPort())
                .withEnv("TZ", "UTC")
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(STARTUP_TIMEOUT)) {
      ircServer.start();
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.quasselPort()));
      IrcProperties.Server server = runtimeCfg.toServer();

      ServerCatalog serverCatalog = mock(ServerCatalog.class);
      when(serverCatalog.require(runtimeCfg.serverId())).thenReturn(server);
      when(serverCatalog.find(runtimeCfg.serverId())).thenReturn(Optional.of(server));
      when(serverCatalog.containsId(runtimeCfg.serverId())).thenReturn(true);

      QuasselCoreIrcClientService service = newService(runtimeCfg, serverCatalog);

      ServerRegistry serverRegistry = mock(ServerRegistry.class);
      when(serverRegistry.serverIds()).thenReturn(Set.of(runtimeCfg.serverId()));
      when(serverRegistry.servers()).thenReturn(List.of(server));

      ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
      when(runtimeConfig.readServerAutoConnectOnStartByServer()).thenReturn(Map.of());
      when(runtimeConfig.readPrivateMessageTargets(anyString())).thenReturn(List.of());
      when(runtimeConfig.readKnownChannels(anyString())).thenReturn(List.of());

      ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
      List<String> errors = new CopyOnWriteArrayList<>();
      doAnswer(
              inv -> {
                String from = Objects.toString(inv.getArgument(1), "");
                String text = Objects.toString(inv.getArgument(2), "");
                errors.add(from + ":" + text);
                return null;
              })
          .when(transcripts)
          .appendError(any(TargetRef.class), anyString(), anyString());

      SwingUiPort ui = newUi(transcripts);
      LogProperties logProps =
          new LogProperties(false, true, false, true, true, true, 0, 50_000, 250, null);
      TrayNotificationsPort trayNotifications = mock(TrayNotificationsPort.class);

      ConnectionCoordinator coordinator =
          FunctionalTestWiringSupport.newConnectionCoordinator(
              IrcConnectionLifecyclePort.from(service),
              IrcBackendAvailabilityPort.from(service),
              QuasselCoreControlPort.from(service),
              ui,
              serverRegistry,
              serverCatalog,
              runtimeConfig,
              logProps,
              trayNotifications);

      TestSubscriber<ServerIrcEvent> events = service.events().test();
      Disposable relay =
          service
              .events()
              .subscribe(
                  se -> coordinator.handleConnectivityEvent(se.serverId(), se.event(), null));

      AtomicBoolean runDialogDriver = new AtomicBoolean(true);
      AtomicInteger setupDialogsHandled = new AtomicInteger();
      Thread dialogDriver =
          new Thread(
              () ->
                  driveQuasselSetupDialog(
                      runtimeCfg.serverId(),
                      runtimeCfg.login(),
                      runtimeCfg.password(),
                      runDialogDriver,
                      setupDialogsHandled),
              "quassel-setup-dialog-driver-network");
      dialogDriver.setDaemon(true);
      dialogDriver.start();

      try (SimpleIrcBot bot =
          SimpleIrcBot.connect(
              ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), cfg.botNick())) {
        String sid = runtimeCfg.serverId();

        coordinator.connectOne(sid);
        awaitCondition(
            () -> setupDialogsHandled.get() >= 1,
            CONNECT_TIMEOUT,
            "Timed out waiting for first-time Quassel setup dialog");
        awaitCondition(
            () -> coordinator.isConnected(sid),
            CONNECT_TIMEOUT,
            "Timed out waiting for first-time Quassel connect");

        String networkName = "ui-net-" + Long.toHexString(System.currentTimeMillis());
        QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest =
            promptAddNetworkViaUi(
                ui,
                sid,
                service.quasselCoreNetworks(sid),
                networkName,
                cfg.ircAlias(),
                cfg.ircPort());
        service.quasselCoreCreateNetwork(sid, createRequest).blockingAwait();

        QuasselCoreControlPort.QuasselCoreNetworkSummary createdNetwork =
            awaitNetworkObserved(service, sid, networkName, NETWORK_SYNC_TIMEOUT);
        String connectToken = promptConnectNetworkViaUi(ui, sid, List.of(createdNetwork));
        service.quasselCoreConnectNetwork(sid, connectToken).blockingAwait();
        awaitNetworkConnected(service, sid, createdNetwork.networkId(), NETWORK_SYNC_TIMEOUT);

        bot.join(cfg.channel(), IRC_BOT_TIMEOUT);

        int joinedCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
        service.joinChannel(sid, cfg.channel()).blockingAwait();
        IrcEvent.JoinedChannel joined =
            awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
        assertEquals(cfg.channel(), joined.channel());

        int messageCount =
            countChannelMessages(events, sid, cfg.channel(), cfg.botNick(), cfg.messageText());
        bot.privmsg(cfg.channel(), cfg.messageText());
        awaitChannelMessage(
            events,
            sid,
            cfg.channel(),
            cfg.botNick(),
            cfg.messageText(),
            messageCount,
            MESSAGE_TIMEOUT);

        assertTrue(containsNoConnectionErrors(errors), "unexpected errors: " + errors);
      } finally {
        runDialogDriver.set(false);
        dialogDriver.join(Duration.ofSeconds(5));
        relay.dispose();
        try {
          events.cancel();
        } catch (Exception ignored) {
        }
        try {
          coordinator.disconnectOne(runtimeCfg.serverId(), "functional network flow cleanup");
        } catch (Exception ignored) {
        }
        try {
          service
              .disconnect(runtimeCfg.serverId(), "functional network flow shutdown")
              .blockingAwait();
        } catch (Exception ignored) {
        }
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static SwingUiPort newUi(ChatTranscriptStore transcripts) {
    return new SwingUiPort(
        mock(ServerTreeDockable.class),
        mock(ChatDockable.class),
        transcripts,
        mock(MentionPatternRegistry.class),
        mock(NotificationStore.class),
        mock(UserListDockable.class),
        mock(StatusBar.class),
        mock(ConnectButton.class),
        mock(DisconnectButton.class),
        new TargetActivationBus(),
        new OutboundLineBus(),
        mock(ChatDockManager.class),
        new ActiveInputRouter());
  }

  private static void awaitCondition(
      BooleanSupplier condition, Duration timeout, String timeoutMessage)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError(timeoutMessage);
  }

  private static boolean containsNoConnectionErrors(List<String> errors) {
    for (String err : errors) {
      String normalized = Objects.toString(err, "").toLowerCase(Locale.ROOT);
      if (normalized.contains("(conn-error)")
          || normalized.contains("(qsetup-error)")
          || normalized.contains("connect failed")
          || normalized.contains("invalid username")
          || normalized.contains("rejected")) {
        return false;
      }
    }
    return true;
  }

  private static void driveQuasselSetupDialog(
      String serverId,
      String adminUser,
      String adminPassword,
      AtomicBoolean running,
      AtomicInteger handledDialogs) {
    String title = "Quassel Core Setup - " + serverId;
    Set<Window> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    while (running.get()) {
      JDialog dialog = findVisibleDialog(title);
      if (dialog != null && seen.add(dialog)) {
        onEdt(
            () -> {
              JTextField userField =
                  findComponentNextToLabel(dialog, "Admin user", JTextField.class, c -> true);
              JPasswordField passField =
                  findComponentNextToLabel(
                      dialog, "Admin password", JPasswordField.class, c -> true);
              if (userField != null) userField.setText(adminUser);
              if (passField != null) passField.setText(adminPassword);
              clickButton(dialog, "OK");
              handledDialogs.incrementAndGet();
            });
      }
      try {
        Thread.sleep(DIALOG_POLL_MS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static QuasselCoreControlPort.QuasselCoreNetworkCreateRequest promptAddNetworkViaUi(
      SwingUiPort ui,
      String serverId,
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks,
      String networkName,
      String ircHost,
      int ircPort)
      throws Exception {
    AtomicReference<Optional<cafe.woden.ircclient.app.api.QuasselNetworkManagerAction>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselNetworkManagerAction(serverId, networks));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-network-manager-add-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog manager = waitForDialog("Quassel Network Manager - " + serverId, UI_DIALOG_TIMEOUT);
    onEdt(() -> clickButton(manager, "Add..."));

    JDialog addDialog = waitForDialog("Add Quassel Network", UI_DIALOG_TIMEOUT);
    onEdt(
        () -> {
          JTextField nameField =
              findComponentNextToLabel(addDialog, "Network name", JTextField.class, c -> true);
          JTextField hostField =
              findComponentNextToLabel(addDialog, "Server host", JTextField.class, c -> true);
          JTextField portField =
              findComponentNextToLabel(addDialog, "Server port", JTextField.class, c -> true);
          if (nameField != null) nameField.setText(networkName);
          if (hostField != null) hostField.setText(ircHost);
          if (portField != null) portField.setText(Integer.toString(ircPort));
          clickButton(addDialog, "OK");
        });

    joinCaller(caller, error, Duration.ofSeconds(20));
    Optional<cafe.woden.ircclient.app.api.QuasselNetworkManagerAction> action = result.get();
    if (action.isEmpty()
        || action.orElseThrow().operation()
            != cafe.woden.ircclient.app.api.QuasselNetworkManagerAction.Operation.ADD
        || action.orElseThrow().createRequest() == null) {
      throw new AssertionError("Expected Quassel network ADD action from UI");
    }
    return action.orElseThrow().createRequest();
  }

  private static String promptConnectNetworkViaUi(
      SwingUiPort ui,
      String serverId,
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks)
      throws Exception {
    AtomicReference<Optional<cafe.woden.ircclient.app.api.QuasselNetworkManagerAction>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselNetworkManagerAction(serverId, networks));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-network-manager-connect-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog manager = waitForDialog("Quassel Network Manager - " + serverId, UI_DIALOG_TIMEOUT);
    onEdt(() -> clickButton(manager, "Connect"));

    joinCaller(caller, error, Duration.ofSeconds(20));
    Optional<cafe.woden.ircclient.app.api.QuasselNetworkManagerAction> action = result.get();
    if (action.isEmpty()
        || action.orElseThrow().operation()
            != cafe.woden.ircclient.app.api.QuasselNetworkManagerAction.Operation.CONNECT) {
      throw new AssertionError("Expected Quassel network CONNECT action from UI");
    }
    String token = Objects.toString(action.orElseThrow().networkIdOrName(), "").trim();
    if (token.isEmpty()) {
      throw new AssertionError("Expected non-blank network token/id from UI connect action");
    }
    return token;
  }

  private static JDialog waitForDialog(String title, Duration timeout) throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      JDialog dialog = findVisibleDialog(title);
      if (dialog != null) return dialog;
      Thread.sleep(DIALOG_POLL_MS);
    }
    throw new AssertionError("Timed out waiting for dialog: " + title);
  }

  private static void joinCaller(Thread caller, AtomicReference<Throwable> error, Duration timeout)
      throws Exception {
    caller.join(timeout);
    if (caller.isAlive()) {
      throw new AssertionError("Dialog caller timed out");
    }
    Throwable err = error.get();
    if (err != null) {
      throw new AssertionError("Dialog caller failed", err);
    }
  }

  private static QuasselCoreControlPort.QuasselCoreNetworkSummary awaitNetworkObserved(
      QuasselCoreIrcClientService service, String serverId, String networkName, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        if (!networkName.equalsIgnoreCase(Objects.toString(summary.networkName(), "").trim())) {
          continue;
        }
        return summary;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError("Timed out waiting to observe Quassel network '" + networkName + "'");
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
    throw new AssertionError("Timed out waiting for Quassel network " + networkId + " to connect");
  }

  private static JDialog findVisibleDialog(String title) {
    for (Window window : Window.getWindows()) {
      if (!(window instanceof JDialog dialog)) continue;
      if (!dialog.isShowing()) continue;
      if (title.equals(dialog.getTitle())) return dialog;
    }
    return null;
  }

  private static <T extends Component> T findComponentNextToLabel(
      Component root, String labelText, Class<T> type, java.util.function.Predicate<T> predicate) {
    if (!(root instanceof Container container)) return null;
    Component[] components = container.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      if (component instanceof JLabel label && labelText.equals(label.getText())) {
        if (i + 1 >= components.length) continue;
        Component candidate = components[i + 1];
        if (!type.isInstance(candidate)) continue;
        T casted = type.cast(candidate);
        if (predicate == null || predicate.test(casted)) {
          return casted;
        }
      }
    }
    for (Component child : components) {
      T found = findComponentNextToLabel(child, labelText, type, predicate);
      if (found != null) return found;
    }
    return null;
  }

  private static void clickButton(Container root, String text) {
    JButton button = findButton(root, text);
    if (button == null) {
      throw new AssertionError("Could not find button: " + text);
    }
    button.doClick();
  }

  private static JButton findButton(Component root, String text) {
    if (root instanceof JButton button && text.equals(button.getText())) return button;
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButton(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(Runnable action) {
    if (SwingUtilities.isEventDispatchThread()) {
      action.run();
      return;
    }
    try {
      SwingUtilities.invokeAndWait(action);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
      if (seen > alreadySeenCount) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError(
        "Timed out waiting for ChannelMessage from "
            + fromNick
            + " in "
            + channel
            + " containing '"
            + expectedTextPart
            + "'");
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
        "Timed out waiting for " + eventType.getSimpleName() + " on server '" + serverId + "'");
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

  private static String firstNonBlank(List<String> values, String fallback) {
    if (values != null) {
      for (String value : values) {
        String candidate = Objects.toString(value, "").trim();
        if (!candidate.isEmpty()) return candidate;
      }
    }
    return Objects.toString(fallback, "").trim();
  }

  private static QuasselCoreIrcClientService newService(
      RuntimeCoreConfig cfg, ServerCatalog serverCatalog) {
    ServerProxyResolver proxyResolver = new ServerProxyResolver(serverCatalog);
    QuasselCoreSocketConnector socketConnector = new QuasselCoreSocketConnector(proxyResolver);
    QuasselCoreProtocolProbe protocolProbe = new QuasselCoreProtocolProbe();
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake authHandshake = new QuasselCoreAuthHandshake(datastreamCodec);

    IrcProperties.Server server = cfg.toServer();
    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe Functional IT",
                new IrcProperties.Reconnect(true, 250, 1_000, 1.5, 0, 8),
                null,
                null,
                null),
            List.of(server));
    return new QuasselCoreIrcClientService(
        serverCatalog, socketConnector, protocolProbe, authHandshake, datastreamCodec, props);
  }

  private record RuntimeCoreConfig(
      String serverId,
      String host,
      int port,
      boolean tls,
      String login,
      String password,
      String nick,
      String realName) {
    IrcProperties.Server toServer() {
      return new IrcProperties.Server(
          serverId,
          host,
          port,
          tls,
          password,
          nick,
          login,
          realName,
          null,
          null,
          List.of(),
          List.of(),
          new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000),
          IrcProperties.Server.Backend.QUASSEL_CORE);
    }
  }

  private record NetworkUiConfig(
      boolean enabled,
      String quasselImage,
      String ircImage,
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
    private static final String DEFAULT_SERVER_ID = "quassel-ui-network-functional-it";
    private static final String DEFAULT_LOGIN = "ircafe-ui-net-it";
    private static final String DEFAULT_PASSWORD = "ircafe-ui-net-it-password";
    private static final String DEFAULT_REAL_NAME = "IRCafe UI Network Functional IT";
    private static final String DEFAULT_IRC_ALIAS = "irc-ui-e2e";
    private static final String DEFAULT_BOT_NICK = "uinetbot";
    private static final String DEFAULT_CHANNEL = "#ui-quassel-e2e";
    private static final String DEFAULT_MESSAGE = "hello-from-ui-net-bot";

    static NetworkUiConfig fromSystem() {
      boolean enabled =
          readBoolean(
                  "quassel.it.container.ui.network.functional.enabled",
                  "QUASSEL_IT_CONTAINER_UI_NETWORK_FUNCTIONAL_ENABLED",
                  false)
              || readBoolean(
                  "quassel.it.container.e2e.enabled", "QUASSEL_IT_CONTAINER_E2E_ENABLED", false);
      String quasselImage =
          readString(
              "quassel.it.container.image", "QUASSEL_IT_CONTAINER_IMAGE", DEFAULT_QUASSEL_IMAGE);
      String ircImage =
          readString(
              "quassel.it.container.e2e.irc-image",
              "QUASSEL_IT_CONTAINER_E2E_IRC_IMAGE",
              DEFAULT_IRC_IMAGE);
      String serverId =
          readString(
              "quassel.it.container.server-id",
              "QUASSEL_IT_CONTAINER_SERVER_ID",
              DEFAULT_SERVER_ID);
      String login =
          readString("quassel.it.container.login", "QUASSEL_IT_CONTAINER_LOGIN", DEFAULT_LOGIN);
      String password =
          readString(
              "quassel.it.container.password", "QUASSEL_IT_CONTAINER_PASSWORD", DEFAULT_PASSWORD);
      String nick =
          readString("quassel.it.container.nick", "QUASSEL_IT_CONTAINER_NICK", DEFAULT_LOGIN);
      String realName =
          readString(
              "quassel.it.container.real-name",
              "QUASSEL_IT_CONTAINER_REAL_NAME",
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

      return new NetworkUiConfig(
          enabled,
          safeTrim(quasselImage, DEFAULT_QUASSEL_IMAGE),
          safeTrim(ircImage, DEFAULT_IRC_IMAGE),
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(login, DEFAULT_LOGIN),
          Objects.toString(password, DEFAULT_PASSWORD),
          safeTrim(nick, DEFAULT_LOGIN),
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
          serverId, host, mappedPort, false, login, password, nick, realName);
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
        return in.readLine();
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

  private record ContainerConfig(
      boolean enabled,
      String image,
      int containerPort,
      String serverId,
      String login,
      String password,
      String nick,
      String realName) {
    private static final String DEFAULT_IMAGE = "linuxserver/quassel-core:0.14.0";
    private static final int DEFAULT_CONTAINER_PORT = 4242;
    private static final String DEFAULT_SERVER_ID = "quassel-ui-functional-it";
    private static final String DEFAULT_LOGIN = "ircafe-ui-it";
    private static final String DEFAULT_PASSWORD = "ircafe-ui-it-password";
    private static final String DEFAULT_REAL_NAME = "IRCafe UI Functional IT";

    static ContainerConfig fromSystem() {
      boolean enabled =
          readBoolean(
                  "quassel.it.container.ui.functional.enabled",
                  "QUASSEL_IT_CONTAINER_UI_FUNCTIONAL_ENABLED",
                  false)
              || readBoolean("quassel.it.container.enabled", "QUASSEL_IT_CONTAINER_ENABLED", false);
      String image =
          readString("quassel.it.container.image", "QUASSEL_IT_CONTAINER_IMAGE", DEFAULT_IMAGE);
      int containerPort =
          readInt("quassel.it.container.port", "QUASSEL_IT_CONTAINER_PORT", DEFAULT_CONTAINER_PORT);
      String serverId =
          readString(
              "quassel.it.container.server-id",
              "QUASSEL_IT_CONTAINER_SERVER_ID",
              DEFAULT_SERVER_ID);
      String login =
          readString("quassel.it.container.login", "QUASSEL_IT_CONTAINER_LOGIN", DEFAULT_LOGIN);
      String password =
          readString(
              "quassel.it.container.password", "QUASSEL_IT_CONTAINER_PASSWORD", DEFAULT_PASSWORD);
      String nick = readString("quassel.it.container.nick", "QUASSEL_IT_CONTAINER_NICK", login);
      String realName =
          readString(
              "quassel.it.container.real-name",
              "QUASSEL_IT_CONTAINER_REAL_NAME",
              DEFAULT_REAL_NAME);

      return new ContainerConfig(
          enabled,
          safeTrim(image, DEFAULT_IMAGE),
          containerPort,
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(login, DEFAULT_LOGIN),
          Objects.toString(password, DEFAULT_PASSWORD),
          safeTrim(nick, DEFAULT_LOGIN),
          safeTrim(realName, DEFAULT_REAL_NAME));
    }

    RuntimeCoreConfig toRuntimeConfig(String host, int mappedPort) {
      return new RuntimeCoreConfig(
          serverId, host, mappedPort, false, login, password, nick, realName);
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

    private static int readInt(String propName, String envName, int fallback) {
      String raw = readString(propName, envName, Integer.toString(fallback)).trim();
      if (raw.isEmpty()) return fallback;
      try {
        return Integer.parseInt(raw);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException(
            "Invalid integer for " + propName + "/" + envName + ": '" + raw + "'", nfe);
      }
    }

    private static String safeTrim(String value, String fallback) {
      String trimmed = Objects.toString(value, "").trim();
      return trimmed.isEmpty() ? fallback : trimmed;
    }
  }
}
