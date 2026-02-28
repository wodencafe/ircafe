package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Owns nick context-menu rendering and ignore prompt flows for {@link ChatDockable}. */
public final class ChatNickContextCoordinator {

  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;
  private final UserListStore userListStore;
  private final NickContextMenuFactory.NickContextMenu nickContextMenu;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Component dialogParent;

  public ChatNickContextCoordinator(
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      UserListStore userListStore,
      NickContextMenuFactory.NickContextMenu nickContextMenu,
      Supplier<TargetRef> activeTargetSupplier,
      Component dialogParent) {
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
    this.userListStore = Objects.requireNonNull(userListStore, "userListStore");
    this.nickContextMenu = Objects.requireNonNull(nickContextMenu, "nickContextMenu");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.dialogParent = Objects.requireNonNull(dialogParent, "dialogParent");
  }

  public JPopupMenu nickContextMenuFor(String nick) {
    if (nick == null || nick.isBlank()) return null;

    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null) return null;

    String sid = Objects.toString(activeTarget.serverId(), "").trim();
    if (sid.isEmpty()) return null;

    String normalizedNick = nick.trim();
    if (normalizedNick.isEmpty()) return null;

    NickInfo nickInfo = findNickInfo(activeTarget, normalizedNick);
    String hostmask = Objects.toString(nickInfo == null ? "" : nickInfo.hostmask(), "").trim();

    IgnoreStatusService.Status status =
        (ignoreStatusService == null)
            ? new IgnoreStatusService.Status(false, false, false, "")
            : ignoreStatusService.status(sid, normalizedNick, hostmask);

    return nickContextMenu.forNick(
        activeTarget,
        normalizedNick,
        new NickContextMenuFactory.IgnoreMark(status.hard(), status.soft()));
  }

  public void promptIgnore(TargetRef target, String nick, boolean removing, boolean soft) {
    if (ignoreListService == null) return;
    if (target == null) return;
    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    String normalizedNick = Objects.toString(nick, "").trim();
    if (normalizedNick.isEmpty()) return;

    NickInfo nickInfo = findNickInfo(target, normalizedNick);
    String hostmask = Objects.toString(nickInfo == null ? "" : nickInfo.hostmask(), "").trim();
    String seedBase =
        (ignoreStatusService == null)
            ? normalizedNick
            : ignoreStatusService.bestSeedForMask(sid, normalizedNick, hostmask);
    String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);

    String title;
    String message;
    if (soft) {
      title = removing ? "Remove soft ignore" : "Soft ignore";
      message =
          removing
              ? "Remove soft ignore for <b>"
                  + escapeHtml(normalizedNick)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed)
              : "Soft ignore <b>"
                  + escapeHtml(normalizedNick)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed);
    } else {
      title = removing ? "Remove ignore" : "Ignore";
      message =
          removing
              ? "Remove ignore for <b>"
                  + escapeHtml(normalizedNick)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed)
              : "Ignore <b>"
                  + escapeHtml(normalizedNick)
                  + "</b>?"
                  + "<br><br><b>Mask</b>:"
                  + "<br>"
                  + escapeHtml(seed);
    }

    int result =
        JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(dialogParent),
            "<html>" + message + "</html>",
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);

    if (result != JOptionPane.OK_OPTION) return;

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
          SwingUtilities.getWindowAncestor(dialogParent),
          "Nothing changed — the ignore list already contained that mask.",
          title,
          JOptionPane.INFORMATION_MESSAGE);
    }
  }

  private NickInfo findNickInfo(TargetRef target, String nick) {
    if (target == null || nick == null || nick.isBlank()) return null;
    if (!target.isChannel()) return null;

    try {
      for (NickInfo nickInfo : userListStore.get(target.serverId(), target.target())) {
        if (nickInfo == null) continue;
        if (nickInfo.nick() == null) continue;
        if (nickInfo.nick().equalsIgnoreCase(nick)) return nickInfo;
      }
    } catch (Exception ignored) {
      // Defensive: userListStore should never throw, but context menus should never crash the UI.
    }

    return null;
  }

  private static String escapeHtml(String value) {
    String input = Objects.toString(value, "");
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
