package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UserActionRequest;
import java.util.Objects;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.springframework.stereotype.Component;

/**
 * Builds the standard context menu used when right-clicking a nickname.
 *
 * <p>The goal is to keep the menu options consistent across surfaces
 * (chat transcript, user list, etc.), while letting each surface provide
 * its own lookup/policy hooks (ignore prompt, CTCP dispatch, etc.).
 */
@Component
public final class NickContextMenuFactory {

  /**
   * A simple marker used to enable/disable ignore-related menu items.
   *
   * @param hard whether the nick is currently hard-ignored
   * @param soft whether the nick is currently soft-ignored
   */
  public record IgnoreMark(boolean hard, boolean soft) {}

  /**
   * Callbacks required for menu actions.
   *
   * <p>Implementations should be defensive: menu code should never crash the UI.
   */
  public interface Callbacks {
    void openQuery(TargetRef ctx, String nick);

    void emitUserAction(TargetRef ctx, String nick, UserActionRequest.Action action);

    void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft);
  }

  /**
   * A stateful menu instance. Each view creates one instance and reuses it.
   */
  public static final class NickContextMenu {
    private final Callbacks callbacks;

    private final JPopupMenu menu = new JPopupMenu();
    private final JMenuItem openQuery = new JMenuItem("Open Query");
    private final JMenuItem whois = new JMenuItem("Whois");
    private final JMenuItem version = new JMenuItem("Version");
    private final JMenuItem ping = new JMenuItem("Ping");
    private final JMenuItem time = new JMenuItem("Time");
    private final JMenuItem ignore = new JMenuItem("Ignore...");
    private final JMenuItem unignore = new JMenuItem("Unignore...");
    private final JMenuItem softIgnore = new JMenuItem("Soft Ignore...");
    private final JMenuItem softUnignore = new JMenuItem("Soft Unignore...");

    private volatile TargetRef popupCtx;
    private volatile String popupNick;

    private NickContextMenu(Callbacks callbacks) {
      this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
      buildMenu();
      wireActions();
    }

    private void buildMenu() {
      menu.add(openQuery);
      menu.addSeparator();

      menu.add(whois);
      menu.add(version);
      menu.add(ping);
      menu.add(time);

      menu.addSeparator();
      menu.add(ignore);
      menu.add(unignore);
      menu.addSeparator();
      menu.add(softIgnore);
      menu.add(softUnignore);
    }

    private void wireActions() {
      openQuery.addActionListener(e -> safe(() -> callbacks.openQuery(popupCtx, popupNick)));

      whois.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.WHOIS)));
      version.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.CTCP_VERSION)));
      ping.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.CTCP_PING)));
      time.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.CTCP_TIME)));

      ignore.addActionListener(e -> safe(() -> callbacks.promptIgnore(popupCtx, popupNick, false, false)));
      unignore.addActionListener(e -> safe(() -> callbacks.promptIgnore(popupCtx, popupNick, true, false)));
      softIgnore.addActionListener(e -> safe(() -> callbacks.promptIgnore(popupCtx, popupNick, false, true)));
      softUnignore.addActionListener(e -> safe(() -> callbacks.promptIgnore(popupCtx, popupNick, true, true)));
    }

    private static void safe(Runnable r) {
      try {
        r.run();
      } catch (Exception ignored) {
        // Context menus should never crash the UI.
      }
    }

    /**
     * Prepares the menu for a particular nick + context, updates enabled state, and returns the menu.
     */
    public JPopupMenu forNick(TargetRef ctx, String nick, IgnoreMark mark) {
      // This menu is not part of any visible component tree, so it won't automatically
      // receive Look-and-Feel updates when the main frame is updated. Ensure that the
      // current LAF (e.g. FlatLaf) is applied before showing.
      try {
        SwingUtilities.updateComponentTreeUI(menu);
      } catch (Exception ignored) {
      }

      this.popupCtx = ctx;
      this.popupNick = (nick == null) ? null : nick.trim();

      boolean hasCtx = ctx != null && ctx.serverId() != null && !ctx.serverId().isBlank();
      boolean hasNick = popupNick != null && !popupNick.isBlank();
      boolean canAct = hasCtx && hasNick;

      openQuery.setEnabled(canAct);
      whois.setEnabled(canAct);
      version.setEnabled(canAct);
      ping.setEnabled(canAct);
      time.setEnabled(canAct);

      boolean hard = mark != null && mark.hard;
      boolean soft = mark != null && mark.soft;

      ignore.setEnabled(canAct);
      unignore.setEnabled(canAct && hard);
      softIgnore.setEnabled(canAct);
      softUnignore.setEnabled(canAct && soft);

      return menu;
    }
  }

  /** Creates a reusable nick context menu instance for a particular view/dock. */
  public NickContextMenu create(Callbacks callbacks) {
    return new NickContextMenu(callbacks);
  }
}
