package cafe.woden.ircclient.ui.userlist;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreMaskMatcher;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.awt.Component;
import java.awt.Window;
import java.util.Objects;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class UserListIgnorePromptHandler {
  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;
  private final Dialogs dialogs;

  public UserListIgnorePromptHandler(
      IgnoreListService ignoreListService, IgnoreStatusService ignoreStatusService) {
    this(ignoreListService, ignoreStatusService, new JOptionPaneDialogs());
  }

  UserListIgnorePromptHandler(
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      Dialogs dialogs) {
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
    this.dialogs = dialogs;
  }

  public boolean prompt(
      Component parent,
      TargetRef active,
      NickInfo nickInfo,
      String nick,
      boolean removing,
      boolean soft) {
    try {
      if (active == null || active.serverId() == null || active.serverId().isBlank()) return false;
      String normalizedNick = Objects.toString(nick, "").trim();
      if (normalizedNick.isEmpty()) return false;

      String hostmask = nickInfo == null ? "" : Objects.toString(nickInfo.hostmask(), "").trim();
      String seedBase =
          (ignoreStatusService == null)
              ? (IgnoreMaskMatcher.isUsefulHostmask(hostmask) ? hostmask : normalizedNick)
              : ignoreStatusService.bestSeedForMask(active.serverId(), normalizedNick, hostmask);
      String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);
      IgnoreDialogCopy copy = dialogCopy(removing, soft);
      Component owner = dialogOwner(parent);

      String input = dialogs.showInput(owner, copy.prompt(), copy.title(), seed);
      if (input == null) return false;
      String arg = input.trim();
      if (arg.isEmpty()) return false;

      boolean changed = applyChange(active.serverId(), arg, removing, soft);
      String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
      dialogs.showInfo(owner, resultMessage(changed, stored, removing, soft), copy.title());
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean applyChange(String serverId, String arg, boolean removing, boolean soft) {
    if (removing) {
      if (soft) {
        return ignoreListService != null && ignoreListService.removeSoftMask(serverId, arg);
      }
      return ignoreListService != null && ignoreListService.removeMask(serverId, arg);
    }
    if (soft) {
      return ignoreListService != null && ignoreListService.addSoftMask(serverId, arg);
    }
    return ignoreListService != null && ignoreListService.addMask(serverId, arg);
  }

  private static String resultMessage(
      boolean changed, String stored, boolean removing, boolean soft) {
    if (soft) {
      if (removing) {
        return changed
            ? ("Removed soft ignore: " + stored)
            : ("Not in soft-ignore list: " + stored);
      }
      return changed ? ("Soft ignoring: " + stored) : ("Already soft-ignored: " + stored);
    }
    if (removing) {
      return changed ? ("Removed ignore: " + stored) : ("Not in ignore list: " + stored);
    }
    return changed ? ("Ignoring: " + stored) : ("Already ignored: " + stored);
  }

  private static IgnoreDialogCopy dialogCopy(boolean removing, boolean soft) {
    if (soft) {
      return removing
          ? new IgnoreDialogCopy("Soft Unignore", "Remove soft-ignore mask (per-server):")
          : new IgnoreDialogCopy("Soft Ignore", "Add soft-ignore mask (per-server):");
    }
    return removing
        ? new IgnoreDialogCopy("Unignore", "Remove ignore mask (per-server):")
        : new IgnoreDialogCopy("Ignore", "Add ignore mask (per-server):");
  }

  private static Component dialogOwner(Component parent) {
    if (parent == null) return null;
    Window owner = SwingUtilities.getWindowAncestor(parent);
    return owner != null ? owner : parent;
  }

  private record IgnoreDialogCopy(String title, String prompt) {}

  interface Dialogs {
    String showInput(Component parent, String prompt, String title, String seed);

    void showInfo(Component parent, String message, String title);
  }

  static final class JOptionPaneDialogs implements Dialogs {
    @Override
    public String showInput(Component parent, String prompt, String title, String seed) {
      return (String)
          JOptionPane.showInputDialog(
              parent, prompt, title, JOptionPane.PLAIN_MESSAGE, null, null, seed);
    }

    @Override
    public void showInfo(Component parent, String message, String title) {
      JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
  }
}
