package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasEngine;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound UI-triggered mediator actions. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorOutboundUiActionHandler {

  private final IrcMediatorInteractionPort irc;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final UserCommandAliasEngine userCommandAliasEngine;
  private final IrcSessionRuntimeConfigPort runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final OutboundCommandDispatcher outboundCommandDispatcher;
  private final TargetCoordinator targetCoordinator;
  private final WhoisRoutingPort whoisRoutingState;
  private final CtcpRoutingPort ctcpRoutingState;

  public void handleUserActionRequest(CompositeDisposable disposables, UserActionRequest req) {
    if (req == null) {
      return;
    }
    TargetRef ctx =
        req.contextTarget() != null ? req.contextTarget() : targetCoordinator.getActiveTarget();
    if (ctx == null) {
      ctx = targetCoordinator.safeStatusTarget();
    }
    final var finalCtx = ctx;
    String sid = ctx.serverId();
    String nick = Objects.toString(req.nick(), "").trim();
    if (sid == null || sid.isBlank() || nick.isBlank()) {
      return;
    }

    switch (req.action()) {
      case OPEN_QUERY ->
          targetCoordinator.openPrivateConversation(new PrivateMessageRequest(sid, nick));
      case WHOIS -> {
        whoisRoutingState.put(sid, nick, ctx);
        ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + nick + "...");
        disposables.add(
            irc.whois(sid, nick)
                .subscribe(() -> {}, err -> ui.appendError(finalCtx, "(whois)", err.toString())));
      }
      case CTCP_VERSION -> {
        ctcpRoutingState.put(sid, nick, "VERSION", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " VERSION");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001VERSION\u0001")
                .subscribe(() -> {}, err -> ui.appendError(finalCtx, "(ctcp)", err.toString())));
      }
      case CTCP_PING -> {
        String token = Long.toString(System.currentTimeMillis());
        ctcpRoutingState.put(sid, nick, "PING", token, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " PING");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001PING " + token + "\u0001")
                .subscribe(() -> {}, err -> ui.appendError(finalCtx, "(ctcp)", err.toString())));
      }
      case CTCP_TIME -> {
        ctcpRoutingState.put(sid, nick, "TIME", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " TIME");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001TIME\u0001")
                .subscribe(() -> {}, err -> ui.appendError(finalCtx, "(ctcp)", err.toString())));
      }
      case OP -> handleNickModeUserAction(disposables, ctx, nick, "+o");
      case DEOP -> handleNickModeUserAction(disposables, ctx, nick, "-o");
      case VOICE -> handleNickModeUserAction(disposables, ctx, nick, "+v");
      case DEVOICE -> handleNickModeUserAction(disposables, ctx, nick, "-v");
      case KICK -> handleKickUserAction(disposables, ctx, nick);
      case BAN -> handleBanUserAction(disposables, ctx, nick);
    }
  }

  public void handleIrcv3CapabilityToggleRequest(
      CompositeDisposable disposables, Ircv3CapabilityToggleRequest req) {
    if (req == null) {
      return;
    }

    String sid = Objects.toString(req.serverId(), "").trim();
    String cap = normalizeIrcv3CapabilityKey(req.capability());
    if (sid.isEmpty() || cap.isEmpty()) {
      return;
    }

    boolean enabled = req.enabled();
    runtimeConfig.rememberIrcv3CapabilityEnabled(cap, enabled);
    TargetRef status = new TargetRef(sid, "status");

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(
          status,
          "(cap)",
          "Saved "
              + cap
              + " as "
              + (enabled ? "enabled" : "disabled")
              + "; connect/reconnect to apply.");
      return;
    }

    ui.appendStatus(
        status,
        "(cap)",
        "Requesting CAP " + (enabled ? "enable" : "disable") + " for " + cap + "...");
    disposables.add(
        irc.setIrcv3CapabilityEnabled(sid, cap, enabled)
            .subscribe(() -> {}, err -> ui.appendError(status, "(cap)", String.valueOf(err))));
  }

  public void handleOutgoingLine(CompositeDisposable disposables, String raw) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef ctx = active != null ? active : targetCoordinator.safeStatusTarget();
    UserCommandAliasEngine.ExpansionResult expanded = userCommandAliasEngine.expand(raw, ctx);

    for (String warning : expanded.warnings()) {
      if (warning == null || warning.isBlank()) {
        continue;
      }
      TargetRef out =
          ctx != null
              ? new TargetRef(ctx.serverId(), "status")
              : targetCoordinator.safeStatusTarget();
      ui.appendStatus(out, "(alias)", warning);
    }

    for (String line : expanded.lines()) {
      if (line == null || line.isBlank()) {
        continue;
      }
      outboundCommandDispatcher.dispatch(disposables, commandParser.parse(line));
    }
  }

  public void handleBackendNamedCommandRequest(
      CompositeDisposable disposables, ParsedInput.BackendNamed command) {
    if (command == null) {
      return;
    }
    outboundCommandDispatcher.dispatch(disposables, command);
  }

  private void handleNickModeUserAction(
      CompositeDisposable disposables, TargetRef context, String nick, String mode) {
    if (context == null || !context.isChannel()) {
      return;
    }
    String line = "MODE " + context.target() + " " + mode + " " + nick;
    sendUserActionRaw(disposables, context, "(mode)", "(mode-error)", line);
  }

  private void handleKickUserAction(
      CompositeDisposable disposables, TargetRef context, String nick) {
    if (context == null || !context.isChannel()) {
      return;
    }
    String line = "KICK " + context.target() + " " + nick;
    sendUserActionRaw(disposables, context, "(kick)", "(kick-error)", line);
  }

  private void handleBanUserAction(
      CompositeDisposable disposables, TargetRef context, String nick) {
    if (context == null || !context.isChannel()) {
      return;
    }
    String mask = looksLikeMask(nick) ? nick : (nick + "!*@*");
    String line = "MODE " + context.target() + " +b " + mask;
    sendUserActionRaw(disposables, context, "(mode)", "(mode-error)", line);
  }

  private void sendUserActionRaw(
      CompositeDisposable disposables,
      TargetRef out,
      String statusTag,
      String errorTag,
      String line) {
    if (out == null) {
      return;
    }
    String sid = Objects.toString(out.serverId(), "").trim();
    String sendLine = Objects.toString(line, "").trim();
    if (sid.isBlank() || sendLine.isBlank()) {
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(sendLine)) {
      ui.appendStatus(status, statusTag, "Refusing to send multi-line input.");
      return;
    }

    ui.ensureTargetExists(out);
    ui.appendStatus(out, statusTag, "\u2192 " + sendLine);
    disposables.add(
        irc.sendRaw(sid, sendLine)
            .subscribe(() -> {}, err -> ui.appendError(status, errorTag, String.valueOf(err))));
  }

  private static boolean containsCrlf(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
  }

  private static boolean looksLikeMask(String value) {
    if (value == null) {
      return false;
    }
    return value.indexOf('!') >= 0
        || value.indexOf('@') >= 0
        || value.indexOf('*') >= 0
        || value.indexOf('?') >= 0;
  }

  private static String normalizeIrcv3CapabilityKey(String capability) {
    String key = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    return switch (key) {
      case "read-marker", "draft/read-marker" -> "draft/read-marker";
      case "multiline", "draft/multiline" -> "draft/multiline";
      case "chathistory", "draft/chathistory" -> "draft/chathistory";
      case "message-redaction", "draft/message-redaction" -> "draft/message-redaction";
      case "sts",
          "draft/channel-context",
          "draft/reply",
          "draft/react",
          "draft/unreact",
          "draft/typing",
          "typing",
          "draft/message-edit",
          "message-edit" ->
          "";
      default -> key;
    };
  }
}
