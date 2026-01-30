package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
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
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CompositeDisposable disposables = new CompositeDisposable();

  // Pending action routing: ensure WHOIS/CTCP responses print into the chat where the request originated.
  private final ConcurrentHashMap<WhoisKey, TargetRef> pendingWhoisTargets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<CtcpKey, PendingCtcp> pendingCtcp = new ConcurrentHashMap<>();

  // Pending MODE queries: route numeric 324 output back to the requesting tab.
  private final ConcurrentHashMap<ModeKey, TargetRef> pendingModeTargets = new ConcurrentHashMap<>();

  // Join-burst mode suppression: buffer simple channel-state modes and print once after join completes.
  private final ConcurrentHashMap<ModeKey, JoinModeBuffer> joinModeBuffers = new ConcurrentHashMap<>();

  // If we already printed a join-burst mode summary, suppress a near-immediate 324 summary to avoid duplicates.
  private final ConcurrentHashMap<ModeKey, Long> joinModeSummaryPrintedMs = new ConcurrentHashMap<>();


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
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator
  ) {
    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
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
        pendingWhoisTargets.put(new WhoisKey(sid, nick), ctx);
        ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + nick + "...");
        Disposable d = irc.whois(sid, nick)
            .subscribe(
                () -> {},
                err -> ui.appendError(fCtx, "(whois)", err.toString())
            );
        disposables.add(d);
      }

      case CTCP_VERSION -> {
        pendingCtcp.put(new CtcpKey(sid, nick, "VERSION", null), new PendingCtcp(ctx, System.currentTimeMillis()));
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " VERSION");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001VERSION\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_PING -> {
        String token = Long.toString(System.currentTimeMillis());
        pendingCtcp.put(new CtcpKey(sid, nick, "PING", token), new PendingCtcp(ctx, System.currentTimeMillis()));
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " PING");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001PING " + token + "\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_TIME -> {
        pendingCtcp.put(new CtcpKey(sid, nick, "TIME", null), new PendingCtcp(ctx, System.currentTimeMillis()));
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " TIME");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001TIME\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }
    }
  }

  private record WhoisKey(String serverId, String nickLower) {
    // Compact canonical constructor: normalize inputs before field assignment.
    WhoisKey {
      serverId = (serverId == null) ? "" : serverId.trim();
      nickLower = (nickLower == null) ? "" : nickLower.trim().toLowerCase(Locale.ROOT);
    }
  }

  private record CtcpKey(String serverId, String nickLower, String commandUpper, String token) {
    // Compact canonical constructor: normalize inputs before field assignment.
    CtcpKey {
      serverId = (serverId == null) ? "" : serverId.trim();
      nickLower = (nickLower == null) ? "" : nickLower.trim().toLowerCase(Locale.ROOT);
      commandUpper = (commandUpper == null) ? "" : commandUpper.trim().toUpperCase(Locale.ROOT);
      token = (token == null) ? null : token.trim();
    }
  }

  private record PendingCtcp(TargetRef target, long startedMs) {}

  private record ModeKey(String serverId, String channelLower) {
    ModeKey {
      serverId = (serverId == null) ? "" : serverId.trim();
      channelLower = (channelLower == null) ? "" : channelLower.trim().toLowerCase(Locale.ROOT);
    }

    static ModeKey of(String serverId, String channel) {
      return new ModeKey(serverId, channel);
    }
  }

  /**
   * Buffers the initial "join burst" of simple channel-state modes (like +ntspimrC) so we can print
   * a single summary line after the join feels complete.
   */
  private static final class JoinModeBuffer {
    private final java.util.LinkedHashSet<Character> plus = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Character> minus = new java.util.LinkedHashSet<>();


    // Debounce flush so we print once shortly after the join-mode burst settles.
    private javax.swing.Timer flushTimer;

    void bumpFlush(Runnable flush) {
      if (flush == null) return;
      if (flushTimer != null) flushTimer.stop();
      flushTimer = new javax.swing.Timer(200, e -> flush.run());
      flushTimer.setRepeats(false);
      flushTimer.start();
    }

    void cancelFlushTimer() {
      if (flushTimer != null) {
        flushTimer.stop();
        flushTimer = null;
      }
    }

    boolean tryAdd(String details) {
      if (details == null) return false;
      String d = details.trim();
      if (d.isEmpty()) return false;

      // Only accept simple flag sets with no args (no spaces).
      int sp = d.indexOf(' ');
      if (sp >= 0) return false;

      char sign = d.charAt(0);
      if (sign != '+' && sign != '-') return false;

      for (int i = 1; i < d.length(); i++) {
        char c = d.charAt(i);
        // Accept any simple flag (no args) during the join burst.
        // We'll render known ones as phrases and unknowns as +x/-x.
        if (!Character.isLetterOrDigit(c)) return false;
      }

      java.util.LinkedHashSet<Character> target = (sign == '+') ? plus : minus;
      for (int i = 1; i < d.length(); i++) {
        target.add(d.charAt(i));
      }

      return true;
    }

    boolean isEmpty() {
      return plus.isEmpty() && minus.isEmpty();
    }

    String summarize() {
      return ModeSummary.describeBufferedJoinModes(plus, minus);
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
      case ParsedInput.Query cmd -> handleQuery(cmd.nick());
      case ParsedInput.Msg cmd -> handleMsg(cmd.nick(), cmd.body());
      case ParsedInput.Me cmd -> handleMe(cmd.action());
      case ParsedInput.Mode cmd -> handleMode(cmd.first(), cmd.rest());
      case ParsedInput.Op cmd -> handleOp(cmd.channel(), cmd.nicks());
      case ParsedInput.Deop cmd -> handleDeop(cmd.channel(), cmd.nicks());
      case ParsedInput.Voice cmd -> handleVoice(cmd.channel(), cmd.nicks());
      case ParsedInput.Devoice cmd -> handleDevoice(cmd.channel(), cmd.nicks());
      case ParsedInput.Ban cmd -> handleBan(cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Unban cmd -> handleUnban(cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.CtcpVersion cmd -> handleCtcpVersion(cmd.nick());
      case ParsedInput.CtcpPing cmd -> handleCtcpPing(cmd.nick());
      case ParsedInput.CtcpTime cmd -> handleCtcpTime(cmd.nick());
      case ParsedInput.Ctcp cmd -> handleCtcp(cmd.nick(), cmd.command(), cmd.args());
      case ParsedInput.Say cmd -> handleSay(cmd.text());
      case ParsedInput.Unknown cmd ->
          ui.appendStatus(safeStatusTarget(), "(system)", "Unknown command: " + cmd.raw());
    }
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
    ui.appendAction(at, me, a);

    disposables.add(
        irc.sendAction(at.serverId(), at.target(), a).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );
  }


  private void handleMode(String first, String rest) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(mode)", "Select a server first.");
      return;
    }

    String f = first == null ? "" : first.trim();
    String r = rest == null ? "" : rest.trim();

    // Determine target channel + mode string.
    String channel;
    String modeSpec;

    if (f.startsWith("#") || f.startsWith("&")) {
      channel = f;
      modeSpec = r;
    } else if (at.isChannel()) {
      channel = at.target();
      modeSpec = (f + (r.isEmpty() ? "" : " " + r)).trim();
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Tip: from a channel tab you can use /mode +o nick");
      return;
    }

    if (channel == null || channel.isBlank()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String line = "MODE " + channel + (modeSpec == null || modeSpec.isBlank() ? "" : " " + modeSpec);
    TargetRef out = at.isChannel() ? new TargetRef(at.serverId(), channel) : new TargetRef(at.serverId(), "status");
    if (modeSpec == null || modeSpec.isBlank()) {
      pendingModeTargets.put(ModeKey.of(at.serverId(), channel), out);
    }
    ensureTargetExists(out);
    ui.appendStatus(out, "(mode)", "→ " + line);

    disposables.add(
        irc.sendRaw(at.serverId(), line).subscribe(
            () -> {},
            err -> ui.appendError(new TargetRef(at.serverId(), "status"), "(mode-error)", String.valueOf(err))
        )
    );
  }

  // --- CTCP slash commands --------------------------------------------------


  private void handleOp(String channel, java.util.List<String> nicks) {
    handleSimpleNickMode(channel, nicks, "+o", "Usage: /op [#channel] <nick> [nick...]");
  }

  private void handleDeop(String channel, java.util.List<String> nicks) {
    handleSimpleNickMode(channel, nicks, "-o", "Usage: /deop [#channel] <nick> [nick...]");
  }

  private void handleVoice(String channel, java.util.List<String> nicks) {
    handleSimpleNickMode(channel, nicks, "+v", "Usage: /voice [#channel] <nick> [nick...]");
  }

  private void handleDevoice(String channel, java.util.List<String> nicks) {
    handleSimpleNickMode(channel, nicks, "-v", "Usage: /devoice [#channel] <nick> [nick...]");
  }

  private void handleBan(String channel, java.util.List<String> masksOrNicks) {
    handleBanMode(channel, masksOrNicks, true);
  }

  private void handleUnban(String channel, java.util.List<String> masksOrNicks) {
    handleBanMode(channel, masksOrNicks, false);
  }

  private void handleSimpleNickMode(String channel, java.util.List<String> nicks, String mode, String usage) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(mode)", "Select a server first.");
      return;
    }
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", usage);
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (nicks == null || nicks.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", usage);
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    ensureTargetExists(out);

    for (String nick : nicks) {
      String n = nick == null ? "" : nick.trim();
      if (n.isEmpty()) continue;

      String line = "MODE " + ch + " " + mode + " " + n;
      ui.appendStatus(out, "(mode)", "→ " + line);

      disposables.add(
          irc.sendRaw(at.serverId(), line).subscribe(
              () -> {},
              err -> ui.appendError(new TargetRef(at.serverId(), "status"), "(mode-error)", String.valueOf(err))
          )
      );
    }
  }

  private void handleBanMode(String channel, java.util.List<String> masksOrNicks, boolean add) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(mode)", "Select a server first.");
      return;
    }
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (masksOrNicks == null || masksOrNicks.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(mode)", "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    ensureTargetExists(out);

    String mode = add ? "+b" : "-b";

    for (String item : masksOrNicks) {
      String raw = item == null ? "" : item.trim();
      if (raw.isEmpty()) continue;

      String mask = looksLikeMask(raw) ? raw : (raw + "!*@*");

      String line = "MODE " + ch + " " + mode + " " + mask;
      ui.appendStatus(out, "(mode)", "→ " + line);

      disposables.add(
          irc.sendRaw(at.serverId(), line).subscribe(
              () -> {},
              err -> ui.appendError(new TargetRef(at.serverId(), "status"), "(mode-error)", String.valueOf(err))
          )
      );
    }
  }

  private static boolean looksLikeMask(String s) {
    if (s == null) return false;
    return s.indexOf('!') >= 0 || s.indexOf('@') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }

  private static String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) return ch;
    if (active != null && active.isChannel()) return active.target();
    return null;
  }

  private void handleCtcpVersion(String nick) {
    sendCtcpSlash("VERSION", nick, "", false);
  }

  private void handleCtcpPing(String nick) {
    // Token lets us compute RTT when the reply comes back.
    String token = Long.toString(System.currentTimeMillis());
    sendCtcpSlash("PING", nick, token, true);
  }

  private void handleCtcpTime(String nick) {
    sendCtcpSlash("TIME", nick, "", false);
  }

  private void handleCtcp(String nick, String command, String args) {
    String n = nick == null ? "" : nick.trim();
    String cmd = command == null ? "" : command.trim();
    String a = args == null ? "" : args.trim();

    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(ctcp)", "Select a server first.");
      return;
    }

    TargetRef ctx = at;
    if (!java.util.Objects.equals(ctx.serverId(), at.serverId())) {
      ctx = new TargetRef(at.serverId(), "status");
    }

    if (n.isEmpty() || cmd.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ctcp)", "Usage: /ctcp <nick> <command> [args...]");
      return;
    }

    String cmdU = cmd.toUpperCase(java.util.Locale.ROOT);

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
      sendCtcp(at, ctx, n, "PING", payload, tokenKey);
      return;
    }

    // If you want /ctcp nick ACTION ... use /me instead.
    if ("ACTION".equals(cmdU)) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ctcp)", "Use /me for ACTION.");
      return;
    }

    sendCtcp(at, ctx, n, cmdU, a, null);
  }

  private void sendCtcpSlash(String cmdUpper, String nick, String args, boolean expectsReply) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(ctcp)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String a = args == null ? "" : args.trim();
    if (n.isEmpty()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ctcp)", "Usage: /" + cmdUpper.toLowerCase(java.util.Locale.ROOT) + " <nick>");
      return;
    }

    sendCtcp(at, at, n, cmdUpper, a, expectsReply ? a : null);
  }

  private void sendCtcp(TargetRef serverCtx, TargetRef outputCtx, String nick, String cmdUpper, String args, String tokenKey) {
    if (serverCtx == null) return;
    String sid = serverCtx.serverId();

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(new TargetRef(sid, "status"), "(conn)", "Not connected");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return;

    String cmd = cmdUpper == null ? "" : cmdUpper.trim().toUpperCase(java.util.Locale.ROOT);
    String a = args == null ? "" : args.trim();

    TargetRef ctx = outputCtx != null ? outputCtx : new TargetRef(sid, "status");

    StringBuilder inner = new StringBuilder(cmd);
    if (!a.isEmpty()) inner.append(' ').append(a);
    String ctcp = "\u0001" + inner + "\u0001";

    // Track pending so replies can be routed back to the current context.
    String nickLower = n.toLowerCase(java.util.Locale.ROOT);
    pendingCtcp.put(new CtcpKey(sid, nickLower, cmd, tokenKey), new PendingCtcp(ctx, System.currentTimeMillis()));

    String display = "→ " + n + " " + cmd + (a.isEmpty() ? "" : " " + a);
    ui.appendStatus(ctx, "(ctcp)", display);

    disposables.add(
        irc.sendPrivateMessage(sid, n, ctcp).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(ctcp-error)", String.valueOf(err))
        )
    );
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
    ui.appendChat(target, "(" + me + ")", m);
  }

  private void onServerIrcEvent(ServerIrcEvent se) {
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");

    // Delegate connectivity state changes.
    if (e instanceof IrcEvent.Connected
        || e instanceof IrcEvent.Reconnecting
        || e instanceof IrcEvent.Disconnected) {
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        targetCoordinator.onServerDisconnected(sid);
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
        ensureTargetExists(chan);
        ui.appendChat(chan, ev.from(), ev.text());
        if (!chan.equals(targetCoordinator.getActiveTarget())) ui.markUnread(chan);
      }

      case IrcEvent.ChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendAction(chan, ev.from(), ev.action());
        if (!chan.equals(targetCoordinator.getActiveTarget())) ui.markUnread(chan);
      }


      case IrcEvent.ChannelModeChanged ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);

        String byRaw = ev.by();
        String details = ev.details();

        // Suppress the initial burst of simple channel-flag modes right after joining.
        JoinModeBuffer joinBuf = joinModeBuffers.get(ModeKey.of(sid, ev.channel()));
        if (joinBuf != null) {
          if (joinBuf.tryAdd(details)) {
            // Print quickly (debounced) instead of waiting for TOPIC/NAMES.
            joinBuf.bumpFlush(() -> flushJoinModesIfAny(sid, ev.channel(), true));
            return;
          }
          // As soon as we see something else, flush the buffered summary so the join feels complete.
          flushJoinModesIfAny(sid, ev.channel(), true);
        }

        // Make MODE output human-friendly (e.g. +b mask -> "ban added").
        for (String line : ModePrettyPrinter.pretty(byRaw, ev.channel(), details)) {
          ui.appendNotice(chan, "(mode)", line);
        }
      }

      case IrcEvent.ChannelModesListed ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);

        ModeKey key = ModeKey.of(sid, ev.channel());

        // If this arrived during join, prefer the authoritative 324 summary and discard any buffered noise.
        JoinModeBuffer removed = joinModeBuffers.remove(key);
        if (removed != null) removed.cancelFlushTimer();

        TargetRef out = pendingModeTargets.remove(key);
        if (out == null) out = chan;

        String summary = ModeSummary.describeCurrentChannelModes(ev.details());
        if (summary != null && !summary.isBlank()) {
          // If we already printed a join-burst summary, don't immediately duplicate it with 324.
          if (out.equals(chan)) {
            Long printedMs = joinModeSummaryPrintedMs.remove(key);
            if (printedMs != null && (System.currentTimeMillis() - printedMs) < 4000L) {
              return;
            }
          } else {
            // Clean up any stale marker.
            joinModeSummaryPrintedMs.remove(key);
          }

          ui.appendNotice(out, "(mode)", summary);
        }

      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        flushJoinModesIfAny(sid, ev.channel(), false);
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.setChannelTopic(chan, ev.topic());
      }

      case IrcEvent.PrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        ensureTargetExists(pm);
        ui.appendChat(pm, ev.from(), ev.text());
        if (!pm.equals(targetCoordinator.getActiveTarget())) ui.markUnread(pm);
      }

      case IrcEvent.PrivateAction ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        ensureTargetExists(pm);
        ui.appendAction(pm, ev.from(), ev.action());
        if (!pm.equals(targetCoordinator.getActiveTarget())) ui.markUnread(pm);
      }

      case IrcEvent.Notice ev -> {
        // CTCP replies come back as NOTICE with 0x01-wrapped payload.
        // Route them to the chat target where the request originated.
        ParsedCtcp ctcp = parseCtcp(ev.text());
        if (ctcp != null) {
          String from = ev.from();
          String cmd = ctcp.commandUpper();
          String arg = ctcp.arg();

          TargetRef dest = null;
          String rendered = null;

          if ("VERSION".equals(cmd)) {
            PendingCtcp p = pendingCtcp.remove(new CtcpKey(sid, from, cmd, null));
            if (p != null) {
              dest = p.target();
              rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
            }
          } else if ("PING".equals(cmd)) {
            String token = arg;
            int sp = token.indexOf(' ');
            if (sp >= 0) token = token.substring(0, sp);
            PendingCtcp p = pendingCtcp.remove(new CtcpKey(sid, from, cmd, token));
            if (p != null) {
              dest = p.target();
              long rtt = Math.max(0L, System.currentTimeMillis() - p.startedMs());
              rendered = from + " PING reply: " + rtt + "ms";
            }
          } else if ("TIME".equals(cmd)) {
            PendingCtcp p = pendingCtcp.remove(new CtcpKey(sid, from, cmd, null));
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
              PendingCtcp p = pendingCtcp.remove(new CtcpKey(sid, from, cmd, null));
              dest = (p != null) ? p.target() : status;
              rendered = from + " " + cmd + (arg.isBlank() ? "" : ": " + arg);
            }
          }

          if (dest != null && rendered != null) {
            ensureTargetExists(dest);
            ui.appendStatus(dest, "(ctcp)", rendered);
            if (!dest.equals(targetCoordinator.getActiveTarget())) ui.markUnread(dest);
            return;
          }
        }

        ui.appendNotice(status, "(notice) " + ev.from(), ev.text());
      }

      case IrcEvent.WhoisResult ev -> {
        TargetRef dest = pendingWhoisTargets.remove(new WhoisKey(sid, ev.nick()));
        if (dest == null) dest = status;
        ensureTargetExists(dest);
        ui.appendStatus(dest, "(whois)", "WHOIS for " + ev.nick());
        for (String line : ev.lines()) ui.appendStatus(dest, "(whois)", line);
        if (!dest.equals(targetCoordinator.getActiveTarget())) ui.markUnread(dest);
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
        startJoinModeBuffer(sid, ev.channel());

        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        ui.selectTarget(chan);
      }

      case IrcEvent.NickListUpdated ev -> {
        flushJoinModesIfAny(sid, ev.channel(), false);
        targetCoordinator.onNickListUpdated(sid, ev);
      }
//
//      case IrcEvent.Error ev -> {
//        targetCoordinator.onNickListUpdated(sid, ev);
//      }

      case IrcEvent.Error ev -> {
          ui.appendError(status, "(error)", ev.message());
      }

      default -> {
      }
    }
  }


  private void startJoinModeBuffer(String serverId, String channel) {
    if (channel == null || channel.isBlank()) return;

    ModeKey key = ModeKey.of(serverId, channel);

    // Always overwrite: the latest join wins.
    joinModeSummaryPrintedMs.remove(key);

    joinModeBuffers.put(key, new JoinModeBuffer());

    // Fallback flush: if we already collected any join-burst flags, print soon after join.
    // IMPORTANT: do NOT discard an empty buffer here; some networks delay MODE for a couple seconds.
    javax.swing.Timer t = new javax.swing.Timer(1500, e -> flushJoinModesIfAny(serverId, channel, false));
    t.setRepeats(false);
    t.start();

    // Cleanup: if we never receive join-burst modes, don't keep the empty buffer forever.
    javax.swing.Timer cleanup = new javax.swing.Timer(15000, e -> flushJoinModesIfAny(serverId, channel, true));
    cleanup.setRepeats(false);
    cleanup.start();
  }

  private void flushJoinModesIfAny(String serverId, String channel, boolean finalizeIfEmpty) {
    if (channel == null || channel.isBlank()) return;

    ModeKey key = ModeKey.of(serverId, channel);

    JoinModeBuffer buf = joinModeBuffers.get(key);
    if (buf == null) return;

    // Don't finalize early when we haven't seen any join-burst modes yet (topic/NAMES can arrive first).
    if (buf.isEmpty()) {
      if (finalizeIfEmpty) {
        joinModeBuffers.remove(key, buf);
        buf.cancelFlushTimer();
      }
      return;
    }

    // We have something to print; finalize this join-burst.
    joinModeBuffers.remove(key, buf);
    buf.cancelFlushTimer();

    TargetRef chan = new TargetRef(serverId, channel);
    ensureTargetExists(chan);

    String summary = buf.summarize();
    if (summary == null || summary.isBlank()) return;

    joinModeSummaryPrintedMs.put(key, System.currentTimeMillis());
    ui.appendNotice(chan, "(mode)", summary);
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }
}
