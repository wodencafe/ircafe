package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreMaskMatcher;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
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
import java.util.Locale;
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

  private static final int ACCOUNT_ICON_SIZE = 10;
  private static final int TYPING_ICON_SIZE = 12;
  private static final int TYPING_ICON_RIGHT_PAD = 6;
  private static final int TYPING_FADE_IN_MS = 220;
  private static final int TYPING_HOLD_MS = 8000;
  private static final int TYPING_FADE_OUT_MS = 900;
  private static final int TYPING_PULSE_MS = 1200;
  private static final int TYPING_TICK_MS = 100;

  private static Icon accountIcon(AccountState state) {
    AccountState s = state != null ? state : AccountState.UNKNOWN;
    return switch (s) {
      case LOGGED_IN -> SvgIcons.icon("account-in", ACCOUNT_ICON_SIZE, Palette.QUIET);
      case LOGGED_OUT -> SvgIcons.icon("account-out", ACCOUNT_ICON_SIZE, Palette.QUIET);
      default -> SvgIcons.icon("account-unknown", ACCOUNT_ICON_SIZE, Palette.QUIET);
    };
  }

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

          IgnoreMark mark = ignoreMark(ni);
          String nick = Objects.toString(ni.nick(), "").trim();
          String hostmask = Objects.toString(ni.hostmask(), "").trim();
          String realName = Objects.toString(ni.realName(), "").trim();
          AwayState away = (ni.awayState() == null) ? AwayState.UNKNOWN : ni.awayState();
          AccountState acct =
              (ni.accountState() == null) ? AccountState.UNKNOWN : ni.accountState();

          boolean hasHostmask = IgnoreMaskMatcher.isUsefulHostmask(hostmask);
          if (nick.isEmpty()) return null;

          StringBuilder sb = new StringBuilder(128);
          sb.append("<html>");
          if (!nick.isEmpty()) {
            sb.append("<b>").append(escapeHtml(nick)).append("</b>");
          }

          if (!realName.isEmpty()) {
            sb.append("<br>").append("<i>Name</i>: ").append(escapeHtml(realName));
          }

          if (hasHostmask) {
            sb.append("<br>")
                .append("<span style='font-family:monospace'>")
                .append(escapeHtml(hostmask))
                .append("</span>");
          } else {
            sb.append("<br>").append("<i>Hostmask pending</i>");
          }

          if (away == AwayState.AWAY) {
            String reason = ni.awayMessage();
            if (reason != null && !reason.isBlank()) {
              sb.append("<br>").append("<i>Away</i>: ").append(escapeHtml(reason));
            } else {
              sb.append("<br>").append("<i>Away</i>");
            }
          }

          if (acct == AccountState.LOGGED_IN) {
            String account = ni.accountName();
            if (account != null && !account.isBlank()) {
              sb.append("<br>").append("<i>Account</i>: ").append(escapeHtml(account.trim()));
            } else {
              sb.append("<br>").append("<i>Account</i>: ").append("<i>logged in</i>");
            }
          } else if (acct == AccountState.LOGGED_OUT) {
            sb.append("<br>").append("<i>Account</i>: ").append("<i>logged out</i>");
          } else {
            sb.append("<br>").append("<i>Account</i>: ").append("<i>unknown</i>");
          }

          if (mark.ignore && mark.softIgnore) {
            sb.append("<br>").append("Ignored + soft ignored");
          } else if (mark.ignore) {
            sb.append("<br>").append("Ignored (messages hidden)");
          } else if (mark.softIgnore) {
            sb.append("<br>").append("Soft ignored (messages shown as spoilers)");
          }

          sb.append("</html>");
          return sb.toString();
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

  private final NickColorService nickColors;
  private final NickColorSettingsBus nickColorSettingsBus;

  private final PropertyChangeListener nickColorSettingsListener = this::onNickColorSettingsChanged;

  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;

  private final IgnoreListService ignoreListService;

  private final IgnoreStatusService ignoreStatusService;
  private final NickContextMenuFactory.NickContextMenu nickContextMenu;
  private final JScrollPane scroll;

  private record IgnoreMark(boolean ignore, boolean softIgnore) {}

  private TargetRef active = new TargetRef("default", "status");
  private final Map<String, TypingIndicatorState> typingByNick = new HashMap<>();
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

    this.nickColors = nickColors;
    this.nickColorSettingsBus = nickColorSettingsBus;

    this.ignoreListService = ignoreListService;

    this.ignoreStatusService = ignoreStatusService;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
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
    list.setCellRenderer(new NickCellRenderer());
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

              IgnoreMark mark = ignoreMark(ni);
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
    TypingIndicatorState indicator =
        typingByNick.computeIfAbsent(key, __ -> new TypingIndicatorState());
    indicator.apply(state, now);
    indicator.expireIfNeeded(now);
    if (indicator.isFinished(now)) {
      typingByNick.remove(key);
    }

    if (!typingByNick.isEmpty()) {
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

  private IgnoreMark ignoreMark(NickInfo ni) {
    if (ignoreListService == null) return new IgnoreMark(false, false);
    if (ignoreStatusService == null) return new IgnoreMark(false, false);
    if (ni == null) return new IgnoreMark(false, false);
    if (active == null || active.serverId() == null || active.serverId().isBlank())
      return new IgnoreMark(false, false);

    String nick = Objects.toString(ni.nick(), "").trim();
    String hostmask = Objects.toString(ni.hostmask(), "").trim();
    if (nick.isEmpty() && hostmask.isEmpty()) return new IgnoreMark(false, false);

    IgnoreStatusService.Status st = ignoreStatusService.status(active.serverId(), nick, hostmask);
    return new IgnoreMark(st.hard(), st.soft());
  }

  private void onTypingIndicatorTick() {
    if (!isShowing() || !list.isShowing()) {
      typingIndicatorTimer.stop();
      return;
    }

    long now = System.currentTimeMillis();
    boolean visible = false;
    boolean changed = false;

    java.util.Iterator<Map.Entry<String, TypingIndicatorState>> it =
        typingByNick.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, TypingIndicatorState> e = it.next();
      TypingIndicatorState state = e.getValue();
      if (state == null) {
        it.remove();
        changed = true;
        continue;
      }
      state.expireIfNeeded(now);
      if (state.isFinished(now)) {
        it.remove();
        changed = true;
        continue;
      }
      if (state.alpha(now) > 0.01f) {
        visible = true;
      }
    }

    if (typingByNick.isEmpty()) {
      typingIndicatorTimer.stop();
      if (changed) list.repaint();
      return;
    }
    if (visible || changed) {
      repaintTypingRows();
    }
  }

  private void clearTypingIndicators() {
    if (typingByNick.isEmpty()) return;
    typingByNick.clear();
    typingIndicatorTimer.stop();
  }

  private void pruneTypingIndicatorsToKnownNicks() {
    if (typingByNick.isEmpty()) return;
    if (nickKeys.isEmpty()) {
      clearTypingIndicators();
      return;
    }
    boolean changed = typingByNick.keySet().removeIf(k -> k == null || !nickKeys.contains(k));
    if (typingByNick.isEmpty()) {
      typingIndicatorTimer.stop();
    }
    if (changed) {
      if (typingByNick.isEmpty()) {
        list.repaint();
      } else {
        repaintTypingRows();
      }
    }
  }

  private void startTypingIndicatorTimerIfNeeded() {
    if (typingByNick.isEmpty()) return;
    if (!isShowing() || !list.isShowing()) return;
    if (!typingIndicatorTimer.isRunning()) {
      typingIndicatorTimer.start();
    }
  }

  private void repaintTypingRows() {
    if (typingByNick.isEmpty()) return;

    Rectangle visible = list.getVisibleRect();
    if (visible == null || visible.isEmpty()) return;
    for (String key : typingByNick.keySet()) {
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
    TypingIndicatorState state = typingByNick.get(key);
    if (state == null) return 0f;
    return state.alpha(System.currentTimeMillis());
  }

  private boolean isPausedTypingForNick(String nick) {
    String key = foldNick(nick);
    if (key == null) return false;
    TypingIndicatorState state = typingByNick.get(key);
    return state != null && state.isPaused();
  }

  private static boolean sameChannelTarget(TargetRef a, TargetRef b) {
    if (a == null || b == null) return false;
    if (!a.isChannel() || !b.isChannel()) return false;
    if (!Objects.equals(a.serverId(), b.serverId())) return false;
    return a.matches(b.target());
  }

  private static String foldNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return null;
    return n.toLowerCase(Locale.ROOT);
  }

  private static String normalizeTypingState(String state) {
    String s = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "active";
    };
  }

  private final class NickCellRenderer extends DefaultListCellRenderer {
    private final Icon keyboardIcon = SvgIcons.icon("keyboard", TYPING_ICON_SIZE, Palette.QUIET);
    private float typingIconAlpha = 0f;

    @Override
    public java.awt.Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JLabel lbl =
          (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      NickInfo ni = (value instanceof NickInfo n) ? n : null;

      String nick = ni == null ? "" : Objects.toString(ni.nick(), "");
      String prefix = ni == null ? "" : Objects.toString(ni.prefix(), "");
      String display = prefix + nick;

      IgnoreMark mark = ignoreMark(ni);
      AwayState away = (ni == null || ni.awayState() == null) ? AwayState.UNKNOWN : ni.awayState();
      AccountState acct =
          (ni == null || ni.accountState() == null) ? AccountState.UNKNOWN : ni.accountState();

      lbl.setIcon(accountIcon(acct));
      lbl.setIconTextGap(6);
      if (mark.ignore) display += "  [IGN]";
      if (mark.softIgnore) display += "  [SOFT]";
      lbl.setText(display);

      Font f = lbl.getFont();
      int style = f.getStyle();
      if (mark.ignore) style |= Font.BOLD;
      if (mark.softIgnore) style |= Font.ITALIC;
      if (away == AwayState.AWAY) style |= Font.ITALIC;
      lbl.setFont(f.deriveFont(style));

      if (!nick.isBlank() && nickColors != null && nickColors.enabled()) {
        Color fg = nickColors.colorForNick(nick, lbl.getBackground(), lbl.getForeground());
        lbl.setForeground(fg);
      }

      typingIconAlpha = typingAlphaForNick(nick);
      if (isPausedTypingForNick(nick)) {
        typingIconAlpha = Math.min(typingIconAlpha, 0.4f);
      }

      int rightPad =
          (keyboardIcon == null) ? 2 : (keyboardIcon.getIconWidth() + TYPING_ICON_RIGHT_PAD + 2);
      lbl.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, rightPad));
      return lbl;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (keyboardIcon == null || typingIconAlpha <= 0.01f) return;

      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, typingIconAlpha))));
        int x = Math.max(0, getWidth() - keyboardIcon.getIconWidth() - TYPING_ICON_RIGHT_PAD);
        int y = Math.max(0, (getHeight() - keyboardIcon.getIconHeight()) / 2);
        keyboardIcon.paintIcon(this, g2, x, y);
      } finally {
        g2.dispose();
      }
    }
  }

  private enum TypingVisualState {
    ACTIVE,
    PAUSED,
    FADING
  }

  private static final class TypingIndicatorState {
    private TypingVisualState mode = TypingVisualState.ACTIVE;
    private long visibleSinceMs = 0L;
    private long expiresAtMs = 0L;
    private long fadeStartedMs = 0L;
    private float fadeFromAlpha = 0f;

    void apply(String rawState, long now) {
      String state = normalizeTypingState(rawState);
      if ("done".equals(state)) {
        startFade(now, alpha(now));
        return;
      }

      if (!isVisible(now)) {
        visibleSinceMs = now;
      }
      mode = "paused".equals(state) ? TypingVisualState.PAUSED : TypingVisualState.ACTIVE;
      expiresAtMs = now + TYPING_HOLD_MS;
      fadeStartedMs = 0L;
      fadeFromAlpha = 0f;
    }

    void expireIfNeeded(long now) {
      if (mode == TypingVisualState.FADING) return;
      if (expiresAtMs > 0L && now >= expiresAtMs) {
        startFade(now, alpha(now));
      }
    }

    boolean isFinished(long now) {
      if (mode != TypingVisualState.FADING) return false;
      return now - fadeStartedMs >= TYPING_FADE_OUT_MS;
    }

    boolean isPaused() {
      return mode == TypingVisualState.PAUSED;
    }

    float alpha(long now) {
      if (mode == TypingVisualState.FADING) {
        if (fadeStartedMs <= 0L) return 0f;
        long elapsed = Math.max(0L, now - fadeStartedMs);
        if (elapsed >= TYPING_FADE_OUT_MS) return 0f;
        float progress = elapsed / (float) TYPING_FADE_OUT_MS;
        return Math.max(0f, fadeFromAlpha * (1f - progress));
      }

      float fadeIn = 1f;
      if (visibleSinceMs > 0L) {
        fadeIn = Math.max(0f, Math.min(1f, (now - visibleSinceMs) / (float) TYPING_FADE_IN_MS));
      }
      if (mode == TypingVisualState.PAUSED) {
        return 0.38f * fadeIn;
      }

      double phase = (now % TYPING_PULSE_MS) / (double) TYPING_PULSE_MS;
      double wave = 0.5d + (0.5d * Math.sin((phase * (Math.PI * 2d)) - (Math.PI / 2d)));
      float pulse = (float) (0.45d + (0.55d * wave));
      return Math.max(0f, Math.min(1f, fadeIn * pulse));
    }

    private void startFade(long now, float fromAlpha) {
      mode = TypingVisualState.FADING;
      fadeStartedMs = now;
      expiresAtMs = 0L;
      fadeFromAlpha = Math.max(0f, Math.min(1f, fromAlpha));
    }

    private boolean isVisible(long now) {
      return alpha(now) > 0.01f;
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
      if (active == null || active.serverId() == null || active.serverId().isBlank()) return;
      int idx = list.getSelectedIndex();
      if (idx < 0) return;

      NickInfo ni = selectedNickInfo(idx);
      String nick = selectedNick(idx);
      if (nick.isBlank()) return;
      String hm = ni == null ? "" : Objects.toString(ni.hostmask(), "").trim();
      String seedBase =
          (ignoreStatusService == null)
              ? (IgnoreMaskMatcher.isUsefulHostmask(hm) ? hm : nick)
              : ignoreStatusService.bestSeedForMask(active.serverId(), nick, hm);
      String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);
      Window owner = SwingUtilities.getWindowAncestor(this);

      String title;
      String prompt;
      if (soft) {
        title = removing ? "Soft Unignore" : "Soft Ignore";
        prompt =
            removing
                ? "Remove soft-ignore mask (per-server):"
                : "Add soft-ignore mask (per-server):";
      } else {
        title = removing ? "Unignore" : "Ignore";
        prompt = removing ? "Remove ignore mask (per-server):" : "Add ignore mask (per-server):";
      }

      String input =
          (String)
              JOptionPane.showInputDialog(
                  owner != null ? owner : this,
                  prompt,
                  title,
                  JOptionPane.PLAIN_MESSAGE,
                  null,
                  null,
                  seed);
      if (input == null) return;
      String arg = input.trim();
      if (arg.isEmpty()) return;

      boolean changed;
      if (removing) {
        if (soft) {
          changed =
              ignoreListService != null && ignoreListService.removeSoftMask(active.serverId(), arg);
        } else {
          changed =
              ignoreListService != null && ignoreListService.removeMask(active.serverId(), arg);
        }
      } else {
        if (soft) {
          changed =
              ignoreListService != null && ignoreListService.addSoftMask(active.serverId(), arg);
        } else {
          changed = ignoreListService != null && ignoreListService.addMask(active.serverId(), arg);
        }
      }

      String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
      String msg;
      if (soft) {
        if (removing) {
          msg =
              changed ? ("Removed soft ignore: " + stored) : ("Not in soft-ignore list: " + stored);
        } else {
          msg = changed ? ("Soft ignoring: " + stored) : ("Already soft-ignored: " + stored);
        }
      } else {
        if (removing) {
          msg = changed ? ("Removed ignore: " + stored) : ("Not in ignore list: " + stored);
        } else {
          msg = changed ? ("Ignoring: " + stored) : ("Already ignored: " + stored);
        }
      }

      JOptionPane.showMessageDialog(
          owner != null ? owner : this, msg, title, JOptionPane.INFORMATION_MESSAGE);
      list.repaint();
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

    if (activationBus != null) {
      activationBus.activate(ctx);
    }
    if (outboundBus != null) {
      outboundBus.emit(cmd);
    }
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
