package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.ComponentCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.ListContextMenuDecorator;
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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Lazy
public class UserListDockable extends JPanel implements Dockable {
  public static final String ID = "users";

  /**
   * Store full NickInfo entries so we can retain metadata (e.g. hostmask) even if we only render the nick.
   */
  private final DefaultListModel<NickInfo> model = new DefaultListModel<>();
  private final JList<NickInfo> list = new JList<>(model) {
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

      boolean hasHostmask = isUsefulHostmask(hostmask);
      // Always show a tooltip for a real nick; if hostmask isn't known yet, show a pending hint.
      if (nick.isEmpty()) return null;

      StringBuilder sb = new StringBuilder(128);
      sb.append("<html>");
      if (!nick.isEmpty()) {
        sb.append("<b>").append(escapeHtml(nick)).append("</b>");
      }

      if (hasHostmask) {
        sb.append("<br>")
            .append("<span style='font-family:monospace'>")
            .append(escapeHtml(hostmask))
            .append("</span>");
      } else {
        sb.append("<br>").append("<i>Hostmask pending</i>");
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

  private final CloseableScope closeables = ComponentCloseableScopeDecorator.install(this);

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final NickColorService nickColors;

  private final IgnoreListService ignoreListService;
  private final IgnoreListDialog ignoreDialog;

  private volatile String ignoreCacheServerId = "";
  private volatile List<String> ignoreCacheMasks = List.of();
  private volatile List<String> ignoreCacheSoftMasks = List.of();
  private final ConcurrentHashMap<String, Pattern> nickGlobCache = new ConcurrentHashMap<>();

  private record IgnoreMark(boolean ignore, boolean softIgnore) {}


  private TargetRef active = new TargetRef("default", "status");

  public UserListDockable(NickColorService nickColors, IgnoreListService ignoreListService, IgnoreListDialog ignoreDialog) {
    super(new BorderLayout());

    this.nickColors = nickColors;

    this.ignoreListService = ignoreListService;
    this.ignoreDialog = ignoreDialog;

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // Enable ToolTipManager support for this component. The actual tooltip text is provided
    // by the JList#getToolTipText(MouseEvent) override above.
    list.setToolTipText("");

    // Deterministic, global per-nick coloring in the user list + ignore indicators.
    final ListCellRenderer<? super NickInfo> baseRenderer = list.getCellRenderer();
    list.setCellRenderer((JList<? extends NickInfo> l, NickInfo value, int index, boolean isSelected, boolean cellHasFocus) -> {
      java.awt.Component c = baseRenderer.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);

      if (!(c instanceof JLabel lbl)) return c;

      String nick = value == null ? "" : Objects.toString(value.nick(), "");
      String prefix = value == null ? "" : Objects.toString(value.prefix(), "");
      String raw = prefix + nick;

      IgnoreMark mark = ignoreMark(value);

      // Text decoration (keep the underlying model unchanged).
      String display = raw;
      if (mark.ignore) display += "  [IGN]";
      if (mark.softIgnore) display += "  [SOFT]";
      lbl.setText(display);

      // Font decoration (reset per cell render).
      Font f = lbl.getFont();
      int style = f.getStyle();
      if (mark.ignore) style |= Font.BOLD;
      if (mark.softIgnore) style |= Font.ITALIC;
      lbl.setFont(f.deriveFont(style));

      // Apply per-nick color last so it wins for the nick label.
      if (nick != null && !nick.isBlank() && nickColors != null && nickColors.enabled()) {
        Color fg = nickColors.colorForNick(nick, lbl.getBackground(), lbl.getForeground());
        lbl.setForeground(fg);
      }

      return c;
    });
    add(new JScrollPane(list), BorderLayout.CENTER);

    // Repaint the list when ignore/soft-ignore lists change for the active server.
    if (ignoreListService != null) {
      var d = ignoreListService.changes()
          .observeOn(SwingEdt.scheduler())
          .subscribe(ch -> {
            if (ch == null) return;
            if (active == null) return;
            if (active.serverId() == null || ch.serverId() == null) return;
            if (!active.serverId().equalsIgnoreCase(ch.serverId())) return;
            refreshIgnoreCache(true);
            list.repaint();
          }, err -> {
            // ignore
          });
      closeables.add((AutoCloseable) d::dispose);
    }

    // Double-click a nick to open a PM
    MouseAdapter doubleClick = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) return;

        int index = list.locationToIndex(e.getPoint());
        if (index < 0) return;

        Rectangle r = list.getCellBounds(index, index);
        if (r == null || !r.contains(e.getPoint())) return;

        // Only meaningful when we're viewing a channel user list.
        if (active == null || !active.isChannel()) return;

        NickInfo ni = model.getElementAt(index);
        String nick = (ni == null) ? "" : Objects.toString(ni.nick(), "").trim();
        if (nick.isBlank()) return;

        openPrivate.onNext(new PrivateMessageRequest(active.serverId(), nick));
      }
    };
    list.addMouseListener(doubleClick);
    closeables.addCleanup(() -> list.removeMouseListener(doubleClick));

    // Right-click context menu for common user actions.
    JPopupMenu menu = new JPopupMenu();
    JMenuItem openQuery = new JMenuItem("Open Query");
    JMenuItem whois = new JMenuItem("Whois");
    JMenuItem version = new JMenuItem("Version");
    JMenuItem ping = new JMenuItem("Ping");
    JMenuItem time = new JMenuItem("Time");

    JMenuItem ignore = new JMenuItem("Ignore...");
    JMenuItem unignore = new JMenuItem("Unignore...");
    JMenuItem softIgnore = new JMenuItem("Soft Ignore...");
    JMenuItem softUnignore = new JMenuItem("Soft Unignore...");

    menu.add(openQuery);
    menu.addSeparator();
    menu.add(whois);
    menu.add(version);
    menu.add(ping);
    menu.add(time);

    menu.addSeparator();
    menu.add(ignore);
    menu.add(unignore);
    menu.add(softIgnore);
    menu.add(softUnignore);

    openQuery.addActionListener(a -> emitSelected(UserActionRequest.Action.OPEN_QUERY));
    whois.addActionListener(a -> emitSelected(UserActionRequest.Action.WHOIS));
    version.addActionListener(a -> emitSelected(UserActionRequest.Action.CTCP_VERSION));
    ping.addActionListener(a -> emitSelected(UserActionRequest.Action.CTCP_PING));
    time.addActionListener(a -> emitSelected(UserActionRequest.Action.CTCP_TIME));

    ignore.addActionListener(a -> promptIgnore(false, false));
    unignore.addActionListener(a -> promptIgnore(true, false));
    softIgnore.addActionListener(a -> promptIgnore(false, true));
    softUnignore.addActionListener(a -> promptIgnore(true, true));

    closeables.add(ListContextMenuDecorator.decorate(list, true, (index, e) -> {
      // If we don't have a meaningful context target (e.g., status), disable actions.
      boolean hasCtx = active != null && active.serverId() != null && !active.serverId().isBlank();
      NickInfo ni = model.getElementAt(index);
      String nick = (ni == null) ? "" : Objects.toString(ni.nick(), "").trim();
      boolean hasNick = nick != null && !nick.isBlank();

      openQuery.setEnabled(hasCtx && hasNick);
      whois.setEnabled(hasCtx && hasNick);
      version.setEnabled(hasCtx && hasNick);
      ping.setEnabled(hasCtx && hasNick);
      time.setEnabled(hasCtx && hasNick);

      IgnoreMark mark = ignoreMark(ni);

      ignore.setEnabled(hasCtx && hasNick);
      unignore.setEnabled(hasCtx && hasNick && mark.ignore);
      softIgnore.setEnabled(hasCtx && hasNick);
      softUnignore.setEnabled(hasCtx && hasNick && mark.softIgnore);

      return menu;
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
    refreshIgnoreCache(false);
    list.repaint();
  }

  public void setNicks(List<NickInfo> nicks) {
    model.clear();
    if (nicks == null) return;
    for (NickInfo n : nicks) model.addElement(n);
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

  private void refreshIgnoreCache(boolean force) {
    if (ignoreListService == null) {
      ignoreCacheServerId = "";
      ignoreCacheMasks = List.of();
      ignoreCacheSoftMasks = List.of();
      nickGlobCache.clear();
      return;
    }

    String sid = (active == null) ? "" : Objects.toString(active.serverId(), "").trim();
    if (sid.isEmpty()) {
      ignoreCacheServerId = "";
      ignoreCacheMasks = List.of();
      ignoreCacheSoftMasks = List.of();
      nickGlobCache.clear();
      return;
    }

    if (force || !Objects.equals(ignoreCacheServerId, sid)) {
      ignoreCacheServerId = sid;
      ignoreCacheMasks = ignoreListService.listMasks(sid);
      ignoreCacheSoftMasks = ignoreListService.listSoftMasks(sid);
      nickGlobCache.clear();
    }
  }

  private IgnoreMark ignoreMark(NickInfo ni) {
    if (ignoreListService == null) return new IgnoreMark(false, false);
    if (ni == null) return new IgnoreMark(false, false);

    String nick = Objects.toString(ni.nick(), "").trim();
    String hostmask = Objects.toString(ni.hostmask(), "").trim();
    if (nick.isEmpty() && hostmask.isEmpty()) return new IgnoreMark(false, false);

    refreshIgnoreCache(false);

    // Prefer full hostmask matching when we have it, because it can match host-only ignores like "*!*@host".
    // When we don't know a user's hostmask yet, fall back to nick-glob heuristics so nick-based ignores
    // (stored as "nick!*@*") still show up in the user list.
    boolean hard = false;
    boolean soft = false;

    if (isUsefulHostmask(hostmask)) {
      hard = hostmaskTargetedByAny(ignoreCacheMasks, hostmask);
      soft = hostmaskTargetedByAny(ignoreCacheSoftMasks, hostmask);
    } else if (!nick.isEmpty()) {
      hard = nickTargetedByAny(ignoreCacheMasks, nick);
      soft = nickTargetedByAny(ignoreCacheSoftMasks, nick);
    }

    return new IgnoreMark(hard, soft);
  }

  private boolean hostmaskTargetedByAny(List<String> masks, String hostmask) {
    if (masks == null || masks.isEmpty()) return false;
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      if (globMatchIgnoreMask(m, hm)) return true;
    }
    return false;
  }

  /**
   * Glob match for ignore masks: "*" = any sequence, "?" = any char, case-insensitive.
   * Mirrors IgnoreListService matching so the user list indicators agree with message filtering.
   */
  private static boolean globMatchIgnoreMask(String pattern, String text) {
    String ptn = Objects.toString(pattern, "").trim().toLowerCase(Locale.ROOT);
    String txt = Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
    if (ptn.isEmpty() || txt.isEmpty()) return false;

    int p = 0;
    int t = 0;
    int star = -1;
    int match = 0;

    while (t < txt.length()) {
      if (p < ptn.length() && (ptn.charAt(p) == '?' || ptn.charAt(p) == txt.charAt(t))) {
        p++;
        t++;
        continue;
      }

      if (p < ptn.length() && ptn.charAt(p) == '*') {
        star = p;
        match = t;
        p++;
        continue;
      }

      if (star != -1) {
        p = star + 1;
        match++;
        t = match;
        continue;
      }

      return false;
    }

    while (p < ptn.length() && ptn.charAt(p) == '*') p++;
    return p == ptn.length();
  }

  private boolean nickTargetedByAny(List<String> masks, String nick) {
    if (masks == null || masks.isEmpty()) return false;
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      int bang = m.indexOf('!');
      if (bang <= 0) continue;
      String nickGlob = m.substring(0, bang).trim();
      if (nickGlob.isEmpty()) continue;

      // Avoid marking everyone for host-only patterns like "*!ident@host".
      if (nickGlob.chars().allMatch(ch -> ch == '*' || ch == '?')) continue;

      if (globMatchesNick(nickGlob, n)) return true;
    }
    return false;
  }

  private boolean globMatchesNick(String glob, String nick) {
    String key = Objects.toString(glob, "").toLowerCase(Locale.ROOT);
    Pattern p = nickGlobCache.computeIfAbsent(key, k -> Pattern.compile(globToRegex(glob), Pattern.CASE_INSENSITIVE));
    return p.matcher(nick).matches();
  }

  private String globToRegex(String glob) {
    StringBuilder sb = new StringBuilder();
    sb.append('^');
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*': sb.append(".*"); break;
        case '?': sb.append('.'); break;
        case '\\': sb.append("\\\\"); break;
        default:
          if (".+()^$|{}[]\\".indexOf(c) >= 0) sb.append('\\');
          sb.append(c);
      }
    }
    sb.append('$');
    return sb.toString();
  }

  /**
   * Best-effort check that a hostmask is "useful" (not empty and not just a derived wildcard placeholder).
   *
   * <p>We treat the common forms {@code nick!user@host} and {@code user@host} as valid. We treat
   * placeholders like {@code nick!*@*} as not useful.
   */
  private static boolean isUsefulHostmask(String hostmask) {
    if (hostmask == null) return false;
    String hm = hostmask.trim();
    if (hm.isEmpty()) return false;

    int at = hm.indexOf('@');
    if (at <= 0 || at >= hm.length() - 1) return false;

    int bang = hm.indexOf('!');
    String user;
    if (bang >= 0) {
      if (bang == 0 || bang >= at - 1) return false;
      user = hm.substring(bang + 1, at).trim();
    } else {
      user = hm.substring(0, at).trim();
    }

    String host = hm.substring(at + 1).trim();
    boolean userUnknown = user.isEmpty() || "*".equals(user);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(userUnknown && hostUnknown);
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

      // If we already know the hostmask, seed the dialog with the full hostmask.
      // Otherwise, fall back to a nick-based pattern.
      String hm = ni == null ? "" : Objects.toString(ni.hostmask(), "").trim();
      String seed = IgnoreListService.normalizeMaskOrNickToHostmask(isUsefulHostmask(hm) ? hm : nick);
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

      // Update UI indicators immediately.
      refreshIgnoreCache(true);
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

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Users"; }
}
