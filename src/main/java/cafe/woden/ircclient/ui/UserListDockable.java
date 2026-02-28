package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.coordinator.DccActionCoordinator;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.userlist.UserListIgnorePromptHandler;
import cafe.woden.ircclient.ui.userlist.UserListNickCellRenderer;
import cafe.woden.ircclient.ui.userlist.UserListNickTooltipBuilder;
import cafe.woden.ircclient.ui.userlist.UserListTypingIndicators;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.ListContextMenuDecorator;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class UserListDockable extends JPanel implements Dockable, Scrollable {
  public static final String ID = "users";

  private static final int TYPING_TICK_MS = 100;

  private final DefaultListModel<NickInfo> model = new DefaultListModel<>();
  private final JList<NickInfo> list =
      new JList<>(model) {

        @Override
        public Dimension getMinimumSize() {
          Dimension d = super.getMinimumSize();
          int h = (d == null) ? 0 : Math.max(0, d.height);
          // Allow the users dock to collapse narrower without the list enforcing a wide minimum.
          return new Dimension(0, h);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
          return true;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
          if (e == null) return null;

          int index = locationToIndex(e.getPoint());
          if (index < 0) return null;

          Rectangle r = getCellBounds(index, index);
          if (r == null || !r.contains(e.getPoint())) return null;

          NickInfo ni = model.getElementAt(index);
          if (ni == null) return null;

          UserListNickCellRenderer.IgnoreMark mark = ignoreMark(ni);
          return tooltipBuilder.build(ni, mark.ignore(), mark.softIgnore());
        }
      };

  /**
   * Lifetime-managed resources for this dockable.
   *
   * <p>Important: Dockables can be closed/hidden and later re-opened. We therefore must not
   * auto-dispose listeners/subscriptions just because the component becomes temporarily
   * non-displayable (that would permanently break behaviors like right-click menus and double-click
   * actions after reopening the dock).
   */
  private final CloseableScope closeables = new CloseableScope();

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final NickColorSettingsBus nickColorSettingsBus;

  private final PropertyChangeListener nickColorSettingsListener = this::onNickColorSettingsChanged;

  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;

  private final IgnoreListService ignoreListService;

  private final IgnoreStatusService ignoreStatusService;
  private final NickContextMenuFactory.NickContextMenu nickContextMenu;
  private final JScrollPane scroll;
  private final DccActionCoordinator dccActionCoordinator;
  private final UserListNickTooltipBuilder tooltipBuilder = new UserListNickTooltipBuilder();
  private final UserListIgnorePromptHandler ignorePromptHandler;

  private TargetRef active = new TargetRef("default", "status");
  private final UserListTypingIndicators typingIndicators = new UserListTypingIndicators();
  private final Set<String> nickKeys = new HashSet<>();
  private final Map<String, Integer> nickIndexByKey = new HashMap<>();
  private final Timer typingIndicatorTimer =
      new Timer(TYPING_TICK_MS, e -> onTypingIndicatorTick());

  public UserListDockable(
      NickColorService nickColors,
      NickColorSettingsBus nickColorSettingsBus,
      IgnoreListService ignoreListService,
      IgnoreListDialog ignoreDialog,
      IgnoreStatusService ignoreStatusService,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      NickContextMenuFactory nickContextMenuFactory) {
    super(new BorderLayout());
    setMinimumSize(new Dimension(0, 0));

    this.nickColorSettingsBus = nickColorSettingsBus;

    this.ignoreListService = ignoreListService;

    this.ignoreStatusService = ignoreStatusService;
    this.ignorePromptHandler =
        new UserListIgnorePromptHandler(ignoreListService, ignoreStatusService);
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.dccActionCoordinator =
        new DccActionCoordinator(
            this,
            (ctx, cmd) -> {
              if (this.activationBus != null) {
                this.activationBus.activate(ctx);
              }
              if (this.outboundBus != null) {
                this.outboundBus.emit(cmd);
              }
            });
    this.nickContextMenu =
        (nickContextMenuFactory == null)
            ? null
            : nickContextMenuFactory.create(
                new NickContextMenuFactory.Callbacks() {
                  @Override
                  public void openQuery(TargetRef ctx, String nick) {
                    if (ctx == null) return;
                    if (nick == null || nick.isBlank()) return;
                    String sid = Objects.toString(ctx.serverId(), "").trim();
                    if (sid.isEmpty()) return;
                    openPrivate.onNext(new PrivateMessageRequest(sid, nick.trim()));
                  }

                  @Override
                  public void emitUserAction(
                      TargetRef ctx, String nick, UserActionRequest.Action action) {
                    if (ctx == null) return;
                    if (nick == null || nick.isBlank()) return;
                    String sid = Objects.toString(ctx.serverId(), "").trim();
                    if (sid.isEmpty()) return;
                    if (action == null) return;

                    if (action == UserActionRequest.Action.OPEN_QUERY) {
                      openPrivate.onNext(new PrivateMessageRequest(sid, nick.trim()));
                    } else {
                      userActions.onNext(new UserActionRequest(ctx, nick.trim(), action));
                    }
                  }

                  @Override
                  public void promptIgnore(
                      TargetRef ctx, String nick, boolean removing, boolean soft) {
                    if (ctx != null) setChannel(ctx);
                    UserListDockable.this.promptIgnore(removing, soft);
                  }

                  @Override
                  public void requestDccAction(
                      TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
                    UserListDockable.this.requestDccAction(ctx, nick, action);
                  }
                });

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setToolTipText("");
    list.setCellRenderer(
        new UserListNickCellRenderer(
            nickColors, this::ignoreMark, this::typingAlphaForNick, this::isPausedTypingForNick));
    typingIndicatorTimer.setRepeats(true);
    list.addHierarchyListener(
        e -> {
          if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
          if (list.isShowing()) {
            startTypingIndicatorTimerIfNeeded();
            list.repaint();
            return;
          }
          typingIndicatorTimer.stop();
        });
    typingIndicatorTimer.setCoalesce(true);
    scroll =
        new JScrollPane(
            list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
          @Override
          public void updateUI() {
            super.updateUI();
            enforceNoHorizontalScrollBar(this);
          }
        };
    scroll.setMinimumSize(new Dimension(0, 0));
    // Ensure the horizontal scrollbar never reserves height or appears.
    enforceNoHorizontalScrollBar(scroll);

    add(scroll, BorderLayout.CENTER);
    if (ignoreListService != null) {
      var d =
          ignoreListService
              .changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> {
                    if (ch == null) return;
                    if (active == null) return;
                    if (active.serverId() == null || ch.serverId() == null) return;
                    if (!active.serverId().equalsIgnoreCase(ch.serverId())) return;
                    list.repaint();
                  },
                  err -> {});
      closeables.add((AutoCloseable) d::dispose);
    }
    MouseAdapter doubleClick =
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) return;

            int index = list.locationToIndex(e.getPoint());
            if (index < 0) return;

            Rectangle r = list.getCellBounds(index, index);
            if (r == null || !r.contains(e.getPoint())) return;
            if (active == null || !active.isChannel()) return;

            NickInfo ni = model.getElementAt(index);
            String nick = (ni == null) ? "" : Objects.toString(ni.nick(), "").trim();
            if (nick.isBlank()) return;

            openPrivate.onNext(new PrivateMessageRequest(active.serverId(), nick));
          }
        };
    list.addMouseListener(doubleClick);
    closeables.addCleanup(() -> list.removeMouseListener(doubleClick));
    closeables.add(
        ListContextMenuDecorator.decorate(
            list,
            true,
            (index, e) -> {
              if (nickContextMenu == null) return null;
              if (active == null || active.serverId() == null || active.serverId().isBlank())
                return null;

              NickInfo ni = model.getElementAt(index);
              String nick = (ni == null) ? "" : Objects.toString(ni.nick(), "").trim();
              if (nick.isBlank()) return null;

              UserListNickCellRenderer.IgnoreMark mark = ignoreMark(ni);
              return nickContextMenu.forNick(
                  active,
                  nick,
                  new NickContextMenuFactory.IgnoreMark(mark.ignore(), mark.softIgnore()));
            }));
  }

  @PreDestroy
  void shutdown() {
    typingIndicatorTimer.stop();
    closeables.closeQuietly();
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public Flowable<UserActionRequest> userActionRequests() {
    return userActions.onBackpressureBuffer();
  }

  public void setChannel(TargetRef target) {
    TargetRef next = target != null ? target : new TargetRef("default", "status");
    boolean changed = !Objects.equals(this.active, next);
    this.active = next;
    if (changed) {
      clearTypingIndicators();
    }
    list.repaint();
  }

  public void setNicks(List<NickInfo> nicks) {
    nickKeys.clear();
    nickIndexByKey.clear();
    model.clear();
    if (nicks == null || nicks.isEmpty()) {
      clearTypingIndicators();
      return;
    }
    // Bulk add to avoid firing an interval-added event per nick (big channels can be thousands).
    model.addAll(nicks);
    for (int i = 0; i < nicks.size(); i++) {
      NickInfo ni = nicks.get(i);
      String key = foldNick(ni == null ? "" : ni.nick());
      if (key != null) {
        nickKeys.add(key);
        nickIndexByKey.putIfAbsent(key, i);
      }
    }
    pruneTypingIndicatorsToKnownNicks();
  }

  public void setPlaceholder(String... nicks) {
    nickKeys.clear();
    nickIndexByKey.clear();
    model.clear();
    if (nicks == null) return;
    for (String n : nicks) {
      String nick = Objects.toString(n, "").trim();
      if (nick.isEmpty()) continue;
      model.addElement(new NickInfo(nick, "", ""));
      String key = foldNick(nick);
      if (key != null) {
        nickKeys.add(key);
        nickIndexByKey.putIfAbsent(key, model.size() - 1);
      }
    }
    pruneTypingIndicatorsToKnownNicks();
  }

  public void showTypingIndicator(TargetRef target, String nick, String state) {
    if (!sameChannelTarget(active, target)) return;
    String key = foldNick(nick);
    if (key == null || !nickKeys.contains(key)) return;

    long now = System.currentTimeMillis();
    typingIndicators.onTyping(key, state, now);

    if (!typingIndicators.isEmpty()) {
      startTypingIndicatorTimerIfNeeded();
    } else {
      typingIndicatorTimer.stop();
    }
    repaintTypingRows();
  }

  public TargetRef getChannel() {
    return active;
  }

  public List<NickInfo> getNicksSnapshot() {
    List<NickInfo> out = new ArrayList<>(model.size());
    for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
    return out;
  }

  private UserListNickCellRenderer.IgnoreMark ignoreMark(NickInfo ni) {
    if (ignoreListService == null) return new UserListNickCellRenderer.IgnoreMark(false, false);
    if (ignoreStatusService == null) return new UserListNickCellRenderer.IgnoreMark(false, false);
    if (ni == null) return new UserListNickCellRenderer.IgnoreMark(false, false);
    if (active == null || active.serverId() == null || active.serverId().isBlank())
      return new UserListNickCellRenderer.IgnoreMark(false, false);

    String nick = Objects.toString(ni.nick(), "").trim();
    String hostmask = Objects.toString(ni.hostmask(), "").trim();
    if (nick.isEmpty() && hostmask.isEmpty())
      return new UserListNickCellRenderer.IgnoreMark(false, false);

    IgnoreStatusService.Status st = ignoreStatusService.status(active.serverId(), nick, hostmask);
    return new UserListNickCellRenderer.IgnoreMark(st.hard(), st.soft());
  }

  private void onTypingIndicatorTick() {
    if (!isShowing() || !list.isShowing()) {
      typingIndicatorTimer.stop();
      return;
    }

    long now = System.currentTimeMillis();
    UserListTypingIndicators.TickOutcome outcome = typingIndicators.tick(now);
    if (!outcome.hasIndicators()) {
      typingIndicatorTimer.stop();
      if (outcome.changed()) list.repaint();
      return;
    }
    if (outcome.hasVisible() || outcome.changed()) {
      repaintTypingRows();
    }
  }

  private void clearTypingIndicators() {
    if (typingIndicators.isEmpty()) return;
    typingIndicators.clear();
    typingIndicatorTimer.stop();
  }

  private void pruneTypingIndicatorsToKnownNicks() {
    if (typingIndicators.isEmpty()) return;
    if (nickKeys.isEmpty()) {
      clearTypingIndicators();
      return;
    }
    boolean changed = typingIndicators.pruneToKnownNicks(nickKeys);
    if (typingIndicators.isEmpty()) {
      typingIndicatorTimer.stop();
    }
    if (changed) {
      if (typingIndicators.isEmpty()) {
        list.repaint();
      } else {
        repaintTypingRows();
      }
    }
  }

  private void startTypingIndicatorTimerIfNeeded() {
    if (typingIndicators.isEmpty()) return;
    if (!isShowing() || !list.isShowing()) return;
    if (!typingIndicatorTimer.isRunning()) {
      typingIndicatorTimer.start();
    }
  }

  private void repaintTypingRows() {
    if (typingIndicators.isEmpty()) return;

    Rectangle visible = list.getVisibleRect();
    if (visible == null || visible.isEmpty()) return;
    for (String key : typingIndicators.activeKeysSnapshot()) {
      Integer idx = nickIndexByKey.get(key);
      if (idx == null) continue;
      if (idx < 0 || idx >= model.size()) continue;
      Rectangle row = list.getCellBounds(idx, idx);
      if (row == null) continue;
      Rectangle dirty = row.intersection(visible);
      if (dirty.isEmpty()) continue;
      list.repaint(dirty);
    }
  }

  private float typingAlphaForNick(String nick) {
    String key = foldNick(nick);
    if (key == null) return 0f;
    return typingIndicators.alphaForKey(key, System.currentTimeMillis());
  }

  private boolean isPausedTypingForNick(String nick) {
    String key = foldNick(nick);
    if (key == null) return false;
    return typingIndicators.isPausedKey(key);
  }

  private static boolean sameChannelTarget(TargetRef a, TargetRef b) {
    if (a == null || b == null) return false;
    if (!a.isChannel() || !b.isChannel()) return false;
    if (!Objects.equals(a.serverId(), b.serverId())) return false;
    return a.matches(b.target());
  }

  private static String foldNick(String nick) {
    return UserListTypingIndicators.foldNick(nick);
  }

  private void enforceNoHorizontalScrollBar(JScrollPane scroll) {
    if (scroll == null) return;

    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollBar hbar = scroll.getHorizontalScrollBar();
    if (hbar == null) return;

    Dimension zero = new Dimension(0, 0);
    hbar.setVisible(false);
    hbar.setEnabled(false);
    hbar.setPreferredSize(zero);
    hbar.setMinimumSize(zero);
    hbar.setMaximumSize(zero);
  }

  private NickInfo selectedNickInfo(int index) {
    if (index < 0 || index >= model.size()) return null;
    try {
      return model.getElementAt(index);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String selectedNick(int index) {
    NickInfo ni = selectedNickInfo(index);
    if (ni == null) return "";
    return Objects.toString(ni.nick(), "").trim();
  }

  public String selectedNick() {
    try {
      return selectedNick(list.getSelectedIndex());
    } catch (Exception ignored) {
      return "";
    }
  }

  private void promptIgnore(boolean removing, boolean soft) {
    try {
      int idx = list.getSelectedIndex();
      if (idx < 0) return;
      NickInfo nickInfo = selectedNickInfo(idx);
      String nick = selectedNick(idx);
      if (nick.isBlank()) return;
      if (ignorePromptHandler.prompt(this, active, nickInfo, nick, removing, soft)) {
        list.repaint();
      }
    } catch (Exception ignored) {
    }
  }

  private void emitSelected(UserActionRequest.Action action) {
    try {
      if (active == null) return;
      int idx = list.getSelectedIndex();
      if (idx < 0) return;

      String nick = selectedNick(idx);
      if (nick.isBlank()) return;

      if (action == UserActionRequest.Action.OPEN_QUERY) {
        openPrivate.onNext(new PrivateMessageRequest(active.serverId(), nick));
      } else {
        userActions.onNext(new UserActionRequest(active, nick, action));
      }
    } catch (Exception ignored) {
    }
  }

  private void requestDccAction(
      TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
    dccActionCoordinator.requestAction(ctx, nick, action);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (nickColorSettingsBus != null) {
      nickColorSettingsBus.addListener(nickColorSettingsListener);
    }
    SwingUtilities.invokeLater(() -> enforceNoHorizontalScrollBar(scroll));
  }

  @Override
  public void removeNotify() {
    if (nickColorSettingsBus != null) {
      nickColorSettingsBus.removeListener(nickColorSettingsListener);
    }
    super.removeNotify();
  }

  private void onNickColorSettingsChanged(PropertyChangeEvent evt) {
    if (!NickColorSettingsBus.PROP_NICK_COLOR_SETTINGS.equals(evt.getPropertyName())) return;
    SwingUtilities.invokeLater(list::repaint);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 16;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (visibleRect == null) return 64;
    return orientation == SwingConstants.VERTICAL
        ? Math.max(32, visibleRect.height - 24)
        : Math.max(32, visibleRect.width - 24);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    // Crucial for outer docking JScrollPane wrappers: prevent horizontal scrolling at dock level.
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    // Let our inner list scrollpane handle vertical overflow; outer wrappers should not scroll.
    return true;
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Users";
  }
}
