package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UserActionRequest;
import javax.swing.JMenu;
import java.util.Objects;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.springframework.stereotype.Component;

/** Builds the standard context menu used when right-clicking a nickname. */
@Component
public final class NickContextMenuFactory {

  public enum DccAction {
    CHAT,
    SEND_FILE,
    ACCEPT_CHAT,
    GET_FILE,
    CLOSE_CHAT
  }

  /**
   * A simple marker used to enable/disable ignore-related menu items.
   *
   * @param hard whether the nick is currently hard-ignored
   * @param soft whether the nick is currently soft-ignored
   */
  public record IgnoreMark(boolean hard, boolean soft) {}

  public interface Callbacks {
    void openQuery(TargetRef ctx, String nick);

    void emitUserAction(TargetRef ctx, String nick, UserActionRequest.Action action);

    void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft);

    void requestDccAction(TargetRef ctx, String nick, DccAction action);
  }

  public static final class NickContextMenu {
    private final Callbacks callbacks;

    private final JPopupMenu menu = new JPopupMenu();
    private final JMenuItem openQuery = new JMenuItem("Open Query");
    private final JMenuItem whois = new JMenuItem("Whois");
    private final JMenuItem version = new JMenuItem("Version");
    private final JMenuItem ping = new JMenuItem("Ping");
    private final JMenuItem time = new JMenuItem("Time");
    private final JMenu dcc = new JMenu("DCC");
    private final JMenuItem dccChat = new JMenuItem("Start Chat");
    private final JMenuItem dccSend = new JMenuItem("Send File...");
    private final JMenuItem dccAccept = new JMenuItem("Accept Chat Offer");
    private final JMenuItem dccGet = new JMenuItem("Get Pending File");
    private final JMenuItem dccClose = new JMenuItem("Close Chat");
    private final JMenuItem op = new JMenuItem("Op");
    private final JMenuItem deop = new JMenuItem("Deop");
    private final JMenuItem voice = new JMenuItem("Voice");
    private final JMenuItem devoice = new JMenuItem("Devoice");
    private final JMenuItem kick = new JMenuItem("Kick");
    private final JMenuItem ban = new JMenuItem("Ban");
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
      dcc.add(dccChat);
      dcc.add(dccSend);
      dcc.addSeparator();
      dcc.add(dccAccept);
      dcc.add(dccGet);
      dcc.add(dccClose);
      menu.add(dcc);
      menu.addSeparator();
      menu.add(op);
      menu.add(deop);
      menu.add(voice);
      menu.add(devoice);
      menu.add(kick);
      menu.add(ban);
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
      dccChat.addActionListener(e -> safe(() -> callbacks.requestDccAction(popupCtx, popupNick, DccAction.CHAT)));
      dccSend.addActionListener(e -> safe(() -> callbacks.requestDccAction(popupCtx, popupNick, DccAction.SEND_FILE)));
      dccAccept.addActionListener(e -> safe(() -> callbacks.requestDccAction(popupCtx, popupNick, DccAction.ACCEPT_CHAT)));
      dccGet.addActionListener(e -> safe(() -> callbacks.requestDccAction(popupCtx, popupNick, DccAction.GET_FILE)));
      dccClose.addActionListener(e -> safe(() -> callbacks.requestDccAction(popupCtx, popupNick, DccAction.CLOSE_CHAT)));
      op.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.OP)));
      deop.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.DEOP)));
      voice.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.VOICE)));
      devoice.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.DEVOICE)));
      kick.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.KICK)));
      ban.addActionListener(e -> safe(() -> callbacks.emitUserAction(popupCtx, popupNick, UserActionRequest.Action.BAN)));

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
      dcc.setEnabled(canAct);
      dccChat.setEnabled(canAct);
      dccSend.setEnabled(canAct);
      dccAccept.setEnabled(canAct);
      dccGet.setEnabled(canAct);
      dccClose.setEnabled(canAct);
      boolean canModerate = canAct && ctx.isChannel();
      op.setEnabled(canModerate);
      deop.setEnabled(canModerate);
      voice.setEnabled(canModerate);
      devoice.setEnabled(canModerate);
      kick.setEnabled(canModerate);
      ban.setEnabled(canModerate);

      boolean hard = mark != null && mark.hard;
      boolean soft = mark != null && mark.soft;

      ignore.setEnabled(canAct);
      unignore.setEnabled(canAct && hard);
      softIgnore.setEnabled(canAct);
      softUnignore.setEnabled(canAct && soft);

      return menu;
    }
  }

  public NickContextMenu create(Callbacks callbacks) {
    return new NickContextMenu(callbacks);
  }
}
