package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.ConnectionCoordinator;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Handles outbound "chatty" slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /join, /part, /nick, /away, /query, /msg, /notice, /me, /topic, /kick,
 * /invite, /names, /who, /list, /say, /quote.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
public class OutboundChatCommandService {

  private static final DateTimeFormatter CHATHISTORY_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withZone(ZoneOffset.UTC);

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final RuntimeConfigStore runtimeConfig;
  private final AwayRoutingState awayRoutingState;
  private final JoinRoutingState joinRoutingState;
  private final PendingEchoMessageState pendingEchoMessageState;

  public OutboundChatCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      RuntimeConfigStore runtimeConfig,
      AwayRoutingState awayRoutingState,
      JoinRoutingState joinRoutingState,
      PendingEchoMessageState pendingEchoMessageState) {
    this.irc = irc;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.runtimeConfig = runtimeConfig;
    this.awayRoutingState = awayRoutingState;
    this.joinRoutingState = joinRoutingState;
    this.pendingEchoMessageState = pendingEchoMessageState;
  }

  public void handleJoin(CompositeDisposable disposables, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(join)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    if (chan.isEmpty()) {
      ui.appendStatus(at, "(join)", "Usage: /join <#channel>");
      return;
    }

    // Persist for auto-join next time.
    runtimeConfig.rememberJoinedChannel(at.serverId(), chan);

    // Remember where the user initiated the join so join-failure numerics can be routed
    // back to the correct buffer (and also to status).
    joinRoutingState.rememberOrigin(at.serverId(), chan, at);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected (join queued in config only)");
      return;
    }

    disposables.add(
        irc.joinChannel(at.serverId(), chan)
            .subscribe(
                () -> {},
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(join-error)", String.valueOf(err))));
  }

  public void handlePart(CompositeDisposable disposables, String channel, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(part)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String msg = reason == null ? "" : reason.trim();

    TargetRef status = new TargetRef(at.serverId(), "status");

    // If no explicit channel was provided, we can only /part if the active target is a channel.
    TargetRef target;
    if (chan.isEmpty()) {
      if (!at.isChannel()) {
        ui.appendStatus(at, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
        return;
      }
      target = at;
    } else {
      target = new TargetRef(at.serverId(), chan);
      if (!target.isChannel()) {
        ui.appendStatus(at, "(part)", "Usage: /part [#channel] [reason]");
        return;
      }
    }

    // Remove from auto-join for next startup.
    runtimeConfig.forgetJoinedChannel(target.serverId(), target.target());

    if (!connectionCoordinator.isConnected(target.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      // Still close the local buffer so the UX matches normal /part.
      ui.closeTarget(target);
      return;
    }

    // If we're parting the currently active target, switch back to status first.
    if (target.equals(at)) {
      ui.selectTarget(status);
    }

    disposables.add(
        irc.partChannel(target.serverId(), target.target(), msg.isBlank() ? null : msg)
            .subscribe(
                () -> ui.appendStatus(status, "(part)", "Left " + target.target()),
                err -> ui.appendError(status, "(part-error)", String.valueOf(err))));

    ui.closeTarget(target);
  }

  public void handleNick(CompositeDisposable disposables, String newNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(at, "(nick)", "Usage: /nick <newNick>");
      return;
    }

    // Persist the preferred nick for next time.
    runtimeConfig.rememberNick(at.serverId(), nick);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    disposables.add(
        irc.changeNick(at.serverId(), nick)
            .subscribe(
                () -> ui.appendStatus(new TargetRef(at.serverId(), "status"), "(nick)", "Requested nick change to " + nick),
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(nick-error)", String.valueOf(err))));
  }

  public void handleAway(CompositeDisposable disposables, String message) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(away)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");

    String msg = message == null ? "" : message.trim();

    boolean explicitClear = "-".equals(msg) || "off".equalsIgnoreCase(msg) || "clear".equalsIgnoreCase(msg);

    boolean clear;
    String toSend;

    // Bare /away toggles: if not away, set a default; if already away, clear it.
    if (msg.isEmpty()) {
      boolean currentlyAway = awayRoutingState.isAway(at.serverId());
      if (currentlyAway) {
        clear = true;
        toSend = "";
      } else {
        clear = false;
        toSend = "Gone for now.";
      }
    } else if (explicitClear) {
      clear = true;
      toSend = "";
    } else {
      clear = false;
      toSend = msg;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    // Remember where the user initiated /away so the server confirmation (305/306)
    // can be printed into the same buffer.
    awayRoutingState.rememberOrigin(at.serverId(), at);

    // Store the requested reason immediately so the upcoming 306 confirmation can
    // include it, even if the numeric arrives before the Completable callback runs.
    // If the request fails, we restore the previous value in the error handler.
    String prevReason = awayRoutingState.getLastReason(at.serverId());
    if (clear) awayRoutingState.setLastReason(at.serverId(), null);
    else awayRoutingState.setLastReason(at.serverId(), toSend);

    disposables.add(
        irc.setAway(at.serverId(), toSend)
            .subscribe(
                () -> {
                  awayRoutingState.setAway(at.serverId(), !clear);
                  ui.appendStatus(status, "(away)", clear ? "Away cleared" : ("Away set: " + toSend));
                },
                err -> {
                  awayRoutingState.setLastReason(at.serverId(), prevReason);
                  ui.appendError(status, "(away-error)", String.valueOf(err));
                }));
  }

  public void handleQuery(String nick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(query)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(query)", "Usage: /query <nick>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  public void handleMsg(CompositeDisposable disposables, String nick, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(msg)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(msg)", "Usage: /msg <nick> <message>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
    sendMessage(disposables, pm, m);
  }

  
  public void handleNotice(CompositeDisposable disposables, String target, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(notice)", "Select a server first.");
      return;
    }

    String t = target == null ? "" : target.trim();
    String m = body == null ? "" : body.trim();
    if (t.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(notice)", "Usage: /notice <target> <message>");
      return;
    }

    sendNotice(disposables, at, t, m);
  }

public void handleMe(CompositeDisposable disposables, String action) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(me)", "Select a server first.");
      return;
    }

    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus(at, "(me)", "Usage: /me <action>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(me)", "Select a channel or PM first.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (shouldUseLocalEcho(at.serverId())) {
      String me = irc.currentNick(at.serverId()).orElse("me");
      ui.appendAction(at, me, a, true);
    }

    disposables.add(
        irc.sendAction(at.serverId(), at.target(), a)
            .subscribe(
                () -> {},
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(send-error)", String.valueOf(err))));
  }

  public void handleTopic(CompositeDisposable disposables, String first, String rest) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(topic)", "Select a server first.");
      return;
    }

    String f = first == null ? "" : first.trim();
    String r = rest == null ? "" : rest.trim();

    String channel;
    String topicText = "";
    boolean settingTopic;

    if (f.startsWith("#") || f.startsWith("&")) {
      channel = f;
      topicText = r;
      settingTopic = !topicText.isEmpty();
    } else if (at.isChannel()) {
      channel = at.target();
      topicText = (f + (r.isEmpty() ? "" : " " + r)).trim();
      settingTopic = !topicText.isEmpty();
    } else {
      ui.appendStatus(at, "(topic)", "Usage: /topic [#channel] [new topic...]");
      ui.appendStatus(at, "(topic)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(channel) || containsCrlf(topicText)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(topic)", "Refusing to send multi-line /topic input.");
      return;
    }

    String line = settingTopic ? ("TOPIC " + channel + " :" + topicText) : ("TOPIC " + channel);
    TargetRef out = new TargetRef(at.serverId(), channel);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(topic)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(topic-error)", String.valueOf(err))));
  }

  public void handleKick(CompositeDisposable disposables, String channel, String nick, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(kick)", "Select a server first.");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    String n = nick == null ? "" : nick.trim();
    String rsn = reason == null ? "" : reason.trim();

    if (ch == null || n.isEmpty()) {
      ui.appendStatus(at, "(kick)", "Usage: /kick [#channel] <nick> [reason]");
      ui.appendStatus(at, "(kick)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch) || containsCrlf(n) || containsCrlf(rsn)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(kick)", "Refusing to send multi-line /kick input.");
      return;
    }

    String line = "KICK " + ch + " " + n + (rsn.isEmpty() ? "" : " :" + rsn);
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(kick)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(kick-error)", String.valueOf(err))));
  }

  public void handleInvite(CompositeDisposable disposables, String nick, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(invite)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String ch = resolveChannelOrNull(at, channel);

    if (n.isEmpty() || ch == null) {
      ui.appendStatus(at, "(invite)", "Usage: /invite <nick> [#channel]");
      ui.appendStatus(at, "(invite)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch) || containsCrlf(n)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(invite)", "Refusing to send multi-line /invite input.");
      return;
    }

    String line = "INVITE " + n + " " + ch;
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(invite)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  public void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(names)", "Select a server first.");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(at, "(names)", "Usage: /names [#channel]");
      ui.appendStatus(at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(names)", "Refusing to send multi-line /names input.");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(names)", "Requesting NAMES for " + ch + "...");

    disposables.add(
        irc.requestNames(at.serverId(), ch)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(names-error)", String.valueOf(err))));
  }

  public void handleWho(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(who)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (a.isEmpty()) {
      if (at.isChannel()) {
        a = at.target();
      } else {
        ui.appendStatus(at, "(who)", "Usage: /who [mask|#channel] [flags]");
        ui.appendStatus(at, "(who)", "Tip: from a channel tab, /who defaults to that channel.");
        return;
      }
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(a)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(who)", "Refusing to send multi-line /who input.");
      return;
    }

    String line = "WHO " + a;
    String firstToken = a;
    int sp = firstToken.indexOf(' ');
    if (sp >= 0) firstToken = firstToken.substring(0, sp).trim();

    TargetRef out = (firstToken.startsWith("#") || firstToken.startsWith("&"))
        ? new TargetRef(at.serverId(), firstToken)
        : new TargetRef(at.serverId(), "status");
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(who)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(who-error)", String.valueOf(err))));
  }

  public void handleList(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(list)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(a)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(list)", "Refusing to send multi-line /list input.");
      return;
    }

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(list)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(list-error)", String.valueOf(err))));
  }

  public void handleSay(CompositeDisposable disposables, String msg) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      sendRawFromStatus(disposables, at.serverId(), m);
      return;
    }

    sendMessage(disposables, at, m);
  }

  private void sendRawFromStatus(CompositeDisposable disposables, String serverId, String rawLine) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) return;

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(raw)", "Refusing to send multi-line input.");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    ui.appendStatus(status, "(raw)", "→ " + redactIfSensitive(line));

    disposables.add(
        irc.sendRaw(sid, line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(raw-error)", String.valueOf(err))));
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit) {
    handleChatHistoryBefore(disposables, limit, "");
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(chathistory)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");

    // Require a real target buffer (channel or query), not status or UI-only.
    if (at.isStatus()) {
      ui.appendStatus(status, "(chathistory)", "Select a channel or query first.");
      return;
    }
    if (at.isUiOnly()) {
      ui.appendStatus(status, "(chathistory)", "That view does not support history requests.");
      return;
    }

    int lim = limit;
    String selectorToken = normalizeChatHistorySelector(selector);
    if (lim <= 0) {
      ui.appendStatus(at, "(chathistory)", "Usage: /chathistory [limit] | /chathistory [before] <msgid=...|timestamp=...> [limit]");
      ui.appendStatus(at, "(chathistory)", "Example: /chathistory 100");
      ui.appendStatus(at, "(chathistory)", "Example: /chathistory msgid=abc123 100");
      return;
    }
    if (!Objects.toString(selector, "").trim().isEmpty() && selectorToken.isEmpty()) {
      ui.appendStatus(at, "(chathistory)", "Selector must be msgid=... or timestamp=...");
      return;
    }
    if (lim > 200) lim = 200;

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (selectorToken.isEmpty()) {
      selectorToken = "timestamp=" + CHATHISTORY_TS_FMT.format(Instant.now());
    }
    String preview = "CHATHISTORY BEFORE " + at.target() + " " + selectorToken + " " + lim;
    ui.appendStatus(at, "(chathistory)", "Requesting older history… limit=" + lim);
    ui.appendStatus(at, "(chathistory)", "→ " + preview);

    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    disposables.add(
        irc.requestChatHistoryBefore(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }
  public void handleQuote(CompositeDisposable disposables, String rawLine) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(quote)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) {
      ui.appendStatus(at, "(quote)", "Usage: /quote <RAW IRC LINE>");
      ui.appendStatus(at, "(quote)", "Example: /quote MONITOR +nick");
      ui.appendStatus(at, "(quote)", "Alias: /raw <RAW IRC LINE>");
      return;
    }

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(quote)", "Refusing to send multi-line /quote input.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    String echo = redactIfSensitive(line);
    ui.appendStatus(status, "(quote)", "→ " + echo);

    disposables.add(
        irc.sendRaw(at.serverId(), line)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(quote-error)", String.valueOf(err))));
  }

  private void sendMessage(CompositeDisposable disposables, TargetRef target, String message) {
    if (target == null) return;
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    String me = irc.currentNick(target.serverId()).orElse("me");
    final PendingEchoMessageState.PendingOutboundChat pendingEntry;
    if (useLocalEcho) {
      pendingEntry = null;
    } else {
      pendingEntry = pendingEchoMessageState.register(target, me, m, Instant.now());
      ui.appendPendingOutgoingChat(target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, m);
    }

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m)
            .subscribe(
                () -> {},
                err -> {
                  if (pendingEntry != null) {
                    pendingEchoMessageState.removeById(pendingEntry.pendingId());
                    ui.failPendingOutgoingChat(
                        target,
                        pendingEntry.pendingId(),
                        Instant.now(),
                        pendingEntry.fromNick(),
                        pendingEntry.text(),
                        String.valueOf(err));
                  }
                  ui.appendError(targetCoordinator.safeStatusTarget(), "(send-error)", String.valueOf(err));
                }));

    if (useLocalEcho) {
      ui.appendChat(target, "(" + me + ")", m, true);
    }
  }
  private void sendNotice(CompositeDisposable disposables, TargetRef echoTarget, String target, String message) {
    if (echoTarget == null) return;
    String t = target == null ? "" : target.trim();
    String m = message == null ? "" : message.trim();
    if (t.isEmpty() || m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(echoTarget.serverId())) {
      TargetRef status = new TargetRef(echoTarget.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!echoTarget.isStatus()) {
        ui.appendStatus(echoTarget, "(conn)", "Not connected");
      }
      return;
    }

    disposables.add(
        irc.sendNotice(echoTarget.serverId(), t, m)
            .subscribe(
                () -> {},
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(send-error)", String.valueOf(err))));

    if (shouldUseLocalEcho(echoTarget.serverId())) {
      String me = irc.currentNick(echoTarget.serverId()).orElse("me");
      ui.appendNotice(echoTarget, "(" + me + ")", "NOTICE → " + t + ": " + m);
    }
  }



  private static String redactIfSensitive(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return s;

    int sp = s.indexOf(' ');
    String head = (sp < 0 ? s : s.substring(0, sp)).trim();
    String upper = head.toUpperCase(Locale.ROOT);
    if (upper.equals("PASS") || upper.equals("OPER") || upper.equals("AUTHENTICATE")) {
      return upper + (sp < 0 ? "" : " <redacted>");
    }
    return s;
  }

  private static String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) {
      if (ch.startsWith("#") || ch.startsWith("&")) return ch;
      return null;
    }
    if (active != null && active.isChannel()) return active.target();
    return null;
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }

  private static String normalizeChatHistorySelector(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return "";
    int eq = s.indexOf('=');
    if (eq <= 0 || eq == s.length() - 1) return "";
    String key = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
    String value = s.substring(eq + 1).trim();
    if (value.isEmpty()) return "";
    if (value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return "";
    if (!"msgid".equals(key) && !"timestamp".equals(key)) return "";
    return key + "=" + value;
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !irc.isEchoMessageAvailable(serverId);
  }
}
