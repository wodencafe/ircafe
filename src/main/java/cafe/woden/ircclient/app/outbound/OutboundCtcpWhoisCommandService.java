package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.ConnectionCoordinator;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Handles outbound WHOIS/WHOWAS and CTCP actions.
 *
 * <p>Extracted from {@code IrcMediator} to keep the mediator focused on wiring and inbound routing.
 * Behavior is intended to match the previous in-mediator implementation.
 */
@Component
public class OutboundCtcpWhoisCommandService {

  private final UiPort ui;
  private final IrcClientService irc;
  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;
  private final CtcpRoutingState ctcpRoutingState;
  private final WhoisRoutingState whoisRoutingState;

  public OutboundCtcpWhoisCommandService(
      UiPort ui,
      IrcClientService irc,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      CtcpRoutingState ctcpRoutingState,
      WhoisRoutingState whoisRoutingState) {
    this.ui = ui;
    this.irc = irc;
    this.targetCoordinator = targetCoordinator;
    this.connectionCoordinator = connectionCoordinator;
    this.ctcpRoutingState = ctcpRoutingState;
    this.whoisRoutingState = whoisRoutingState;
  }


  public void requestWhois(CompositeDisposable disposables, TargetRef ctx, String nick) {
    if (ctx == null) return;
    String sid = ctx.serverId();
    String n = nick == null ? "" : nick.trim();
    if (sid == null || sid.isBlank() || n.isBlank()) return;

    ui.ensureTargetExists(ctx);

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(new TargetRef(sid, "status"), "(conn)", "Not connected");
      return;
    }

    whoisRoutingState.put(sid, n, ctx);
    ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + n + "...");

    disposables.add(
        irc.whois(sid, n).subscribe(
            () -> {},
            err -> ui.appendError(ctx, "(whois)", String.valueOf(err))
        )
    );
  }

  public void ctcpVersion(CompositeDisposable disposables, TargetRef ctx, String nick) {
    sendCtcpForUserAction(disposables, ctx, nick, "VERSION", "", null, "→ " + nick + " VERSION");
  }

  public void ctcpTime(CompositeDisposable disposables, TargetRef ctx, String nick) {
    sendCtcpForUserAction(disposables, ctx, nick, "TIME", "", null, "→ " + nick + " TIME");
  }

  public void ctcpPing(CompositeDisposable disposables, TargetRef ctx, String nick) {
    // Token lets us compute RTT when the reply comes back.
    String token = Long.toString(System.currentTimeMillis());
    // Preserve previous UX: do not show the token in the status line for the context-menu ping.
    sendCtcpForUserAction(disposables, ctx, nick, "PING", token, token, "→ " + nick + " PING");
  }


  public void handleWhois(CompositeDisposable disposables, String nick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(whois)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(whois)", "Usage: /whois <nick>");
      return;
    }

    requestWhois(disposables, at, n);
  }

  public void handleWhowas(CompositeDisposable disposables, String nick, int count) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(whowas)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(whowas)", "Usage: /whowas <nick> [count]");
      return;
    }
    if (count < 0) {
      ui.appendStatus(at, "(whowas)", "Usage: /whowas <nick> [count]");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    ui.ensureTargetExists(at);
    if (count > 0) {
      ui.appendStatus(at, "(whowas)", "Requesting WHOWAS for " + n + " (" + count + ")...");
    } else {
      ui.appendStatus(at, "(whowas)", "Requesting WHOWAS for " + n + "...");
    }

    disposables.add(
        irc.whowas(at.serverId(), n, count).subscribe(
            () -> {},
            err -> ui.appendError(at, "(whowas)", String.valueOf(err))
        )
    );
  }


  public void handleCtcpVersion(CompositeDisposable disposables, String nick) {
    sendCtcpSlash(disposables, "VERSION", nick, "", false);
  }

  public void handleCtcpPing(CompositeDisposable disposables, String nick) {
    // Token lets us compute RTT when the reply comes back.
    String token = Long.toString(System.currentTimeMillis());
    sendCtcpSlash(disposables, "PING", nick, token, true);
  }

  public void handleCtcpTime(CompositeDisposable disposables, String nick) {
    sendCtcpSlash(disposables, "TIME", nick, "", false);
  }

  public void handleCtcp(CompositeDisposable disposables, String nick, String command, String args) {
    String n = nick == null ? "" : nick.trim();
    String cmd = command == null ? "" : command.trim();
    String a = args == null ? "" : args.trim();

    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ctcp)", "Select a server first.");
      return;
    }

    TargetRef ctx = at;

    if (n.isEmpty() || cmd.isEmpty()) {
      ui.appendStatus(at, "(ctcp)", "Usage: /ctcp <nick> <command> [args...]");
      return;
    }

    String cmdU = cmd.toUpperCase(Locale.ROOT);

    // Convenience: treat /ctcp nick PING [token] as RTT-measurable.
    if ("PING".equals(cmdU)) {
      final String payload;
      final String tokenKey;
      if (a.isEmpty()) {
        payload = Long.toString(System.currentTimeMillis());
        tokenKey = payload;
      } else {
        payload = a;
        int sp = a.indexOf(' ');
        tokenKey = (sp >= 0) ? a.substring(0, sp) : a;
      }
      sendCtcp(disposables, at, ctx, n, "PING", payload, tokenKey, null);
      return;
    }

    // If you want /ctcp nick ACTION ... use /me instead.
    if ("ACTION".equals(cmdU)) {
      ui.appendStatus(at, "(ctcp)", "Use /me for ACTION.");
      return;
    }

    sendCtcp(disposables, at, ctx, n, cmdU, a, null, null);
  }

  private void sendCtcpSlash(CompositeDisposable disposables, String cmdUpper, String nick, String args, boolean expectsReply) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ctcp)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String a = args == null ? "" : args.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(ctcp)",
          "Usage: /" + cmdUpper.toLowerCase(Locale.ROOT) + " <nick>");
      return;
    }

    sendCtcp(disposables, at, at, n, cmdUpper, a, expectsReply ? a : null, null);
  }

  private void sendCtcpForUserAction(
      CompositeDisposable disposables,
      TargetRef ctx,
      String nick,
      String cmdUpper,
      String args,
      String tokenKey,
      String displayOverride) {

    if (ctx == null) return;
    TargetRef serverCtx = ctx;
    sendCtcp(disposables, serverCtx, ctx, nick, cmdUpper, args, tokenKey, displayOverride);
  }

  private void sendCtcp(
      CompositeDisposable disposables,
      TargetRef serverCtx,
      TargetRef outputCtx,
      String nick,
      String cmdUpper,
      String args,
      String tokenKey,
      String displayOverride) {

    if (serverCtx == null) return;
    String sid = serverCtx.serverId();

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(new TargetRef(sid, "status"), "(conn)", "Not connected");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return;

    String cmd = cmdUpper == null ? "" : cmdUpper.trim().toUpperCase(Locale.ROOT);
    String a = args == null ? "" : args.trim();

    TargetRef ctx = outputCtx != null ? outputCtx : new TargetRef(sid, "status");
    ui.ensureTargetExists(ctx);

    StringBuilder inner = new StringBuilder(cmd);
    if (!a.isEmpty()) inner.append(' ').append(a);
    String ctcp = "\u0001" + inner + "\u0001";

    // Track pending so replies can be routed back to the current context.
    ctcpRoutingState.put(sid, n, cmd, tokenKey, ctx);

    String display = displayOverride != null
        ? displayOverride
        : ("→ " + n + " " + cmd + (a.isEmpty() ? "" : " " + a));
    ui.appendStatus(ctx, "(ctcp)", display);

    disposables.add(
        irc.sendPrivateMessage(sid, n, ctcp).subscribe(
            () -> {},
            err -> ui.appendError(ctx, "(ctcp-error)", String.valueOf(err))
        )
    );
  }
}
