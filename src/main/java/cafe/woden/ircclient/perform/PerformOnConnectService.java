package cafe.woden.ircclient.perform;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes a per-server "perform" list when that server connects.
 *
 * <p>HexChat-style rules:
 *
 * <ul>
 *   <li>Each configured line is run in order.
 *   <li>Lines starting with '/' are treated as IRCafe slash commands.
 *   <li>Lines without '/' are treated as raw IRC lines.
 * </ul>
 */
@Component
@ApplicationLayer
public class PerformOnConnectService {

  private static final Logger log = LoggerFactory.getLogger(PerformOnConnectService.class);

  // Keep this small; PircbotX also has its own message delay.
  private static final long DEFAULT_INTERLINE_DELAY_MS = 200L;

  private final IrcClientService irc;
  private final ServerCatalog serverCatalog;
  private final CommandParser commandParser;
  private final UiPort ui;

  private final ConcurrentHashMap<String, Disposable> activeRunsByServer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> activeRunIdByServer = new ConcurrentHashMap<>();
  private final AtomicLong runIdSequence = new AtomicLong();
  private final Disposable eventsSub;

  public PerformOnConnectService(
      IrcClientService irc, ServerCatalog serverCatalog, CommandParser commandParser, UiPort ui) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.commandParser = Objects.requireNonNull(commandParser, "commandParser");
    this.ui = Objects.requireNonNull(ui, "ui");

    this.eventsSub =
        irc.events()
            .subscribe(
                this::onEvent,
                err -> log.debug("PerformOnConnectService event handler failed", err));
  }

  @jakarta.annotation.PreDestroy
  void shutdown() {
    try {
      if (eventsSub != null && !eventsSub.isDisposed()) eventsSub.dispose();
    } catch (Exception ignored) {
    }
    for (Disposable d : activeRunsByServer.values()) {
      try {
        if (d != null && !d.isDisposed()) d.dispose();
      } catch (Exception ignored) {
      }
    }
    activeRunsByServer.clear();
  }

  private void onEvent(ServerIrcEvent sev) {
    if (sev == null) return;
    String sid = norm(sev.serverId());
    if (sid.isEmpty()) return;
    IrcEvent e = sev.event();
    if (e instanceof IrcEvent.Connected c) {
      runPerform(sid, Objects.toString(c.nick(), "").trim());
      return;
    }
    if (e instanceof IrcEvent.Disconnected) {
      cancelRun(sid);
    }
  }

  private void cancelRun(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    activeRunIdByServer.remove(sid);
    Disposable prev = activeRunsByServer.remove(sid);
    if (prev != null) {
      try {
        if (!prev.isDisposed()) prev.dispose();
      } catch (Exception ignored) {
      }
    }
  }

  private void runPerform(String serverId, String connectedNick) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;

    Optional<IrcProperties.Server> sOpt = serverCatalog.find(sid);
    if (sOpt.isEmpty()) return;

    List<String> perform = sOpt.get().perform();
    if (perform == null || perform.isEmpty()) return;

    // Cancel any previous run (reconnect storms, etc.).
    cancelRun(sid);

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);

    int count = 0;
    for (String line : perform) {
      String t = Objects.toString(line, "").trim();
      if (!t.isEmpty() && !isComment(t)) count++;
    }
    if (count == 0) return;

    ui.appendStatus(
        status,
        "(perform)",
        "Running perform list (" + count + " line" + (count == 1 ? "" : "s") + ")");

    List<String> lines = List.copyOf(perform);
    long runId = runIdSequence.incrementAndGet();
    activeRunIdByServer.put(sid, runId);

    Completable chain = Completable.complete();
    for (int i = 0; i < lines.size(); i++) {
      String raw = lines.get(i);
      Completable step =
          Completable.defer(
                  () ->
                      isRunActive(sid, runId)
                          ? executeLine(sid, status, connectedNick, raw)
                          : Completable.complete())
              .onErrorComplete(
                  err -> {
                    // Keep going, but surface the error.
                    ui.appendError(
                        status, "(perform)", "Error running: " + summarize(raw) + " — " + err);
                    return true;
                  });
      chain = chain.andThen(step);
      // Small spacing between lines to reduce flood risk.
      if (i < lines.size() - 1) {
        chain =
            chain.andThen(
                Completable.defer(
                    () ->
                        isRunActive(sid, runId)
                            ? Completable.timer(DEFAULT_INTERLINE_DELAY_MS, TimeUnit.MILLISECONDS)
                            : Completable.complete()));
      }
    }

    Disposable run =
        chain.subscribe(
            () -> {
              activeRunIdByServer.remove(sid, runId);
            },
            err -> {
              activeRunIdByServer.remove(sid, runId);
              ui.appendError(status, "(perform)", "Perform list failed: " + err);
            });
    activeRunsByServer.put(sid, run);
  }

  private boolean isRunActive(String serverId, long runId) {
    Long activeRunId = activeRunIdByServer.get(serverId);
    return activeRunId != null && activeRunId == runId;
  }

  private Completable executeLine(
      String serverId, TargetRef status, String connectedNick, String rawLine) {
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty() || isComment(line)) return Completable.complete();

    String nick = Objects.toString(connectedNick, "").trim();
    String expanded = substitute(line, nick, serverId);
    if (containsCrlf(expanded)) {
      return Completable.error(new IllegalArgumentException("perform line contains CR/LF"));
    }

    // Allow simple delays inside the list (best-effort).
    if (isWaitLine(expanded)) {
      long ms = parseWaitMs(expanded);
      if (ms <= 0) return Completable.complete();
      ui.appendStatus(status, "(perform)", "Waiting " + ms + "ms");
      return Completable.timer(ms, TimeUnit.MILLISECONDS);
    }

    if (!expanded.startsWith("/")) {
      return irc.sendRaw(serverId, expanded);
    }

    ParsedInput parsed = commandParser.parse(expanded);
    return executeParsed(serverId, status, parsed, expanded);
  }

  private Completable executeParsed(
      String serverId, TargetRef status, ParsedInput in, String rawSlashLine) {
    if (in == null) return Completable.complete();

    return switch (in) {
      case ParsedInput.Join cmd -> {
        String chan = normTarget(cmd.channel());
        String key = Objects.toString(cmd.key(), "").trim();
        if (chan.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /join (missing channel)");
          yield Completable.complete();
        }
        if (key.isEmpty()) {
          yield irc.joinChannel(serverId, chan);
        }
        yield irc.sendRaw(serverId, "JOIN " + chan + " " + key);
      }

      case ParsedInput.Part cmd -> {
        String chan = normTarget(cmd.channel());
        String reason = Objects.toString(cmd.reason(), "").trim();
        if (chan.isEmpty()) {
          ui.appendStatus(
              status, "(perform)", "Skipping /part (provide an explicit #channel in perform)");
          yield Completable.complete();
        }
        yield irc.partChannel(serverId, chan, reason.isEmpty() ? null : reason);
      }

      case ParsedInput.Quit cmd -> {
        String reason = Objects.toString(cmd.reason(), "").trim();
        yield irc.disconnect(serverId, reason);
      }

      case ParsedInput.Nick cmd -> {
        String nn = Objects.toString(cmd.newNick(), "").trim();
        if (nn.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /nick (missing new nick)");
          yield Completable.complete();
        }
        yield irc.changeNick(serverId, nn);
      }

      case ParsedInput.Away cmd -> {
        String msg = Objects.toString(cmd.message(), "").trim();
        yield irc.setAway(serverId, msg.isEmpty() ? null : msg);
      }

      case ParsedInput.Query cmd -> {
        String nick = normTarget(cmd.nick());
        if (nick.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /query (missing nick)");
          yield Completable.complete();
        }
        ui.ensureTargetExists(new TargetRef(serverId, nick));
        yield Completable.complete();
      }

      case ParsedInput.Whois cmd -> {
        String nick = normTarget(cmd.nick());
        if (nick.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /whois (missing nick)");
          yield Completable.complete();
        }
        yield irc.whois(serverId, nick);
      }

      case ParsedInput.Whowas cmd -> {
        String nick = normTarget(cmd.nick());
        if (nick.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /whowas (missing nick)");
          yield Completable.complete();
        }
        yield irc.whowas(serverId, nick, cmd.count());
      }

      case ParsedInput.Msg cmd -> {
        String nick = normTarget(cmd.nick());
        String body = Objects.toString(cmd.body(), "");
        if (nick.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /msg (missing nick)");
          yield Completable.complete();
        }
        if (body == null || body.trim().isEmpty()) {
          // Treat empty as "open query".
          ui.ensureTargetExists(new TargetRef(serverId, nick));
          yield Completable.complete();
        }
        yield irc.sendPrivateMessage(serverId, nick, body);
      }

      case ParsedInput.Notice cmd -> {
        String tgt = normTarget(cmd.target());
        String body = Objects.toString(cmd.body(), "");
        if (tgt.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /notice (missing target)");
          yield Completable.complete();
        }
        yield irc.sendNotice(serverId, tgt, body);
      }

      case ParsedInput.Topic cmd -> {
        String first = Objects.toString(cmd.first(), "").trim();
        String rest = Objects.toString(cmd.rest(), "");
        if (first.isEmpty()) {
          ui.appendStatus(
              status, "(perform)", "Skipping /topic (provide an explicit #channel in perform)");
          yield Completable.complete();
        }
        // In perform, we require explicit #channel.
        if (!first.startsWith("#") && !first.startsWith("&")) {
          ui.appendStatus(
              status, "(perform)", "Skipping /topic (first token must be #channel in perform)");
          yield Completable.complete();
        }
        String ch = first;
        String topic = Objects.toString(rest, "").trim();
        if (topic.isEmpty()) {
          yield irc.sendRaw(serverId, "TOPIC " + ch);
        }
        yield irc.sendRaw(serverId, "TOPIC " + ch + " :" + topic);
      }

      case ParsedInput.Kick cmd -> {
        String ch = normTarget(cmd.channel());
        String nick = normTarget(cmd.nick());
        String reason = Objects.toString(cmd.reason(), "").trim();
        if (ch.isEmpty() || nick.isEmpty()) {
          ui.appendStatus(
              status, "(perform)", "Skipping /kick (usage: /kick #channel nick [reason])");
          yield Completable.complete();
        }
        String line = "KICK " + ch + " " + nick + (reason.isEmpty() ? "" : " :" + reason);
        yield irc.sendRaw(serverId, line);
      }

      case ParsedInput.Invite cmd -> {
        String nick = normTarget(cmd.nick());
        String ch = normTarget(cmd.channel());
        if (nick.isEmpty() || ch.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /invite (usage: /invite nick #channel)");
          yield Completable.complete();
        }
        yield irc.sendRaw(serverId, "INVITE " + nick + " " + ch);
      }

      case ParsedInput.Names cmd -> {
        String ch = normTarget(cmd.channel());
        if (ch.isEmpty()) {
          yield irc.sendRaw(serverId, "NAMES");
        }
        yield irc.requestNames(serverId, ch);
      }

      case ParsedInput.Who cmd -> {
        String args = Objects.toString(cmd.args(), "").trim();
        yield irc.sendRaw(serverId, args.isEmpty() ? "WHO" : ("WHO " + args));
      }

      case ParsedInput.ListCmd cmd -> {
        String args = Objects.toString(cmd.args(), "").trim();
        yield irc.sendRaw(serverId, args.isEmpty() ? "LIST" : ("LIST " + args));
      }

      case ParsedInput.Monitor cmd -> {
        String args = Objects.toString(cmd.args(), "").trim();
        if (args.isEmpty()) {
          ui.appendStatus(
              status,
              "(perform)",
              "Skipping /monitor (usage: /monitor <+|-|list|status|clear> [nicks])");
          yield Completable.complete();
        }
        String line = "MONITOR " + args;
        if (containsCrlf(line)) {
          ui.appendStatus(status, "(perform)", "Skipping /monitor (contains CR/LF)");
          yield Completable.complete();
        }
        yield irc.sendRaw(serverId, line);
      }

      case ParsedInput.Mode cmd -> {
        String first = normTarget(cmd.first());
        String rest = Objects.toString(cmd.rest(), "").trim();
        if (first.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /mode (missing target)");
          yield Completable.complete();
        }
        String line = rest.isEmpty() ? ("MODE " + first) : ("MODE " + first + " " + rest);
        yield irc.sendRaw(serverId, line);
      }

      case ParsedInput.Op cmd -> multiMode(serverId, status, cmd.channel(), "+o", cmd.nicks());
      case ParsedInput.Deop cmd -> multiMode(serverId, status, cmd.channel(), "-o", cmd.nicks());
      case ParsedInput.Voice cmd -> multiMode(serverId, status, cmd.channel(), "+v", cmd.nicks());
      case ParsedInput.Devoice cmd -> multiMode(serverId, status, cmd.channel(), "-v", cmd.nicks());
      case ParsedInput.Ban cmd ->
          multiMode(serverId, status, cmd.channel(), "+b", cmd.masksOrNicks());
      case ParsedInput.Unban cmd ->
          multiMode(serverId, status, cmd.channel(), "-b", cmd.masksOrNicks());

      case ParsedInput.CtcpVersion cmd -> ctcp(serverId, cmd.nick(), "VERSION", "");
      case ParsedInput.CtcpPing cmd ->
          ctcp(serverId, cmd.nick(), "PING", String.valueOf(System.currentTimeMillis()));
      case ParsedInput.CtcpTime cmd -> ctcp(serverId, cmd.nick(), "TIME", "");
      case ParsedInput.Ctcp cmd -> ctcp(serverId, cmd.nick(), cmd.command(), cmd.args());

      case ParsedInput.Quote cmd -> {
        String raw = Objects.toString(cmd.rawLine(), "").trim();
        if (raw.isEmpty()) {
          ui.appendStatus(status, "(perform)", "Skipping /quote (missing raw line)");
          yield Completable.complete();
        }
        if (containsCrlf(raw)) {
          yield Completable.error(new IllegalArgumentException("/quote line contains CR/LF"));
        }
        yield irc.sendRaw(serverId, raw);
      }

      case ParsedInput.Unknown cmd -> {
        ui.appendStatus(status, "(perform)", "Unknown perform command: " + summarize(cmd.raw()));
        yield Completable.complete();
      }

      default -> {
        // Many commands depend on UI/active target context, or are local-only. In perform, prefer
        // raw IRC (/quote or no '/') or commands that explicitly include their target.
        ui.appendStatus(
            status,
            "(perform)",
            "Unsupported in perform: " + summarize(rawSlashLine) + " (use /quote or raw IRC)");
        yield Completable.complete();
      }
    };
  }

  private Completable multiMode(
      String serverId, TargetRef status, String channel, String op, List<String> nicksOrMasks) {
    String ch = normTarget(channel);
    if (ch.isEmpty()) {
      ui.appendStatus(status, "(perform)", "Skipping (provide an explicit #channel in perform)");
      return Completable.complete();
    }

    List<String> items = new ArrayList<>();
    if (nicksOrMasks != null) {
      for (String n : nicksOrMasks) {
        String t = normTarget(n);
        if (!t.isEmpty()) items.add(t);
      }
    }
    if (items.isEmpty()) {
      ui.appendStatus(status, "(perform)", "Skipping (no targets provided)");
      return Completable.complete();
    }

    Completable chain = Completable.complete();
    for (String item : items) {
      String line = "MODE " + ch + " " + op + " " + item;
      chain = chain.andThen(irc.sendRaw(serverId, line));
    }
    return chain;
  }

  private Completable ctcp(String serverId, String nick, String command, String args) {
    String n = normTarget(nick);
    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    String a = Objects.toString(args, "").trim();
    if (n.isEmpty() || cmd.isEmpty()) return Completable.complete();
    String payload = a.isEmpty() ? cmd : (cmd + " " + a);
    String ctcp = "\u0001" + payload + "\u0001";
    return irc.sendPrivateMessage(serverId, n, ctcp);
  }

  private static boolean isComment(String line) {
    String t = Objects.toString(line, "").trim();
    return t.startsWith("#") || t.startsWith(";") || t.startsWith("//");
  }

  private static boolean isWaitLine(String line) {
    String t = Objects.toString(line, "").trim().toLowerCase(Locale.ROOT);
    return t.startsWith("/wait ") || t.startsWith("/sleep ");
  }

  private static long parseWaitMs(String line) {
    String t = Objects.toString(line, "").trim();
    String[] parts = t.split("\\s+", 2);
    if (parts.length < 2) return 0;
    String raw = parts[1].trim();
    if (raw.isEmpty()) return 0;
    try {
      long v = Long.parseLong(raw);
      if (v <= 0) return 0;
      // Heuristic: small values are seconds, larger are milliseconds.
      return (v <= 60) ? (v * 1000L) : v;
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String substitute(String line, String nick, String serverId) {
    String n = Objects.toString(nick, "");
    String sid = Objects.toString(serverId, "");
    // Common HexChat variable.
    String out = line.replace("$me", n).replace("$nick", n);
    // A couple of harmless extras.
    out = out.replace("$server", sid);
    return out;
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.contains("\r") || s.contains("\n"));
  }

  private static String norm(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? "" : v;
  }

  private static String normTarget(String s) {
    String v = Objects.toString(s, "").trim();
    if (v.isEmpty()) return "";
    // Strip a leading ':' if the user pasted IRC-style trailing params.
    if (v.startsWith(":")) v = v.substring(1).trim();
    return v;
  }

  private static String summarize(String raw) {
    String t = Objects.toString(raw, "").trim();
    if (t.length() <= 140) return t;
    return t.substring(0, 140) + "…";
  }
}
