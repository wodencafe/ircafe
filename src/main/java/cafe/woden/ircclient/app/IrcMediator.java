package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.outbound.OutboundModeCommandService;
import cafe.woden.ircclient.app.outbound.OutboundCtcpWhoisCommandService;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState.PendingCtcp;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Mediator.
 *
 * <p>Multi-server support:
 * targets are scoped to a server id via {@link TargetRef}.
 */
@Component
@Lazy
public class IrcMediator {
  private final IrcClientService irc;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final IgnoreListService ignoreListService;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CompositeDisposable disposables = new CompositeDisposable();

  // Routing/correlation state extracted from IrcMediator.
  private final WhoisRoutingState whoisRoutingState;
  private final CtcpRoutingState ctcpRoutingState;
  private final ModeRoutingState modeRoutingState;
  private final AwayRoutingState awayRoutingState;

  // Inbound MODE-related event handler (join-burst buffering + MODE pretty printing + 324 routing).
  private final InboundModeEventHandler inboundModeEventHandler;
  private final OutboundModeCommandService outboundModeCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;

  private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

  // Active target state is owned by TargetCoordinator.

  @PostConstruct
  void init() {
    start();
  }

  @PreDestroy
  void shutdown() {
    stop();
  }

  public IrcMediator(
      IrcClientService irc,
      UiPort ui,
      CommandParser commandParser,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      IgnoreListService ignoreListService,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      WhoisRoutingState whoisRoutingState,
      CtcpRoutingState ctcpRoutingState,
      ModeRoutingState modeRoutingState,
      AwayRoutingState awayRoutingState,
      InboundModeEventHandler inboundModeEventHandler,
      OutboundModeCommandService outboundModeCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService
  ) {
    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.ignoreListService = ignoreListService;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.whoisRoutingState = whoisRoutingState;
    this.ctcpRoutingState = ctcpRoutingState;
    this.modeRoutingState = modeRoutingState;
    this.awayRoutingState = awayRoutingState;
    this.inboundModeEventHandler = inboundModeEventHandler;
    this.outboundModeCommandService = outboundModeCommandService;
    this.outboundCtcpWhoisCommandService = outboundCtcpWhoisCommandService;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetSelected,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.targetActivations()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetActivated,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::openPrivateConversation,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.userActionRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleUserActionRequest,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServerIrcEvent,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString()))
    );

    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.connectAll())
    );

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.disconnectAll())
    );

    disposables.add(
        ui.connectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::connectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.disconnectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::disconnectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.closeTargetRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::closeTarget,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.clearLogRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::clearLog,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    // React to runtime server list edits.
    disposables.add(
        serverRegistry.updates()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(latest -> {
                  connectionCoordinator.onServersUpdated(latest, targetCoordinator.getActiveTarget());
                  targetCoordinator.refreshInputEnabledForActiveTarget();
                },
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", "Server list update failed: " + err))
    );
  }

  private void handleUserActionRequest(UserActionRequest req) {
    if (req == null) return;
    TargetRef ctx = req.contextTarget() != null ? req.contextTarget() : targetCoordinator.getActiveTarget();
    if (ctx == null) ctx = targetCoordinator.safeStatusTarget();
    final var fCtx = ctx;
    String sid = ctx.serverId();
    String nick = req.nick() == null ? "" : req.nick().trim();
    if (sid == null || sid.isBlank() || nick.isBlank()) return;

    switch (req.action()) {
      case OPEN_QUERY -> targetCoordinator.openPrivateConversation(new PrivateMessageRequest(sid, nick));

      case WHOIS -> {
        whoisRoutingState.put(sid, nick, ctx);
        ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + nick + "...");
        Disposable d = irc.whois(sid, nick)
            .subscribe(
                () -> {},
                err -> ui.appendError(fCtx, "(whois)", err.toString())
            );
        disposables.add(d);
      }

      case CTCP_VERSION -> {
        ctcpRoutingState.put(sid, nick, "VERSION", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " VERSION");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001VERSION\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_PING -> {
        String token = Long.toString(System.currentTimeMillis());
        ctcpRoutingState.put(sid, nick, "PING", token, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " PING");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001PING " + token + "\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_TIME -> {
        ctcpRoutingState.put(sid, nick, "TIME", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " TIME");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001TIME\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }
    }
  }



  private record ParsedCtcp(String commandUpper, String arg) {}

  private ParsedCtcp parseCtcp(String text) {
    if (text == null || text.length() < 2) return null;
    if (text.charAt(0) != 0x01 || text.charAt(text.length() - 1) != 0x01) return null;
    String inner = text.substring(1, text.length() - 1).trim();
    if (inner.isEmpty()) return null;
    int sp = inner.indexOf(' ');
    String cmd = (sp >= 0) ? inner.substring(0, sp) : inner;
    String arg = (sp >= 0) ? inner.substring(sp + 1).trim() : "";
    cmd = cmd.trim().toUpperCase(Locale.ROOT);
    return new ParsedCtcp(cmd, arg);
  }



  private void handleNoticeOrSpoiler(String sid, TargetRef status, String from, String text, boolean spoiler) {
    // CTCP replies come back as NOTICE with 0x01-wrapped payload.
    // Route them to the chat target where the request originated.
    ParsedCtcp ctcp = parseCtcp(text);
    if (ctcp != null) {
      String cmd = ctcp.commandUpper();
      String arg = ctcp.arg();

      TargetRef dest = null;
      String rendered = null;

      if ("VERSION".equals(cmd)) {
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
        if (p != null) {
          dest = p.target();
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        }
      } else if ("PING".equals(cmd)) {
        String token = arg;
        int sp = token.indexOf(' ');
        if (sp >= 0) token = token.substring(0, sp);
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, token);
        if (p != null) {
          dest = p.target();
          long rtt = Math.max(0L, System.currentTimeMillis() - p.startedMs());
          rendered = from + " PING reply: " + rtt + "ms";
        }
      } else if ("TIME".equals(cmd)) {
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
        if (p != null) {
          dest = p.target();
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        }
      }

      // If we received a CTCP reply we recognize but didn't have a pending request for,
      // still render a clean status line to the server status window (better than raw 0x01).
      if (dest == null && rendered == null) {
        if ("VERSION".equals(cmd)) {
          dest = status;
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        } else if ("PING".equals(cmd)) {
          dest = status;
          rendered = from + " PING: " + (arg.isBlank() ? "(no payload)" : arg);
        } else if ("TIME".equals(cmd)) {
          dest = status;
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        } else {
          PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
          dest = (p != null) ? p.target() : status;
          rendered = from + " " + cmd + (arg.isBlank() ? "" : ": " + arg);
        }
      }

      if (dest != null && rendered != null) {
        ensureTargetExists(dest);
        if (spoiler) {
          ui.appendSpoilerChat(dest, "(ctcp)", rendered);
        } else {
          ui.appendStatus(dest, "(ctcp)", rendered);
        }
        if (!dest.equals(targetCoordinator.getActiveTarget())) ui.markUnread(dest);
        return;
      }
    }

    if (spoiler) {
      ui.appendSpoilerChat(status, "(notice) " + from, text);
    } else {
      ui.appendNotice(status, "(notice) " + from, text);
    }
  }
  public void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }
    disposables.dispose();
  }

  /**
   * Connect to all configured servers.
   *
   * <p>Safe to call multiple times; connection attempts are idempotent per server.
   */
  public void connectAll() {
    connectionCoordinator.connectAll();
  }

  /** Disconnect from all configured servers. */
  public void disconnectAll() {
    connectionCoordinator.disconnectAll();
  }

  private void handleOutgoingLine(String raw) {
    ParsedInput in = commandParser.parse(raw);
    switch (in) {
      case ParsedInput.Join cmd -> handleJoin(cmd.channel());
      case ParsedInput.Nick cmd -> handleNick(cmd.newNick());
      case ParsedInput.Away cmd -> handleAway(cmd.message());
      case ParsedInput.Query cmd -> handleQuery(cmd.nick());
      case ParsedInput.Msg cmd -> handleMsg(cmd.nick(), cmd.body());
      case ParsedInput.Me cmd -> handleMe(cmd.action());
      case ParsedInput.Mode cmd -> outboundModeCommandService.handleMode(disposables, cmd.first(), cmd.rest());
      case ParsedInput.Op cmd -> outboundModeCommandService.handleOp(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Deop cmd -> outboundModeCommandService.handleDeop(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Voice cmd -> outboundModeCommandService.handleVoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Devoice cmd -> outboundModeCommandService.handleDevoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Ban cmd -> outboundModeCommandService.handleBan(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Unban cmd -> outboundModeCommandService.handleUnban(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Ignore cmd -> handleIgnore(cmd.maskOrNick());
      case ParsedInput.Unignore cmd -> handleUnignore(cmd.maskOrNick());
      case ParsedInput.IgnoreList cmd -> handleIgnoreList();
      case ParsedInput.CtcpVersion cmd -> outboundCtcpWhoisCommandService.handleCtcpVersion(disposables, cmd.nick());
      case ParsedInput.CtcpPing cmd -> outboundCtcpWhoisCommandService.handleCtcpPing(disposables, cmd.nick());
      case ParsedInput.CtcpTime cmd -> outboundCtcpWhoisCommandService.handleCtcpTime(disposables, cmd.nick());
      case ParsedInput.Ctcp cmd -> outboundCtcpWhoisCommandService.handleCtcp(disposables, cmd.nick(), cmd.command(), cmd.args());
      case ParsedInput.Quote cmd -> handleQuote(cmd.rawLine());
      case ParsedInput.Say cmd -> handleSay(cmd.text());
      case ParsedInput.Unknown cmd ->
          ui.appendStatus(safeStatusTarget(), "(system)", "Unknown command: " + cmd.raw());
    }
  }

  private void handleQuote(String rawLine) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(quote)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) {
      ui.appendStatus(status, "(quote)", "Usage: /quote <RAW IRC LINE>");
      ui.appendStatus(status, "(quote)", "Example: /quote MONITOR +nick");
      ui.appendStatus(status, "(quote)", "Alias: /raw <RAW IRC LINE>");
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
        irc.sendRaw(at.serverId(), line).subscribe(
            () -> {},
            err -> ui.appendError(status, "(quote-error)", String.valueOf(err))
        )
    );
  }

  private static String redactIfSensitive(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return s;

    int sp = s.indexOf(' ');
    String head = (sp < 0 ? s : s.substring(0, sp)).trim();
    String upper = head.toUpperCase(java.util.Locale.ROOT);
    if (upper.equals("PASS") || upper.equals("OPER") || upper.equals("AUTHENTICATE")) {
      return upper + (sp < 0 ? "" : " <redacted>");
    }
    return s;
  }

  private void handleJoin(String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(join)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    if (chan.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(join)", "Usage: /join <#channel>");
      return;
    }

    // Persist for auto-join next time.
    runtimeConfig.rememberJoinedChannel(at.serverId(), chan);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected (join queued in config only)");
      return;
    }

    disposables.add(
        irc.joinChannel(at.serverId(), chan).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(join-error)", String.valueOf(err))
        )
    );
  }

  private void handleNick(String newNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(nick)", "Usage: /nick <newNick>");
      return;
    }

    // Persist the preferred nick for next time.
    runtimeConfig.rememberNick(at.serverId(), nick);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    disposables.add(
        irc.changeNick(at.serverId(), nick).subscribe(
            () -> ui.appendStatus(new TargetRef(at.serverId(), "status"), "(nick)", "Requested nick change to " + nick),
            err -> ui.appendError(safeStatusTarget(), "(nick-error)", String.valueOf(err))
        )
    );
  }

  private void handleAway(String message) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(away)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");

    String msg = message == null ? "" : message.trim();

    boolean explicitClear = "-".equals(msg)
        || "off".equalsIgnoreCase(msg)
        || "clear".equalsIgnoreCase(msg);

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
        irc.setAway(at.serverId(), toSend).subscribe(
            () -> {
              awayRoutingState.setAway(at.serverId(), !clear);
              ui.appendStatus(status, "(away)", clear ? "Away cleared" : ("Away set: " + toSend));
            },
            err -> {
              awayRoutingState.setLastReason(at.serverId(), prevReason);
              ui.appendError(status, "(away-error)", String.valueOf(err));
            }
        )
    );
  }

  private void handleQuery(String nick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(query)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(query)", "Usage: /query <nick>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  private void handleMsg(String nick, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(msg)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(msg)", "Usage: /msg <nick> <message>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
    sendMessage(pm, m);
  }

  private void handleMe(String action) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(me)", "Select a server first.");
      return;
    }

    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(me)", "Usage: /me <action>");
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

    String me = irc.currentNick(at.serverId()).orElse("me");
    ui.appendAction(at, me, a, true);

    disposables.add(
        irc.sendAction(at.serverId(), at.target(), a).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );
  }


  // --- MODE slash commands extracted to OutboundModeCommandService --------------------

  private void handleIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ignore)", "Usage: /ignore <maskOrNick>");
      return;
    }

    boolean added = ignoreListService.addMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (added) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ignore)", "Ignoring: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ignore)", "Already ignored: " + stored);
    }
  }

  private void handleUnignore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(unignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unignore)", "Usage: /unignore <maskOrNick>");
      return;
    }

    boolean removed = ignoreListService.removeMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unignore)", "Removed ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unignore)", "Not in ignore list: " + stored);
    }
  }

  private void handleIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    java.util.List<String> masks = ignoreListService.listMasks(at.serverId());
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (masks.isEmpty()) {
      ui.appendStatus(status, "(ignore)", "Ignore list is empty.");
      return;
    }

    ui.appendStatus(status, "(ignore)", "Ignore masks (" + masks.size() + "): ");
    for (String m : masks) {
      ui.appendStatus(status, "(ignore)", "  - " + m);
    }
  }

  private void handleSay(String msg) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(system)", "Select a channel, or double-click a nick to PM them.");
      return;
    }

    sendMessage(at, m);
  }

  private void sendMessage(TargetRef target, String message) {
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

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );

    String me = irc.currentNick(target.serverId()).orElse("me");
    ui.appendChat(target, "(" + me + ")", m, true);
  }

  private void onServerIrcEvent(ServerIrcEvent se) {
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");

    // Delegate connectivity state changes.
    if (e instanceof IrcEvent.Connected
        || e instanceof IrcEvent.Connecting
        || e instanceof IrcEvent.Reconnecting
        || e instanceof IrcEvent.Disconnected) {
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        targetCoordinator.onServerDisconnected(sid);
        // Drop any per-server correlation state so it doesn't stick across reconnects.
        whoisRoutingState.clearServer(sid);
        ctcpRoutingState.clearServer(sid);
        modeRoutingState.clearServer(sid);
        awayRoutingState.clearServer(sid);
        inboundModeEventHandler.clearServer(sid);
      }
      targetCoordinator.refreshInputEnabledForActiveTarget();
      return;
    }

    switch (e) {
      case IrcEvent.NickChanged ev -> {
        irc.currentNick(sid).ifPresent(currentNick -> {
          if (!Objects.equals(currentNick, ev.oldNick()) && !Objects.equals(currentNick, ev.newNick())) {
            ui.appendNotice(status, "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
          } else {
            ui.appendStatus(status, "(nick)", "Now known as " + ev.newNick());
            ui.setChatCurrentNick(sid, ev.newNick());
          }
        });
      }

      case IrcEvent.ChannelMessage ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        postTo(chan, active, true, d -> ui.appendChat(d, ev.from(), ev.text()));
        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.text())) ui.markHighlight(chan);
      }

      case IrcEvent.SoftChannelMessage ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        postTo(chan, active, true, d -> ui.appendSpoilerChat(d, ev.from(), ev.text()));
        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.text())) ui.markHighlight(chan);
      }

      case IrcEvent.ChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        postTo(chan, active, true, d -> ui.appendAction(d, ev.from(), ev.action()));
        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.action())) ui.markHighlight(chan);
      }

      case IrcEvent.SoftChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        postTo(chan, active, true, d -> ui.appendSpoilerChat(d, ev.from(), "* " + ev.action()));
        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.action())) ui.markHighlight(chan);
      }


      case IrcEvent.ChannelModeChanged ev -> {
        inboundModeEventHandler.handleChannelModeChanged(sid, ev);
      }

      case IrcEvent.ChannelModesListed ev -> {
        inboundModeEventHandler.handleChannelModesListed(sid, ev);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        inboundModeEventHandler.onChannelTopicUpdated(sid, ev.channel());
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.setChannelTopic(chan, ev.topic());
      }

      case IrcEvent.PrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        postTo(pm, true, d -> ui.appendChat(d, ev.from(), ev.text()));
      }

      case IrcEvent.SoftPrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        postTo(pm, true, d -> ui.appendSpoilerChat(d, ev.from(), ev.text()));
      }

      case IrcEvent.PrivateAction ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        postTo(pm, true, d -> ui.appendAction(d, ev.from(), ev.action()));
      }

      case IrcEvent.SoftPrivateAction ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        postTo(pm, true, d -> ui.appendSpoilerChat(d, ev.from(), "* " + ev.action()));
      }
      case IrcEvent.Notice ev -> {
        handleNoticeOrSpoiler(sid, status, ev.from(), ev.text(), false);
      }

      case IrcEvent.CtcpRequestReceived ev -> {
        // Requested behavior: show inbound CTCP requests in the currently active chat target.
        // (If the active target is on a different server, fall back to status.)
        TargetRef dest = resolveActiveOrStatus(sid, status);

        StringBuilder sb = new StringBuilder()
            .append("\u2190 ")
            .append(ev.from())
            .append(" CTCP ")
            .append(ev.command());
        if (ev.argument() != null && !ev.argument().isBlank()) sb.append(' ').append(ev.argument());
        if (ev.channel() != null && !ev.channel().isBlank()) sb.append(" in ").append(ev.channel());
        final String rendered = sb.toString();

        postTo(dest, true, d -> ui.appendStatus(d, "(ctcp)", rendered));
      }

      case IrcEvent.SoftCtcpRequestReceived ev -> {
        TargetRef dest = resolveActiveOrStatus(sid, status);

        StringBuilder sb = new StringBuilder()
            .append("\u2190 ")
            .append(ev.from())
            .append(" CTCP ")
            .append(ev.command());
        if (ev.argument() != null && !ev.argument().isBlank()) sb.append(' ').append(ev.argument());
        if (ev.channel() != null && !ev.channel().isBlank()) sb.append(" in ").append(ev.channel());
        final String rendered = sb.toString();

        postTo(dest, true, d -> ui.appendSpoilerChat(d, "(ctcp)", rendered));
      }

      case IrcEvent.AwayStatusChanged ev -> {
        awayRoutingState.setAway(sid, ev.away());
        if (!ev.away()) awayRoutingState.setLastReason(sid, null);
        TargetRef dest = null;

        // Prefer routing back to where the user initiated /away (if recent), otherwise
        // fall back to the currently active target on the same server.
        TargetRef origin = awayRoutingState.recentOriginIfFresh(sid, Duration.ofSeconds(15));
        if (origin != null && Objects.equals(origin.serverId(), sid)) {
          dest = origin;
        }

        if (dest == null) {
          dest = resolveActiveOrStatus(sid, status);
        }

        final String rendered;
        if (ev.away()) {
          String reason = awayRoutingState.getLastReason(sid);
          if (reason != null && !reason.isBlank()) {
            rendered = "You are now marked as being away (Reason: " + reason + ")";
          } else {
            rendered = ev.message();
          }
        } else {
          rendered = "You are no longer marked as being away";
        }

        TargetRef finalDest = dest;
        postTo(finalDest, true, d -> ui.appendStatus(d, "(away)", rendered));
      }

      case IrcEvent.SoftNotice ev -> {
        handleNoticeOrSpoiler(sid, status, ev.from(), ev.text(), true);
      }

      case IrcEvent.WhoisResult ev -> {
        TargetRef dest = whoisRoutingState.remove(sid, ev.nick());
        if (dest == null) dest = status;
        postTo(dest, true, d -> {
          ui.appendStatus(d, "(whois)", "WHOIS for " + ev.nick());
          for (String line : ev.lines()) ui.appendStatus(d, "(whois)", line);
        });
      }

      case IrcEvent.UserJoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.join(ev.nick()));
      }

      case IrcEvent.UserPartedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.part(ev.nick(), ev.reason()));
      }

      case IrcEvent.UserQuitChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.quit(ev.nick(), ev.reason()));
      }

      case IrcEvent.UserNickChangedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.nick(ev.oldNick(), ev.newNick()));
      }

      case IrcEvent.JoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        runtimeConfig.rememberJoinedChannel(sid, ev.channel());

        // Buffer the initial channel-flag modes so the join doesn't spam the view.
        inboundModeEventHandler.onJoinedChannel(sid, ev.channel());

        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        ui.selectTarget(chan);
      }

      case IrcEvent.NickListUpdated ev -> {
        inboundModeEventHandler.onNickListUpdated(sid, ev.channel());
        targetCoordinator.onNickListUpdated(sid, ev);
      }

      case IrcEvent.UserHostmaskObserved ev -> {
        targetCoordinator.onUserHostmaskObserved(sid, ev);
      }

      case IrcEvent.UserAwayStateObserved ev -> {
        targetCoordinator.onUserAwayStateObserved(sid, ev);
      }

      case IrcEvent.Error ev -> {
          ui.appendError(status, "(error)", ev.message());
      }

      default -> {
      }
    }
  }


  private boolean containsSelfMention(String serverId, String from, String message) {
    if (serverId == null || message == null || message.isEmpty()) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;

    String fromNorm = normalizeNickForCompare(from);
    if (fromNorm != null && fromNorm.equalsIgnoreCase(me)) return false;

    return containsNickToken(message, me);
  }

  private static String normalizeNickForCompare(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return s;

    // Some UI paths wrap self-nick in parentheses.
    if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }

    // Strip common IRC user-mode prefixes if they appear.
    while (!s.isEmpty()) {
      char c = s.charAt(0);
      if (c == '@' || c == '+' || c == '%' || c == '~' || c == '&') {
        s = s.substring(1);
      } else {
        break;
      }
    }
    return s;
  }

  private static boolean containsNickToken(String message, String nick) {
    if (message == null || nick == null || nick.isEmpty()) return false;

    String nickLower = nick.toLowerCase(Locale.ROOT);
    int nlen = nickLower.length();

    int i = 0;
    final int len = message.length();
    while (i < len) {
      // Skip non-nick chars.
      while (i < len && !isNickChar(message.charAt(i))) i++;
      if (i >= len) break;
      int start = i;
      while (i < len && isNickChar(message.charAt(i))) i++;
      int end = i;
      int tokLen = end - start;
      if (tokLen == nlen) {
        String tokenLower = message.substring(start, end).toLowerCase(Locale.ROOT);
        if (tokenLower.equals(nickLower)) return true;
      }
    }
    return false;
  }

  private static boolean isNickChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '[' || ch == ']' || ch == '\\' || ch == '`' || ch == '_' || ch == '^' || ch == '{' || ch == '|' || ch == '}' || ch == '-';
  }

  /**
   * Convenience helper used by event handlers:
   * <ul>
   *   <li>Ensures the target exists</li>
   *   <li>Writes output via the provided callback</li>
   *   <li>Optionally marks the target unread if it is not the active tab</li>
   * </ul>
   */
  private void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    postTo(dest, targetCoordinator.getActiveTarget(), markUnreadIfNotActive, write);
  }

  /**
   * Resolves the destination target as:
   * <ol>
   *   <li>the currently active target, if it belongs to {@code sid}</li>
   *   <li>otherwise {@code status}</li>
   * </ol>
   *
   * This is used by events (CTCP, away, etc.) that should "notify the user" in the active tab,
   * but must not leak messages across different server sessions.
   */
  private TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) return active;
    return status != null ? status : safeStatusTarget();
  }

  /**
   * Overload used when the caller wants to make additional decisions (eg, highlight) based on a stable view of
   * what was considered the active target at the time of handling.
   */
  private void postTo(TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    if (dest == null) dest = safeStatusTarget();
    ensureTargetExists(dest);

    if (write != null) {
      write.accept(dest);
    }

    if (markUnreadIfNotActive && active != null && !dest.equals(active)) {
      ui.markUnread(dest);
    }
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }
}
