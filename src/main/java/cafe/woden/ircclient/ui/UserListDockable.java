package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreMaskMatcher;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.ListContextMenuDecorator;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Lazy
public class UserListDockable extends JPanel implements Dockable {
  public static final String ID = "users";

  private static final int ACCOUNT_ICON_SIZE = 10;

  private static Icon accountIcon(AccountState state) {
    AccountState s = state != null ? state : AccountState.UNKNOWN;
    return switch (s) {
      case LOGGED_IN -> SvgIcons.icon("account-in", ACCOUNT_ICON_SIZE, Palette.QUIET);
      case LOGGED_OUT -> SvgIcons.icon("account-out", ACCOUNT_ICON_SIZE, Palette.QUIET);
      default -> SvgIcons.icon("account-unknown", ACCOUNT_ICON_SIZE, Palette.QUIET);
    };
  }


  private final DefaultListModel<NickInfo> model = new DefaultListModel<>();
  private final JList<NickInfo> list = new JList<>(model) {

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
      AccountState acct = (ni.accountState() == null) ? AccountState.UNKNOWN : ni.accountState();

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
   * <p>Important: Dockables can be closed/hidden and later re-opened. We therefore must
   * not auto-dispose listeners/subscriptions just because the component becomes
   * temporarily non-displayable (that would permanently break behaviors like right-click
   * menus and double-click actions after reopening the dock).
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
  private final IgnoreListDialog ignoreDialog;
  private final IgnoreStatusService ignoreStatusService;
  private final NickContextMenuFactory.NickContextMenu nickContextMenu;

  private record IgnoreMark(boolean ignore, boolean softIgnore) {}

  private TargetRef active = new TargetRef("default", "status");

  public UserListDockable(
                         NickColorService nickColors,
                         NickColorSettingsBus nickColorSettingsBus,
                         IgnoreListService ignoreListService, IgnoreListDialog ignoreDialog,
                         IgnoreStatusService ignoreStatusService,
                         TargetActivationBus activationBus,
                         OutboundLineBus outboundBus,
                         NickContextMenuFactory nickContextMenuFactory) {
    super(new BorderLayout());

    this.nickColors = nickColors;
    this.nickColorSettingsBus = nickColorSettingsBus;

    this.ignoreListService = ignoreListService;
    this.ignoreDialog = ignoreDialog;

    this.ignoreStatusService = ignoreStatusService;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.nickContextMenu = (nickContextMenuFactory == null) ? null
        : nickContextMenuFactory.create(new NickContextMenuFactory.Callbacks() {
      @Override
      public void openQuery(TargetRef ctx, String nick) {
        if (ctx == null) return;
        if (nick == null || nick.isBlank()) return;
        String sid = Objects.toString(ctx.serverId(), "").trim();
        if (sid.isEmpty()) return;
        openPrivate.onNext(new PrivateMessageRequest(sid, nick.trim()));
      }

      @Override
      public void emitUserAction(TargetRef ctx, String nick, UserActionRequest.Action action) {
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
      public void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft) {
        if (ctx != null) setChannel(ctx);
        UserListDockable.this.promptIgnore(removing, soft);
      }

      @Override
      public void requestDccAction(TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
        UserListDockable.this.requestDccAction(ctx, nick, action);
      }
    });

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setToolTipText("");
    final ListCellRenderer<? super NickInfo> baseRenderer = list.getCellRenderer();
    list.setCellRenderer((JList<? extends NickInfo> l, NickInfo value, int index, boolean isSelected, boolean cellHasFocus) -> {
      java.awt.Component c = baseRenderer.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);

      if (!(c instanceof JLabel lbl)) return c;

      String nick = value == null ? "" : Objects.toString(value.nick(), "");
      String prefix = value == null ? "" : Objects.toString(value.prefix(), "");
      String raw = prefix + nick;

      IgnoreMark mark = ignoreMark(value);
      String display = raw;
      AwayState away = (value == null || value.awayState() == null) ? AwayState.UNKNOWN : value.awayState();
      AccountState acct = (value == null || value.accountState() == null) ? AccountState.UNKNOWN : value.accountState();

      // Account indicator (logged in/out/unknown).
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
      if (nick != null && !nick.isBlank() && nickColors != null && nickColors.enabled()) {
        Color fg = nickColors.colorForNick(nick, lbl.getBackground(), lbl.getForeground());
        lbl.setForeground(fg);
      }

      return c;
    });
    JScrollPane scroll = new JScrollPane(
        list,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    // Ensure the horizontal scrollbar never reserves height.
    JScrollBar hbar = scroll.getHorizontalScrollBar();
    if (hbar != null) {
      hbar.setVisible(false);
      Dimension zero = new Dimension(0, 0);
      hbar.setPreferredSize(zero);
      hbar.setMinimumSize(zero);
      hbar.setMaximumSize(zero);
    }

    add(scroll, BorderLayout.CENTER);
    if (ignoreListService != null) {
      var d = ignoreListService.changes()
          .observeOn(SwingEdt.scheduler())
          .subscribe(ch -> {
            if (ch == null) return;
            if (active == null) return;
            if (active.serverId() == null || ch.serverId() == null) return;
            if (!active.serverId().equalsIgnoreCase(ch.serverId())) return;
            list.repaint();
          }, err -> {
          });
      closeables.add((AutoCloseable) d::dispose);
    }
    MouseAdapter doubleClick = new MouseAdapter() {
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
    closeables.add(ListContextMenuDecorator.decorate(list, true, (index, e) -> {
      if (nickContextMenu == null) return null;
      if (active == null || active.serverId() == null || active.serverId().isBlank()) return null;

      NickInfo ni = model.getElementAt(index);
      String nick = (ni == null) ? "" : Objects.toString(ni.nick(), "").trim();
      if (nick.isBlank()) return null;

      IgnoreMark mark = ignoreMark(ni);
      return nickContextMenu.forNick(active, nick,
          new NickContextMenuFactory.IgnoreMark(mark.ignore(), mark.softIgnore()));
    }));

  }

  @PreDestroy
  void shutdown() {
    closeables.closeQuietly();
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public Flowable<UserActionRequest> userActionRequests() {
    return userActions.onBackpressureBuffer();
  }

  public void setChannel(TargetRef target) {
    this.active = target;
    list.repaint();
  }

  public void setNicks(List<NickInfo> nicks) {
    model.clear();
    if (nicks == null || nicks.isEmpty()) return;
    // Bulk add to avoid firing an interval-added event per nick (big channels can be thousands).
    model.addAll(nicks);
  }

  public void setPlaceholder(String... nicks) {
    model.clear();
    if (nicks == null) return;
    for (String n : nicks) {
      String nick = Objects.toString(n, "").trim();
      if (nick.isEmpty()) continue;
      model.addElement(new NickInfo(nick, "", ""));
    }
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
    if (active == null || active.serverId() == null || active.serverId().isBlank()) return new IgnoreMark(false, false);

    String nick = Objects.toString(ni.nick(), "").trim();
    String hostmask = Objects.toString(ni.hostmask(), "").trim();
    if (nick.isEmpty() && hostmask.isEmpty()) return new IgnoreMark(false, false);

    IgnoreStatusService.Status st = ignoreStatusService.status(active.serverId(), nick, hostmask);
    return new IgnoreMark(st.hard(), st.soft());
  }

  private static String escapeHtml(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        case '\'': sb.append("&#39;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
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

  private void promptIgnore(boolean removing, boolean soft) {
    try {
      if (active == null || active.serverId() == null || active.serverId().isBlank()) return;
      int idx = list.getSelectedIndex();
      if (idx < 0) return;

      NickInfo ni = selectedNickInfo(idx);
      String nick = selectedNick(idx);
      if (nick.isBlank()) return;
      String hm = ni == null ? "" : Objects.toString(ni.hostmask(), "").trim();
      String seedBase = (ignoreStatusService == null)
          ? (IgnoreMaskMatcher.isUsefulHostmask(hm) ? hm : nick)
          : ignoreStatusService.bestSeedForMask(active.serverId(), nick, hm);
      String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);
      Window owner = SwingUtilities.getWindowAncestor(this);

      String title;
      String prompt;
      if (soft) {
        title = removing ? "Soft Unignore" : "Soft Ignore";
        prompt = removing
            ? "Remove soft-ignore mask (per-server):"
            : "Add soft-ignore mask (per-server):";
      } else {
        title = removing ? "Unignore" : "Ignore";
        prompt = removing
            ? "Remove ignore mask (per-server):"
            : "Add ignore mask (per-server):";
      }

      String input = (String) JOptionPane.showInputDialog(
          owner != null ? owner : this,
          prompt,
          title,
          JOptionPane.PLAIN_MESSAGE,
          null,
          null,
          seed
      );
      if (input == null) return;
      String arg = input.trim();
      if (arg.isEmpty()) return;

      boolean changed;
      if (removing) {
        if (soft) {
          changed = ignoreListService != null && ignoreListService.removeSoftMask(active.serverId(), arg);
        } else {
          changed = ignoreListService != null && ignoreListService.removeMask(active.serverId(), arg);
        }
      } else {
        if (soft) {
          changed = ignoreListService != null && ignoreListService.addSoftMask(active.serverId(), arg);
        } else {
          changed = ignoreListService != null && ignoreListService.addMask(active.serverId(), arg);
        }
      }

      String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
      String msg;
      if (soft) {
        if (removing) {
          msg = changed ? ("Removed soft ignore: " + stored) : ("Not in soft-ignore list: " + stored);
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

      JOptionPane.showMessageDialog(owner != null ? owner : this, msg, title, JOptionPane.INFORMATION_MESSAGE);
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

  private void requestDccAction(TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
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

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Users"; }
}
