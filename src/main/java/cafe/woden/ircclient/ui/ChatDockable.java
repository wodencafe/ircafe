package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.DccTransferStore;
import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.SpringRuntimeEventsService;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.ui.application.JfrDiagnosticsPanel;
import cafe.woden.ircclient.ui.application.RuntimeEventsPanel;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.dcc.DccTransfersPanel;
import cafe.woden.ircclient.ui.interceptors.InterceptorPanel;
import cafe.woden.ircclient.ui.logviewer.LogViewerPanel;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import cafe.woden.ircclient.ui.notifications.NotificationsPanel;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The main chat dockable.
 *
 * <p>Displays a single transcript at a time (selected via the server tree), but keeps per-target
 * scroll state so switching targets feels natural.
 *
 * <p>Transcripts themselves live in {@link ChatTranscriptStore} so other views (e.g., pinned chat
 * docks) can share them.
 */
@Component
@Lazy
public class ChatDockable extends ChatViewPanel implements Dockable {

  private static final Logger log = LoggerFactory.getLogger(ChatDockable.class);

  // Diagnostics: avoid spamming logs if the server doesn't support typing.
  private final java.util.concurrent.atomic.AtomicBoolean typingUnavailableWarned =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  public static final String ID = "chat";
  private static final long READ_MARKER_SEND_COOLDOWN_MS = 3000L;

  private final ChatTranscriptStore transcripts;
  private final ServerTreeDockable serverTree;

  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;
  private final IrcClientService irc;

  private final ChatHistoryService chatHistoryService;

  private final ActiveInputRouter activeInputRouter;

  private final MessageInputPanel inputPanel;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;
  private final MonitorListService monitorListService;
  private final UserListStore userListStore;
  private final UserListDockable usersDock;

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final NickContextMenuFactory.NickContextMenu nickContextMenu;

  private final ServerProxyResolver proxyResolver;

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();
  private final FlowableProcessor<TopicUpdate> topicUpdates =
      PublishProcessor.<TopicUpdate>create().toSerialized();
  private final FlowableProcessor<String> channelListCommandRequests =
      PublishProcessor.<String>create().toSerialized();

  private final Map<TargetRef, ViewState> stateByTarget = new HashMap<>();
  private final ViewState fallbackState = new ViewState();

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();
  private final Map<TargetRef, String> draftByTarget = new HashMap<>();
  private final Map<TargetRef, Long> lastReadMarkerSentAtByTarget = new HashMap<>();
  private final Map<String, Map<String, Boolean>> privateMessageOnlineByServer = new HashMap<>();
  private final Map<String, Map<String, ArrayList<BanListEntry>>> banListEntriesByServer =
      new HashMap<>();
  private final Map<String, Map<String, String>> banListSummaryByServer = new HashMap<>();

  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;

  // Center content swaps between transcript and UI-only per-server views.
  private static final String CARD_TRANSCRIPT = "transcript";
  private static final String CARD_NOTIFICATIONS = "notifications";
  private static final String CARD_CHANNEL_LIST = "channel-list";
  private static final String CARD_DCC_TRANSFERS = "dcc-transfers";
  private static final String CARD_MONITOR = "monitor";
  private static final String CARD_LOG_VIEWER = "log-viewer";
  private static final String CARD_INTERCEPTOR = "interceptor";
  private static final String CARD_APP_JFR = "app-jfr";
  private static final String CARD_APP_SPRING = "app-spring";
  private static final String CARD_TERMINAL = "terminal";
  private final JPanel centerCards = new JPanel(new CardLayout());
  private final NotificationsPanel notificationsPanel;
  private final ChannelListPanel channelListPanel = new ChannelListPanel();
  private final DccTransfersPanel dccTransfersPanel;
  private final MonitorPanel monitorPanel = new MonitorPanel();
  private final LogViewerPanel logViewerPanel;
  private final InterceptorStore interceptorStore;
  private final InterceptorPanel interceptorPanel;
  private final JfrDiagnosticsPanel appJfrPanel;
  private final RuntimeEventsPanel appSpringPanel;
  private final TerminalDockable terminalPanel;

  private static final int TOPIC_DIVIDER_SIZE = 6;
  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;

  private TargetRef activeTarget;

  /** Channel topic update for pinned docks and other secondary views. */
  public record TopicUpdate(TargetRef target, String topic) {}

  private record BanListEntry(String mask, String setBy, Long setAtEpochSeconds) {}

  public ChatDockable(
      ChatTranscriptStore transcripts,
      ServerTreeDockable serverTree,
      NotificationStore notificationStore,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      IrcClientService irc,
      ActiveInputRouter activeInputRouter,
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      MonitorListService monitorListService,
      UserListStore userListStore,
      UserListDockable usersDock,
      NickContextMenuFactory nickContextMenuFactory,
      ServerProxyResolver proxyResolver,
      ChatHistoryService chatHistoryService,
      ChatLogViewerService chatLogViewerService,
      InterceptorStore interceptorStore,
      DccTransferStore dccTransferStore,
      TerminalDockable terminalDockable,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      SpringRuntimeEventsService springRuntimeEventsService,
      UiSettingsBus settingsBus,
      SpellcheckSettingsBus spellcheckSettingsBus,
      CommandHistoryStore commandHistoryStore) {
    super(settingsBus);
    this.transcripts = transcripts;
    this.serverTree = serverTree;

    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.irc = irc;
    this.activeInputRouter = activeInputRouter;
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
    this.monitorListService =
        java.util.Objects.requireNonNull(monitorListService, "monitorListService");
    this.userListStore = java.util.Objects.requireNonNull(userListStore, "userListStore");
    this.usersDock = java.util.Objects.requireNonNull(usersDock, "usersDock");
    this.proxyResolver = proxyResolver;
    this.chatHistoryService = chatHistoryService;
    this.interceptorStore = java.util.Objects.requireNonNull(interceptorStore, "interceptorStore");
    this.terminalPanel = java.util.Objects.requireNonNull(terminalDockable, "terminalDockable");
    this.appJfrPanel = new JfrDiagnosticsPanel(jfrRuntimeEventsService);
    this.appSpringPanel =
        new RuntimeEventsPanel(
            "Spring Events",
            "Application lifecycle, availability, and framework events.",
            () ->
                springRuntimeEventsService != null
                    ? springRuntimeEventsService.recentEvents(600)
                    : List.of());

    this.nickContextMenu =
        nickContextMenuFactory.create(
            new NickContextMenuFactory.Callbacks() {
              @Override
              public void openQuery(TargetRef ctx, String nick) {
                if (ctx == null) return;
                if (nick == null || nick.isBlank()) return;
                openPrivate.onNext(new PrivateMessageRequest(ctx.serverId(), nick));
              }

              @Override
              public void emitUserAction(
                  TargetRef ctx, String nick, UserActionRequest.Action action) {
                if (ctx == null) return;
                if (nick == null || nick.isBlank()) return;
                if (action == null) return;
                userActions.onNext(new UserActionRequest(ctx, nick, action));
              }

              @Override
              public void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft) {
                // Qualify to avoid resolving to the callback method itself.
                ChatDockable.this.promptIgnore(ctx, nick, findNickInfo(ctx, nick), removing, soft);
              }

              @Override
              public void requestDccAction(
                  TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
                ChatDockable.this.requestDccAction(ctx, nick, action);
              }
            });

    // Show something harmless on startup; first selection will swap it.
    setDocument(new DefaultStyledDocument());

    // Insert an optional topic panel above the transcript.
    // We use a vertical split so the user can shrink/expand the topic area.
    remove(scroll);
    topicSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topicPanel, scroll);
    topicSplit.setResizeWeight(0.0);
    topicSplit.setBorder(null);
    topicSplit.setOneTouchExpandable(true);
    topicPanel.setMinimumSize(new Dimension(0, 0));
    topicPanel.setPreferredSize(new Dimension(10, lastTopicHeightPx));

    // Track the user's preferred topic height when the panel is visible.
    topicSplit.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          if (!topicVisible) return;
          Object v = evt.getNewValue();
          if (v instanceof Integer i) {
            // Divider location == height of the top component for VERTICAL_SPLIT.
            lastTopicHeightPx = Math.max(0, i);
          }
        });

    // Notifications panel is a UI-only target view (selected from the server tree).
    this.notificationsPanel =
        new NotificationsPanel(
            java.util.Objects.requireNonNull(notificationStore, "notificationStore"));
    this.notificationsPanel.setOnSelectTarget(
        ref -> {
          // Clickable channel names in the notifications list should navigate like a tree
          // selection.
          if (ChatDockable.this.serverTree != null) {
            ChatDockable.this.serverTree.selectTarget(ref);
          }
        });
    this.channelListPanel.setOnJoinChannel(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestJoinChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnRunListRequest(
        () -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          channelListCommandRequests.onNext("/list");
        });
    this.channelListPanel.setOnRunAlisRequest(
        command -> {
          String sid = channelListServerIdForActions();
          String cmd = Objects.toString(command, "").trim();
          if (sid.isBlank() || cmd.isEmpty()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          channelListCommandRequests.onNext(cmd);
        });
    this.channelListPanel.setOnAddChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          TargetRef ref = new TargetRef(sid, ch);
          serverTree.ensureNode(ref);
          serverTree.setChannelAutoReattach(ref, true);
          serverTree.requestJoinChannel(ref);
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnAttachChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestJoinChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnDetachChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestDetachChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnCloseChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestCloseChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnAutoReattachChanged(
        (channel, autoReattach) -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.setChannelAutoReattach(new TargetRef(sid, ch), autoReattach);
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnManagedSortModeChanged(
        mode -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          ServerTreeDockable.ChannelSortMode mapped =
              mode == ChannelListPanel.ManagedSortMode.ALPHABETICAL
                  ? ServerTreeDockable.ChannelSortMode.ALPHABETICAL
                  : ServerTreeDockable.ChannelSortMode.CUSTOM;
          serverTree.setChannelSortModeForServer(sid, mapped);
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnManagedCustomOrderChanged(
        order -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          serverTree.setChannelCustomOrderForServer(sid, order);
          refreshManagedChannelsCard(sid);
        });
    this.channelListPanel.setOnManagedChannelSelected(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          updateUsersDockForChannel(sid, ch);
        });
    this.channelListPanel.setOnChannelTopicRequest(
        (serverId, channel) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return "";
          return topicFor(new TargetRef(sid, ch));
        });
    this.channelListPanel.setOnChannelBanListSnapshotRequest(
        (serverId, channel) -> channelBanListSnapshot(serverId, channel));
    this.channelListPanel.setOnChannelBanListRefreshRequest(
        (serverId, channel) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          outboundBus.emit("/mode " + ch + " +b");
        });
    disposables.add(
        channelListCommandRequests
            .debounce(150, TimeUnit.MILLISECONDS)
            .throttleFirst(5, TimeUnit.SECONDS)
            .subscribe(
                outboundBus::emit,
                err -> log.debug("[ircafe] channel-list command flow failed", err)));
    disposables.add(
        serverTree
            .managedChannelsChangedByServer()
            .subscribe(
                sid -> {
                  String changed = Objects.toString(sid, "").trim();
                  if (changed.isEmpty()) return;
                  if (!changed.equalsIgnoreCase(channelListPanel.currentServerId())) return;
                  refreshManagedChannelsCard(changed);
                },
                err -> log.debug("[ircafe] managed-channel refresh stream failed", err)));
    this.dccTransfersPanel =
        new DccTransfersPanel(
            java.util.Objects.requireNonNull(dccTransferStore, "dccTransferStore"));
    this.dccTransfersPanel.setOnEmitCommand(
        line -> {
          String cmd = Objects.toString(line, "").trim();
          TargetRef t = activeTarget;
          if (cmd.isEmpty() || t == null || !t.isDccTransfers()) return;
          activationBus.activate(t);
          armTailPinOnNextAppendIfAtBottom();
          outboundBus.emit(cmd);
        });
    this.monitorPanel.setOnEmitCommand(
        line -> {
          String cmd = Objects.toString(line, "").trim();
          TargetRef t = activeTarget;
          if (cmd.isEmpty() || t == null || !t.isMonitorGroup()) return;
          activationBus.activate(t);
          // Ensure activation has a chance to propagate before command handling.
          SwingUtilities.invokeLater(() -> outboundBus.emit(cmd));
        });
    this.logViewerPanel =
        new LogViewerPanel(
            java.util.Objects.requireNonNull(chatLogViewerService, "chatLogViewerService"),
            sid ->
                (this.serverTree == null)
                    ? java.util.List.of()
                    : this.serverTree.openChannelsForServer(sid));
    this.interceptorPanel = new InterceptorPanel(this.interceptorStore);
    this.interceptorPanel.setOnSelectTarget(
        ref -> {
          if (ref == null) return;
          if (ChatDockable.this.serverTree != null) {
            ChatDockable.this.serverTree.selectTarget(ref);
          } else {
            ChatDockable.this.setActiveTarget(ref);
          }
        });

    centerCards.add(topicSplit, CARD_TRANSCRIPT);
    centerCards.add(notificationsPanel, CARD_NOTIFICATIONS);
    centerCards.add(channelListPanel, CARD_CHANNEL_LIST);
    centerCards.add(dccTransfersPanel, CARD_DCC_TRANSFERS);
    centerCards.add(monitorPanel, CARD_MONITOR);
    centerCards.add(logViewerPanel, CARD_LOG_VIEWER);
    centerCards.add(interceptorPanel, CARD_INTERCEPTOR);
    centerCards.add(appJfrPanel, CARD_APP_JFR);
    centerCards.add(appSpringPanel, CARD_APP_SPRING);
    centerCards.add(terminalPanel, CARD_TERMINAL);
    add(centerCards, BorderLayout.CENTER);
    showTranscriptCard();
    hideTopicPanel();

    // Input panel is embedded in the main chat dock so input is always coupled with the transcript.
    this.inputPanel =
        new MessageInputPanel(settingsBus, commandHistoryStore, spellcheckSettingsBus);
    add(inputPanel, BorderLayout.SOUTH);
    // Context menu: Clear (buffer only) + Reload recent history (clear + reload from DB/bouncer).
    setTranscriptContextMenuActions(
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            transcripts.clearTarget(t);
          } catch (Exception ignored) {
          }
        },
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            if (chatHistoryService != null && chatHistoryService.canReloadRecent(t)) {
              chatHistoryService.reloadRecent(t);
            }
          } catch (Exception ignored) {
          }
        });
    if (this.activeInputRouter != null) {
      // Default active typing surface is the main chat input.
      this.activeInputRouter.activate(inputPanel);
      inputPanel.setOnActivated(
          () -> {
            this.activeInputRouter.activate(inputPanel);
            if (activeTarget != null) {
              activationBus.activate(activeTarget);
            }
          });
    }
    inputPanel.setOnTypingStateChanged(this::onLocalTypingStateChanged);
    disposables.add(
        inputPanel
            .outboundMessages()
            .subscribe(
                line -> {
                  armTailPinOnNextAppendIfAtBottom();
                  outboundBus.emit(line);
                },
                err -> {
                  // Never crash the UI because an outbound subscriber failed.
                }));

    // Keep an initial view state so the first auto-scroll behaves.
    this.activeTarget = new TargetRef("default", "status");
    stateByTarget.put(activeTarget, new ViewState());
    updateDockTitle();

    disposables.add(
        this.interceptorStore
            .changes()
            .subscribe(
                ch -> {
                  TargetRef at = activeTarget;
                  if (at == null || !at.isInterceptor()) return;
                  if (!Objects.equals(at.serverId(), ch.serverId())) return;
                  if (!Objects.equals(at.interceptorId(), ch.interceptorId())) return;
                  SwingUtilities.invokeLater(
                      () -> {
                        updateDockTitle();
                        interceptorPanel.setInterceptorTarget(at.serverId(), at.interceptorId());
                      });
                },
                err -> {
                  // Keep chat UI usable even if interceptor updates fail.
                }));

    disposables.add(
        this.monitorListService
            .changes()
            .subscribe(
                ch -> {
                  if (ch == null) return;
                  TargetRef at = activeTarget;
                  if (at == null || !at.isMonitorGroup()) return;
                  if (!Objects.equals(at.serverId(), ch.serverId())) return;
                  SwingUtilities.invokeLater(() -> refreshMonitorRows(ch.serverId()));
                },
                err -> {
                  // Keep chat UI usable even if monitor updates fail.
                }));
  }

  @Override
  protected ProxyPlan currentProxyPlan() {
    try {
      if (proxyResolver == null) return null;
      if (activeTarget == null) return null;
      return proxyResolver.planForServer(activeTarget.serverId());
    } catch (Exception ignored) {
      return null;
    }
  }

  public void setActiveTarget(TargetRef target) {
    if (target == null) return;
    if (Objects.equals(activeTarget, target)) return;
    boolean leavingInterceptor = activeTarget != null && activeTarget.isInterceptor();

    // Incoming typing indicators are per-target. Clear the shared banner before switching
    // so typing users from the previous buffer do not linger in the next buffer UI.
    inputPanel.clearRemoteTypingIndicator();

    // Persist state + draft of the current target before swapping.
    if (activeTarget != null) {
      inputPanel.flushTypingForBufferSwitch();
      draftByTarget.put(activeTarget, inputPanel.getDraftText());
      updateScrollStateFromBar();
    }

    activeTarget = target;
    updateDockTitle();
    refreshTypingSignalAvailabilityForActiveTarget();
    setInputPanelVisibleForTarget(target);

    if (leavingInterceptor && !target.isInterceptor()) {
      try {
        interceptorPanel.setInterceptorTarget("", "");
      } catch (Exception ignored) {
      }
    }

    // UI-only targets do not have a transcript.
    if (target.isNotifications()) {
      showNotificationsCard(target.serverId());
      // Notifications doesn't accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isChannelList()) {
      showChannelListCard(target.serverId());
      // Channel list doesn't accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isDccTransfers()) {
      showDccTransfersCard(target.serverId());
      // DCC transfers view doesn't accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isMonitorGroup()) {
      showMonitorCard(target.serverId());
      // Monitor view does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isApplicationJfr()) {
      showApplicationJfrCard();
      // Runtime diagnostics view does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isApplicationSpring()) {
      showApplicationSpringCard();
      // Runtime diagnostics view does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isApplicationTerminal()) {
      showTerminalCard();
      // Terminal view does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isLogViewer()) {
      showLogViewerCard(target.serverId());
      // Log viewer does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isInterceptorsGroup()) {
      showInterceptorCard(target.serverId(), "");
      // Interceptors overview does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }
    if (target.isInterceptor()) {
      showInterceptorCard(target.serverId(), target.interceptorId());
      // Interceptor view does not accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }

    showTranscriptCard();
    transcripts.ensureTargetExists(target);
    setDocument(transcripts.document(target));
    int unreadJumpOffset = transcripts.readMarkerJumpOffset(target);

    // Restore any saved draft for this target.
    inputPanel.setDraftText(draftByTarget.getOrDefault(target, ""));

    updateTopicPanelForActiveTarget();

    SwingUtilities.invokeLater(() -> applyReadMarkerViewState(target, unreadJumpOffset));

    // UX: selecting a different buffer should let the user immediately start typing.
    // (No-op when input is disabled.)
    inputPanel.focusInput();
  }

  private void showTranscriptCard() {
    try {
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_TRANSCRIPT);
    } catch (Exception ignored) {
    }
  }

  private void showNotificationsCard(String serverId) {
    try {
      notificationsPanel.setServerId(serverId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_NOTIFICATIONS);
    } catch (Exception ignored) {
    }
  }

  private void showChannelListCard(String serverId) {
    try {
      channelListPanel.setServerId(serverId);
      refreshManagedChannelsCard(serverId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_CHANNEL_LIST);
    } catch (Exception ignored) {
    }
  }

  private void refreshManagedChannelsCard(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || serverTree == null) {
      channelListPanel.setManagedChannels(sid, List.of(), ChannelListPanel.ManagedSortMode.CUSTOM);
      return;
    }

    List<ChannelListPanel.ManagedChannelRow> rows =
        serverTree.managedChannelsForServer(sid).stream()
            .map(
                entry -> {
                  int users = userListStore.get(sid, entry.channel()).size();
                  String modes = modeSummaryForChannel();
                  return new ChannelListPanel.ManagedChannelRow(
                      entry.channel(),
                      entry.detached(),
                      entry.autoReattach(),
                      users,
                      entry.notifications(),
                      modes);
                })
            .toList();
    ChannelListPanel.ManagedSortMode sortMode =
        serverTree.channelSortModeForServer(sid) == ServerTreeDockable.ChannelSortMode.ALPHABETICAL
            ? ChannelListPanel.ManagedSortMode.ALPHABETICAL
            : ChannelListPanel.ManagedSortMode.CUSTOM;
    channelListPanel.setManagedChannels(sid, rows, sortMode);
  }

  private static String modeSummaryForChannel() {
    return "(Unknown)";
  }

  private void updateUsersDockForChannel(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    TargetRef target = new TargetRef(sid, ch);
    usersDock.setChannel(target);
    usersDock.setNicks(userListStore.get(sid, ch));
  }

  private String channelListServerIdForActions() {
    String sid = Objects.toString(channelListPanel.currentServerId(), "").trim();
    if (!sid.isEmpty()) return sid;

    TargetRef t = activeTarget;
    if (t == null) return "";
    return Objects.toString(t.serverId(), "").trim();
  }

  private static String normalizeChannelName(String channel) {
    String c = Objects.toString(channel, "").trim();
    if (c.isEmpty()) return "";
    return (c.startsWith("#") || c.startsWith("&")) ? c : "";
  }

  private void showDccTransfersCard(String serverId) {
    try {
      dccTransfersPanel.setServerId(serverId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_DCC_TRANSFERS);
    } catch (Exception ignored) {
    }
  }

  private void showMonitorCard(String serverId) {
    try {
      String sid = Objects.toString(serverId, "").trim();
      monitorPanel.setServerId(sid);
      refreshMonitorRows(sid);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_MONITOR);
    } catch (Exception ignored) {
    }
  }

  private void showLogViewerCard(String serverId) {
    try {
      logViewerPanel.setServerId(serverId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_LOG_VIEWER);
    } catch (Exception ignored) {
    }
  }

  private void showTerminalCard() {
    try {
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_TERMINAL);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationJfrCard() {
    try {
      appJfrPanel.refreshNow();
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_APP_JFR);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationSpringCard() {
    try {
      appSpringPanel.refreshNow();
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_APP_SPRING);
    } catch (Exception ignored) {
    }
  }

  private void showInterceptorCard(String serverId, String interceptorId) {
    try {
      interceptorPanel.setInterceptorTarget(serverId, interceptorId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_INTERCEPTOR);
    } catch (Exception ignored) {
    }
  }

  private void setInputPanelVisibleForTarget(TargetRef target) {
    boolean visible = target != null && !target.isUiOnly();
    if (inputPanel.isVisible() == visible) return;
    inputPanel.setVisible(visible);
    revalidate();
    repaint();
  }

  /**
   * Enable/disable the embedded input bar.
   *
   * <p>We intentionally preserve any draft text when disabling so the user doesn't lose what they
   * were typing during connect/disconnect transitions.
   */
  public void setInputEnabled(boolean enabled) {
    inputPanel.setInputEnabled(enabled);
    if (!enabled) {
      inputPanel.setTypingSignalAvailable(false);
    } else {
      refreshTypingSignalAvailabilityForActiveTarget();
    }
  }

  /** Update the nick completion list used by the embedded input bar. */
  public void setNickCompletions(java.util.List<String> nicks) {
    inputPanel.setNickCompletions(nicks);
  }

  public void setTopic(TargetRef target, String topic) {
    if (target == null) return;
    if (!target.isChannel()) return;

    String sanitized = sanitizeTopic(topic);
    String normalized = sanitized.isBlank() ? "" : sanitized;
    String before = topicByTarget.getOrDefault(target, "");
    if (Objects.equals(before, normalized)) {
      if (target.equals(activeTarget)) {
        updateTopicPanelForActiveTarget();
      }
      return;
    }

    if (normalized.isBlank()) {
      topicByTarget.remove(target);
    } else {
      topicByTarget.put(target, normalized);
    }

    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget();
    }
    channelListPanel.refreshOpenChannelDetails(target.serverId(), target.target());
    topicUpdates.onNext(new TopicUpdate(target, normalized));
  }

  public void clearTopic(TargetRef target) {
    if (target == null) return;
    String removed = topicByTarget.remove(target);
    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget();
    }
    if (target.isChannel() && removed != null && !removed.isBlank()) {
      channelListPanel.refreshOpenChannelDetails(target.serverId(), target.target());
      topicUpdates.onNext(new TopicUpdate(target, ""));
    }
  }

  public String topicFor(TargetRef target) {
    if (target == null) return "";
    return topicByTarget.getOrDefault(target, "");
  }

  public Flowable<TopicUpdate> topicUpdates() {
    return topicUpdates.onBackpressureLatest();
  }

  public void beginChannelList(String serverId, String banner) {
    channelListPanel.beginList(serverId, banner);
  }

  public void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    channelListPanel.appendEntry(serverId, channel, visibleUsers, topic);
  }

  public void appendChannelListEntries(
      String serverId, List<ChannelListPanel.ListEntryRow> entries) {
    channelListPanel.appendEntries(serverId, entries);
  }

  public void endChannelList(String serverId, String summary) {
    channelListPanel.endList(serverId, summary);
  }

  public void beginChannelBanList(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;

    banListEntriesByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, new ArrayList<>());
    banListSummaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).remove(ch);
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  public void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    String m = Objects.toString(mask, "").trim();
    if (sid.isEmpty() || ch.isEmpty() || m.isEmpty()) return;

    Map<String, ArrayList<BanListEntry>> byChannel =
        banListEntriesByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    ArrayList<BanListEntry> entries = byChannel.computeIfAbsent(ch, __ -> new ArrayList<>());
    entries.add(
        new BanListEntry(
            m,
            Objects.toString(setBy, "").trim(),
            setAtEpochSeconds != null && setAtEpochSeconds.longValue() > 0L
                ? setAtEpochSeconds
                : null));
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  public void endChannelBanList(String serverId, String channel, String summary) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    String text = Objects.toString(summary, "").trim();
    if (!text.isEmpty()) {
      banListSummaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, text);
    }
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  private List<String> channelBanListSnapshot(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return List.of();

    Map<String, ArrayList<BanListEntry>> byChannel = banListEntriesByServer.get(sid);
    ArrayList<BanListEntry> entries = byChannel == null ? null : byChannel.get(ch);
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }

    ArrayList<String> out = new ArrayList<>(entries.size() + 1);
    for (BanListEntry entry : entries) {
      if (entry == null) continue;
      StringBuilder line = new StringBuilder(entry.mask());
      String by = Objects.toString(entry.setBy(), "").trim();
      if (!by.isEmpty()) line.append("  |  set by ").append(by);
      if (entry.setAtEpochSeconds() != null && entry.setAtEpochSeconds().longValue() > 0L) {
        try {
          line.append("  |  ").append(Instant.ofEpochSecond(entry.setAtEpochSeconds().longValue()));
        } catch (Exception ignored) {
        }
      }
      out.add(line.toString());
    }

    if (out.isEmpty()) {
      return List.of();
    }
    String summary = banListSummaryByServer.getOrDefault(sid, Map.of()).getOrDefault(ch, "").trim();
    if (!summary.isEmpty()) out.add(summary);
    return List.copyOf(out);
  }

  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    String sid = Objects.toString(serverId, "").trim();
    String key = normalizeNickKey(nick);
    if (sid.isEmpty() || key.isEmpty()) return;

    Map<String, Boolean> byNick =
        privateMessageOnlineByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    byNick.put(key, online);

    TargetRef at = activeTarget;
    if (at != null && at.isMonitorGroup() && Objects.equals(at.serverId(), sid)) {
      refreshMonitorRows(sid);
    }
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    privateMessageOnlineByServer.remove(sid);

    TargetRef at = activeTarget;
    if (at != null && at.isMonitorGroup() && Objects.equals(at.serverId(), sid)) {
      refreshMonitorRows(sid);
    }
  }

  private void refreshMonitorRows(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      monitorPanel.setRows(java.util.List.of());
      return;
    }

    java.util.List<String> nicks = monitorListService.listNicks(sid);
    Map<String, Boolean> onlineByNick = privateMessageOnlineByServer.get(sid);

    ArrayList<MonitorPanel.Row> rows = new ArrayList<>(nicks.size());
    for (String nick : nicks) {
      String n = Objects.toString(nick, "").trim();
      if (n.isEmpty()) continue;
      Boolean online = (onlineByNick == null) ? null : onlineByNick.get(normalizeNickKey(n));
      rows.add(new MonitorPanel.Row(n, online));
    }
    monitorPanel.setRows(rows);
  }

  private static String normalizeNickKey(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return "";
    return n.toLowerCase(Locale.ROOT);
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public Flowable<UserActionRequest> userActionRequests() {
    return userActions.onBackpressureBuffer();
  }

  private NickInfo findNickInfo(TargetRef ctx, String nick) {
    if (ctx == null) return null;
    if (nick == null || nick.isBlank()) return null;
    if (!ctx.isChannel()) return null;
    if (userListStore == null) return null;

    try {
      for (NickInfo ni : userListStore.get(ctx.serverId(), ctx.target())) {
        if (ni == null) continue;
        if (ni.nick() == null) continue;
        if (ni.nick().equalsIgnoreCase(nick)) return ni;
      }
    } catch (Exception ignored) {
      // Defensive: userListStore should never throw, but context menus should never crash the UI.
    }

    return null;
  }

  private void promptIgnore(
      TargetRef ctx, String nick, NickInfo ni, boolean removing, boolean soft) {
    if (ignoreListService == null) return;
    if (ctx == null) return;
    String sid = Objects.toString(ctx.serverId(), "").trim();
    if (sid.isEmpty()) return;

    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return;

    String hm = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();
    String seedBase =
        (ignoreStatusService == null) ? n : ignoreStatusService.bestSeedForMask(sid, n, hm);
    String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);

    String title;
    String message;
    if (soft) {
      title = removing ? "Remove soft ignore" : "Soft ignore";
      message =
          removing
              ? "Remove soft ignore for <b>"
                  + escapeHtml(n)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed)
              : "Soft ignore <b>"
                  + escapeHtml(n)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed);
    } else {
      title = removing ? "Remove ignore" : "Ignore";
      message =
          removing
              ? "Remove ignore for <b>"
                  + escapeHtml(n)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed)
              : "Ignore <b>"
                  + escapeHtml(n)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed);
    }

    int res =
        JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            "<html>" + message + "</html>",
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);

    if (res != JOptionPane.OK_OPTION) return;

    boolean changed;
    if (soft) {
      changed =
          removing
              ? ignoreListService.removeSoftMask(sid, seed)
              : ignoreListService.addSoftMask(sid, seed);
    } else {
      changed =
          removing ? ignoreListService.removeMask(sid, seed) : ignoreListService.addMask(sid, seed);
    }

    if (!changed) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Nothing changed — the ignore list already contained that mask.",
          "No change",
          JOptionPane.INFORMATION_MESSAGE);
    }
  }

  private static String escapeHtml(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&':
          sb.append("&amp;");
          break;
        case '<':
          sb.append("&lt;");
          break;
        case '>':
          sb.append("&gt;");
          break;
        case '"':
          sb.append("&quot;");
          break;
        case '\'':
          sb.append("&#39;");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }

  @Override
  protected JPopupMenu nickContextMenuFor(String nick) {
    if (nick == null || nick.isBlank()) return null;
    if (activeTarget == null) return null;

    String sid = Objects.toString(activeTarget.serverId(), "").trim();
    if (sid.isEmpty()) return null;

    String n = nick.trim();
    if (n.isEmpty()) return null;

    NickInfo ni = findNickInfo(activeTarget, n);
    String hm = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();

    IgnoreStatusService.Status st =
        (ignoreStatusService == null)
            ? new IgnoreStatusService.Status(false, false, false, "")
            : ignoreStatusService.status(sid, n, hm);

    return nickContextMenu.forNick(
        activeTarget, n, new NickContextMenuFactory.IgnoreMark(st.hard(), st.soft()));
  }

  @Override
  protected void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
    if (activeTarget == null) return;
    activationBus.activate(activeTarget);
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    inputPanel.focusInput();
  }

  public void showTypingIndicator(TargetRef target, String nick, String state) {
    if (target == null || nick == null || nick.isBlank()) return;
    if (activeTarget == null || !activeTarget.equals(target)) return;
    boolean atBottomBefore = isTranscriptAtBottom();
    if (atBottomBefore) {
      armTailPinOnNextAppendIfAtBottom();
    }
    boolean typingBannerVisibilityChanged = inputPanel.showRemoteTypingIndicator(nick, state);
    repinAfterInputAreaGeometryChange(atBottomBefore, typingBannerVisibilityChanged);
  }

  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    String sid = Objects.toString(serverId, "").trim();
    String cap = Objects.toString(capability, "").trim().toLowerCase(Locale.ROOT);
    if (sid.isEmpty() || cap.isEmpty()) return;

    if ("typing".equals(cap) || "message-tags".equals(cap)) {
      if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
        boolean atBottomBefore = isTranscriptAtBottom();
        if (atBottomBefore) {
          armTailPinOnNextAppendIfAtBottom();
        }
        boolean typingBannerVisibilityChanged = inputPanel.clearRemoteTypingIndicator();
        repinAfterInputAreaGeometryChange(atBottomBefore, typingBannerVisibilityChanged);
        refreshTypingSignalAvailabilityForActiveTarget();
      }
      return;
    }

    if (!"draft/reply".equals(cap) && !"draft/react".equals(cap)) {
      return;
    }

    boolean replySupported = isDraftReplySupportedForServer(sid);
    boolean reactSupported = isDraftReactSupportedForServer(sid);

    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && !activeTarget.isUiOnly()) {
      inputPanel.normalizeIrcv3DraftForCapabilities(replySupported, reactSupported);
    }

    ArrayList<TargetRef> targets = new ArrayList<>(draftByTarget.keySet());
    for (TargetRef t : targets) {
      if (t == null || !Objects.equals(t.serverId(), sid)) continue;
      String before = draftByTarget.getOrDefault(t, "");
      String after =
          MessageInputPanel.normalizeIrcv3DraftForCapabilities(
              before, replySupported, reactSupported);
      if (!Objects.equals(before, after)) {
        draftByTarget.put(t, after);
      }
    }
  }

  private void repinAfterInputAreaGeometryChange(
      boolean atBottomBefore, boolean inputAreaChangedHeight) {
    if (!inputAreaChangedHeight) return;
    if (!atBottomBefore && !isFollowTail()) return;
    SwingUtilities.invokeLater(this::scrollToBottom);
  }

  @Override
  protected boolean replyContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    if (irc == null) return false;
    try {
      return irc.isDraftReplyAvailable(t.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  protected boolean reactContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    if (irc == null) return false;
    try {
      return irc.isDraftReactAvailable(t.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  protected boolean editContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    return isMessageEditSupportedForServer(t.serverId());
  }

  @Override
  protected boolean redactContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    return isMessageRedactionSupportedForServer(t.serverId());
  }

  @Override
  protected boolean loadNewerHistoryContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    return isLoadNewerHistorySupportedForServer(t.serverId());
  }

  @Override
  protected boolean loadAroundMessageContextActionVisible() {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return false;
    return isChatHistorySupportedForServer(t.serverId());
  }

  @Override
  protected void onLoadNewerHistoryRequested() {
    if (!loadNewerHistoryContextActionVisible()) return;
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (isChatHistorySupportedForServer(t.serverId())) {
      emitHistoryCommand(buildChatHistoryLatestCommand());
      return;
    }
    if (chatHistoryService != null && chatHistoryService.canReloadRecent(t)) {
      chatHistoryService.reloadRecent(t);
    }
  }

  @Override
  protected void onLoadContextAroundMessageRequested(String messageId) {
    if (!loadAroundMessageContextActionVisible()) return;
    String line = buildChatHistoryAroundByMsgIdCommand(messageId);
    if (line.isBlank()) return;
    emitHistoryCommand(line);
  }

  @Override
  protected void onReplyToMessageRequested(String messageId) {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (!replyContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeTarget != null) {
      activationBus.activate(activeTarget);
    }
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    inputPanel.beginReplyCompose(t.target(), msgId);
    inputPanel.focusInput();
  }

  @Override
  protected void onReactToMessageRequested(String messageId) {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (!reactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeTarget != null) {
      activationBus.activate(activeTarget);
    }
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    inputPanel.openQuickReactionPicker(t.target(), msgId);
    inputPanel.focusInput();
  }

  @Override
  protected void onEditMessageRequested(String messageId) {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (!editContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeTarget != null) {
      activationBus.activate(activeTarget);
    }
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    inputPanel.setDraftText("/edit " + msgId + " ");
    inputPanel.focusInput();
  }

  @Override
  protected void onRedactMessageRequested(String messageId) {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (!redactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    emitHistoryCommand("/redact " + msgId);
  }

  private void emitHistoryCommand(String line) {
    String cmd = Objects.toString(line, "").trim();
    if (cmd.isEmpty()) return;
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (activeTarget != null) {
      activationBus.activate(activeTarget);
    }
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    armTailPinOnNextAppendIfAtBottom();
    outboundBus.emit(cmd);
  }

  private void requestDccAction(
      TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
    if (ctx == null) return;
    if (action == null) return;

    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return;

    switch (action) {
      case CHAT -> emitDccCommand(ctx, "/dcc chat " + n);
      case ACCEPT_CHAT -> emitDccCommand(ctx, "/dcc accept " + n);
      case GET_FILE -> emitDccCommand(ctx, "/dcc get " + n);
      case CLOSE_CHAT -> emitDccCommand(ctx, "/dcc close " + n);
      case SEND_FILE -> promptAndSendDccFile(ctx, n);
    }
  }

  private void promptAndSendDccFile(TargetRef ctx, String nick) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Send File to " + nick);
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

    int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (result != JFileChooser.APPROVE_OPTION) return;

    java.io.File selected = chooser.getSelectedFile();
    if (selected == null) return;

    String path = Objects.toString(selected.getAbsolutePath(), "").trim();
    if (path.isEmpty()) return;
    if (path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Refusing file path containing newlines.",
          "DCC Send",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    emitDccCommand(ctx, "/dcc send " + nick + " " + path);
  }

  private void emitDccCommand(TargetRef ctx, String line) {
    String sid = Objects.toString(ctx == null ? "" : ctx.serverId(), "").trim();
    String cmd = Objects.toString(line, "").trim();
    if (sid.isEmpty() || cmd.isEmpty()) return;

    activationBus.activate(ctx);
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    armTailPinOnNextAppendIfAtBottom();
    outboundBus.emit(cmd);
  }

  @Override
  protected boolean onNickClicked(String nick) {
    if (activeTarget == null || !activeTarget.isChannel()) return false;
    if (nick == null || nick.isBlank()) return false;

    openPrivate.onNext(new PrivateMessageRequest(activeTarget.serverId(), nick));
    return true;
  }

  @Override
  protected boolean onChannelClicked(String channel) {
    if (activeTarget == null) return false;
    if (channel == null || channel.isBlank()) return false;

    // Ensure the app's "active target" (server context) matches the transcript we clicked in.
    activationBus.activate(activeTarget);

    // Delegate join to the normal command pipeline so config/auto-join behavior stays consistent.
    outboundBus.emit("/join " + channel.trim());
    return true;
  }

  @Override
  protected boolean onMessageReferenceClicked(String messageId) {
    TargetRef t = activeTarget;
    if (t == null || t.isUiOnly()) return false;
    int off = transcripts.messageOffsetById(t, messageId);
    if (off >= 0) {
      setFollowTail(false);
      scrollToTranscriptOffset(off);
      return true;
    }

    if (!loadAroundMessageContextActionVisible()) return false;
    String line = buildChatHistoryAroundByMsgIdCommand(messageId);
    if (line.isBlank()) return false;
    emitHistoryCommand(line);
    return true;
  }

  private void onLocalTypingStateChanged(String state) {
    TargetRef t = activeTarget;
    if (t == null || t.isStatus() || t.isUiOnly()) return;
    if (irc == null) return;
    boolean typingAvailable = false;
    try {
      typingAvailable = irc.isTypingAvailable(t.serverId());
    } catch (Exception ignored) {
    }
    inputPanel.setTypingSignalAvailable(typingAvailable);
    if (!typingAvailable) {
      String s = normalizeTypingState(state);
      // Only warn once per session when the user is actively composing.
      if (!"done".equals(s) && typingUnavailableWarned.compareAndSet(false, true)) {
        String reason = Objects.toString(irc.typingAvailabilityReason(t.serverId()), "").trim();
        if (reason.isEmpty()) reason = "not negotiated / not allowed";
        log.info(
            "[{}] typing indicators are enabled, but unavailable on this server ({})",
            t.serverId(),
            reason);
      }
      return;
    }
    typingUnavailableWarned.set(false);
    String s = normalizeTypingState(state);
    if (s.isEmpty()) return;
    var unused =
        irc.sendTyping(t.serverId(), t.target(), s)
            .subscribe(
                () -> inputPanel.onLocalTypingIndicatorSent(s),
                err -> {
                  if (log.isDebugEnabled()) {
                    log.debug(
                        "[{}] typing send failed (target={} state={}): {}",
                        t.serverId(),
                        t.target(),
                        s,
                        err.toString());
                  }
                });
  }

  private void refreshTypingSignalAvailabilityForActiveTarget() {
    TargetRef t = activeTarget;
    boolean available = false;
    if (t != null && !t.isStatus() && !t.isUiOnly() && irc != null) {
      try {
        available = irc.isTypingAvailable(t.serverId());
      } catch (Exception ignored) {
      }
    }
    inputPanel.setTypingSignalAvailable(available);
  }

  private static String normalizeTypingState(String state) {
    String s = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "";
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  private boolean isDraftReplySupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isDraftReplyAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isDraftReactSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isDraftReactAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageEditSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageEditAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageRedactionSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageRedactionAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isLoadNewerHistorySupportedForServer(String serverId) {
    return isChatHistorySupportedForServer(serverId) || isZncPlaybackSupportedForServer(serverId);
  }

  private boolean isChatHistorySupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isChatHistoryAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isZncPlaybackSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isZncPlaybackAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    TargetRef t = activeTarget;
    if (t == null) return "Chat";
    if (t.isNotifications()) return "Notifications";
    if (t.isChannelList()) return "Channel List";
    if (t.isDccTransfers()) return "DCC Transfers";
    if (t.isMonitorGroup()) return "Monitor";
    if (t.isInterceptorsGroup()) return "Interceptors";
    if (t.isApplicationUnhandledErrors()) return "Unhandled Errors";
    if (t.isApplicationAssertjSwing()) return "AssertJ Swing";
    if (t.isApplicationJhiccup()) return "jHiccup";
    if (t.isApplicationJfr()) return "JFR";
    if (t.isApplicationSpring()) return "Spring";
    if (t.isApplicationTerminal()) return "Terminal";
    if (t.isLogViewer()) return "Log Viewer";
    if (t.isInterceptor()) {
      String name = interceptorStore.interceptorName(t.serverId(), t.interceptorId());
      return (name == null || name.isBlank()) ? "Interceptor" : name;
    }
    if (t.isStatus()) return "Server";
    String name = t.target();
    if (name == null || name.isBlank()) return "Chat";
    return name;
  }

  private void updateDockTitle() {
    try {
      String title = getTabText();
      if (title == null || title.isBlank()) title = "Chat";
      if (!Objects.equals(getName(), title)) {
        setName(title);
      }

      // ModernDocking caches Dockable#getTabText when rendering tab groups.
      // Some versions expose Docking.updateTabText(...) for this, but the single-app
      // facade doesn't always include it. So we defensively update the tab title
      // ourselves when we're inside a JTabbedPane.
      SwingUtilities.invokeLater(this::updateTabTitleIfTabbed);
    } catch (Exception ignored) {
    }
  }

  private void updateTabTitleIfTabbed() {
    try {
      String title = getTabText();
      if (title == null || title.isBlank()) title = "Chat";

      JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
      if (tabs == null) return;

      int idx = tabs.indexOfComponent(this);
      if (idx < 0) {
        // Dockables are sometimes wrapped; locate the tab whose component contains us.
        for (int i = 0; i < tabs.getTabCount(); i++) {
          java.awt.Component c = tabs.getComponentAt(i);
          if (c == null) continue;
          if (c == this || SwingUtilities.isDescendingFrom(this, c)) {
            idx = i;
            break;
          }
        }
      }

      if (idx >= 0 && idx < tabs.getTabCount()) {
        if (!Objects.equals(tabs.getTitleAt(idx), title)) {
          tabs.setTitleAt(idx, title);
        }
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  protected boolean isFollowTail() {
    return state().followTail;
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    ViewState s = state();
    boolean was = s.followTail;
    s.followTail = followTail;
    TargetRef t = activeTarget;
    if (!was && followTail && t != null && !t.isUiOnly()) {
      maybeSendReadMarker(t);
    }
  }

  @Override
  protected int getSavedScrollValue() {
    return state().scrollValue;
  }

  @Override
  protected void setSavedScrollValue(int value) {
    state().scrollValue = value;
  }

  private ViewState state() {
    if (activeTarget == null) return fallbackState;
    return stateByTarget.computeIfAbsent(activeTarget, t -> new ViewState());
  }

  private void updateTopicPanelForActiveTarget() {
    if (activeTarget == null || !activeTarget.isChannel()) {
      topicPanel.setTopic("", "");
      hideTopicPanel();
      return;
    }

    String topic = topicByTarget.getOrDefault(activeTarget, "");
    if (topic == null) topic = "";
    topic = topic.trim();

    if (topic.isEmpty()) {
      topicPanel.setTopic(activeTarget.target(), "");
      hideTopicPanel();
      return;
    }

    topicPanel.setTopic(activeTarget.target(), topic);
    showTopicPanel();
  }

  private void showTopicPanel() {
    topicVisible = true;
    topicPanel.setVisible(true);
    topicSplit.setDividerSize(TOPIC_DIVIDER_SIZE);
    int targetHeight = Math.max(28, Math.min(lastTopicHeightPx, 200));
    topicSplit.setDividerLocation(targetHeight);
    revalidate();
    repaint();
  }

  private void hideTopicPanel() {
    topicVisible = false;
    topicPanel.setVisible(false);
    topicSplit.setDividerSize(0);
    topicSplit.setDividerLocation(0);
    revalidate();
    repaint();
  }

  private static String sanitizeTopic(String topic) {
    if (topic == null) return "";
    // Strip IRC formatting control chars and other low ASCII controls.
    // (Color codes, bold, reset, etc.)
    return topic.replaceAll("[\\x00-\\x1F\\x7F]", "");
  }

  private static class ViewState {
    boolean followTail = true;
    int scrollValue = 0;
  }

  private void applyReadMarkerViewState(TargetRef target, int unreadJumpOffset) {
    if (target == null || target.isUiOnly()) return;
    if (!Objects.equals(activeTarget, target)) return;

    if (unreadJumpOffset >= 0) {
      setFollowTail(false);
      scrollToTranscriptOffset(unreadJumpOffset);
      updateScrollStateFromBar();
      return;
    }

    if (isTranscriptAtBottom()) {
      maybeSendReadMarker(target);
    }
  }

  private void maybeSendReadMarker(TargetRef target) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (irc == null || !irc.isReadMarkerAvailable(target.serverId())) return;

    long now = System.currentTimeMillis();
    Long last = lastReadMarkerSentAtByTarget.get(target);
    if (last != null && (now - last) < READ_MARKER_SEND_COOLDOWN_MS) return;
    lastReadMarkerSentAtByTarget.put(target, now);

    transcripts.updateReadMarker(target, now);
    var unused =
        irc.sendReadMarker(target.serverId(), target.target(), Instant.ofEpochMilli(now))
            .subscribe(() -> {}, err -> {});
  }

  private static final class TopicPanel extends JPanel {
    private final JLabel header = new JLabel();
    private final JTextArea text = new JTextArea();

    private TopicPanel() {
      super(new BorderLayout(8, 6));

      header.setFont(header.getFont().deriveFont(Font.BOLD));

      text.setEditable(false);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      text.setOpaque(false);
      text.setBorder(null);

      JPanel top = new JPanel(new BorderLayout());
      top.setOpaque(false);
      top.add(header, BorderLayout.WEST);

      setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
      setOpaque(true);

      add(top, BorderLayout.NORTH);
      add(text, BorderLayout.CENTER);
    }

    void setTopic(String channelName, String topic) {
      String ch = (channelName == null) ? "" : channelName.trim();
      header.setText(ch.isEmpty() ? "Topic" : "Topic — " + ch);
      text.setText(topic == null ? "" : topic);
      text.setCaretPosition(0);
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      inputPanel.flushTypingDone();
    } catch (Exception ignored) {
    }
    try {
      disposables.dispose();
    } catch (Exception ignored) {
    }
    try {
      notificationsPanel.close();
    } catch (Exception ignored) {
    }
    try {
      dccTransfersPanel.close();
    } catch (Exception ignored) {
    }
    try {
      logViewerPanel.close();
    } catch (Exception ignored) {
    }
    try {
      interceptorPanel.close();
    } catch (Exception ignored) {
    }
    // Ensure decorator listeners/subscriptions are removed when Spring disposes this dock.
    closeDecorators();
  }
}
