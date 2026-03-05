package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles inbound channel MODE-related events and keeps MODE-specific state out of {@link
 * IrcMediator}.
 */
@Component
public class InboundModeEventHandler {
  private static final Logger log = LoggerFactory.getLogger(InboundModeEventHandler.class);
  private final UiPort ui;
  private final ModeRoutingPort modeRoutingState;
  private final JoinModeBurstService joinModeBurstService;
  private final ModeFormattingService modeFormattingService;
  private final ChannelFlagModeStatePort channelFlagModeState;
  private final RecentStatusModePort recentStatusModeState;

  public InboundModeEventHandler(
      UiPort ui,
      ModeRoutingPort modeRoutingState,
      JoinModeBurstService joinModeBurstService,
      ModeFormattingService modeFormattingService,
      ChannelFlagModeStatePort channelFlagModeState,
      RecentStatusModePort recentStatusModeState) {
    this.ui = ui;
    this.modeRoutingState = modeRoutingState;
    this.joinModeBurstService = joinModeBurstService;
    this.modeFormattingService = modeFormattingService;
    this.channelFlagModeState = channelFlagModeState;
    this.recentStatusModeState = recentStatusModeState;
  }

  public void onJoinedChannel(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    joinModeBurstService.startJoinModeBuffer(serverId, channel);
  }

  public void onChannelTopicUpdated(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    joinModeBurstService.flushJoinModesIfAny(serverId, channel, false);
  }

  public void onNickListUpdated(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    joinModeBurstService.flushJoinModesIfAny(serverId, channel, false);
  }

  public void clearServer(String serverId) {
    if (serverId == null) return;
    joinModeBurstService.clearServer(serverId);
    channelFlagModeState.clearServer(serverId);
    recentStatusModeState.clearServer(serverId);
  }

  public void onLeftChannel(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    joinModeBurstService.clearChannel(serverId, channel);
    channelFlagModeState.clearChannel(serverId, channel);
    recentStatusModeState.clearChannel(serverId, channel);
    ui.setChannelModeSnapshot(serverId, channel, "", "");
  }

  public void handleChannelModeObserved(String serverId, IrcEvent.ChannelModeObserved ev) {
    if (serverId == null || ev == null) return;
    if (ev.kind() == IrcEvent.ChannelModeKind.SNAPSHOT) {
      handleChannelModeSnapshot(serverId, ev.channel(), ev.details(), ev.provenance());
      return;
    }
    handleChannelModeDelta(serverId, ev.channel(), ev.by(), ev.details());
  }

  private void handleChannelModeDelta(String serverId, String channel, String by, String details) {
    if (serverId == null || channel == null) return;

    TargetRef chan = new TargetRef(serverId, channel);
    ui.ensureTargetExists(chan);

    // Weak signal: if this is a status-mode change (+v/+o/+h/+a/+q) we may see a MODE echo right
    // after.
    if (containsStatusMode(details)) {
      recentStatusModeState.markStatusMode(serverId, channel);
    }

    boolean flagOnly = isFlagOnly(details);
    boolean hadFlagState = flagOnly && channelFlagModeState.hasAnyState(serverId, channel);
    boolean changedFlagState = false;
    if (flagOnly) {
      changedFlagState = channelFlagModeState.applyDelta(serverId, channel, details);
      String rawModes = channelFlagModeState.snapshotModeSummary(serverId, channel);
      if (!rawModes.isBlank()) {
        ui.setChannelModeSnapshot(
            serverId,
            channel,
            rawModes,
            modeFormattingService.describeCurrentChannelModes(rawModes));
      }
    }

    if (joinModeBurstService.handleChannelModeChanged(serverId, channel, details)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "MODEDBG handler ChannelModeChanged consumedByJoinBurst serverId={} channel={} by={} details={}",
            serverId,
            channel,
            by,
            clip(details));
      }
      return;
    }

    // Suppress no-op flag echoes like "+Cnst" that appear after a status change.
    if (flagOnly) {
      if (!hadFlagState && recentStatusModeState.isRecent(serverId, channel, 2000L)) {
        if (log.isDebugEnabled()) {
          log.debug(
              "MODEDBG handler suppress echo (no flag state yet) serverId={} channel={} details={}",
              serverId,
              channel,
              clip(details));
        }
        return;
      }
      if (!changedFlagState) {
        if (log.isDebugEnabled()) {
          log.debug(
              "MODEDBG handler suppress no-op flag delta serverId={} channel={} details={}",
              serverId,
              channel,
              clip(details));
        }
        return;
      }
    }

    var lines = modeFormattingService.prettyModeChange(by, channel, details);
    if (log.isDebugEnabled()) {
      log.debug(
          "MODEDBG handler ChannelModeChanged serverId={} channel={} by={} details={} -> {} lines",
          serverId,
          channel,
          by,
          clip(details),
          lines.size());
    }
    for (String line : lines) {
      ui.appendNotice(chan, "(mode)", line);
    }
  }

  private static boolean isFlagOnly(String details) {
    if (details == null) return false;
    String d = details.trim();
    if (d.isEmpty()) return false;
    return d.indexOf(' ') < 0;
  }

  private void handleChannelModeSnapshot(
      String serverId, String channel, String details, IrcEvent.ChannelModeProvenance provenance) {
    if (serverId == null || channel == null) return;

    boolean hadFlagState = channelFlagModeState.hasAnyState(serverId, channel);
    boolean changedFlagState = false;
    if (details != null) {
      String d = details.trim();
      if (!d.isEmpty()) {
        int sp = d.indexOf(' ');
        String token = (sp < 0) ? d : d.substring(0, sp);
        changedFlagState = channelFlagModeState.applyDelta(serverId, channel, token);
      }
    }

    boolean joinBootstrapActive = joinModeBurstService.hasActiveJoinModeBuffer(serverId, channel);
    TargetRef chan = new TargetRef(serverId, channel);
    ui.ensureTargetExists(chan);
    joinModeBurstService.discardJoinModeBuffer(serverId, channel);

    TargetRef out = modeRoutingState.removePendingModeTarget(serverId, channel);
    boolean hadPendingModeTarget = out != null;
    if (out == null) out = chan;
    ui.ensureTargetExists(out);

    String summary = modeFormattingService.describeCurrentChannelModes(details);
    String rawModes = normalizeModeDetailsForSnapshot(details);
    if (!rawModes.isBlank() || (summary != null && !summary.isBlank())) {
      ui.setChannelModeSnapshot(serverId, channel, rawModes, summary);
    }
    if (summary == null || summary.isBlank()) return;

    boolean outputIsChannel = out.equals(chan);
    if (joinModeBurstService.shouldSuppressModesListedSummary(serverId, channel, outputIsChannel)) {
      return;
    }

    if (shouldSuppressLiveModeSnapshot(
        provenance, hadPendingModeTarget, outputIsChannel, joinBootstrapActive)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "MODEDBG handler suppress live snapshot echo serverId={} channel={} provenance={} details={}",
            serverId,
            channel,
            provenance,
            clip(details));
      }
      return;
    }

    // Some networks emit a MODE listing (324) immediately after +v/+o updates.
    // If the summary is unsolicited and does not change our tracked channel flags, suppress it.
    if (!hadPendingModeTarget
        && outputIsChannel
        && recentStatusModeState.isRecent(serverId, channel, 2000L)
        && (!hadFlagState || !changedFlagState)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "MODEDBG handler suppress 324 status echo serverId={} channel={} details={} hadFlagState={} changedFlagState={}",
            serverId,
            channel,
            clip(details),
            hadFlagState,
            changedFlagState);
      }
      return;
    }
    ui.appendNotice(out, "(mode)", summary);
  }

  private static boolean shouldSuppressLiveModeSnapshot(
      IrcEvent.ChannelModeProvenance provenance,
      boolean hadPendingModeTarget,
      boolean outputIsChannel,
      boolean joinBootstrapActive) {
    if (provenance != IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT) return false;
    if (hadPendingModeTarget || !outputIsChannel) return false;
    return !joinBootstrapActive;
  }

  private static boolean containsStatusMode(String details) {
    if (details == null) return false;
    String d = details.trim();
    if (d.isEmpty()) return false;

    int sp = d.indexOf(' ');
    if (sp < 0) return false; // no args; not a status-mode change

    String modeToken = d.substring(0, sp);
    String argsPart = d.substring(sp + 1).trim();
    String[] argTokens = argsPart.isEmpty() ? new String[0] : argsPart.split("\\s+");
    int argIdx = 0;
    boolean adding = true;
    for (int i = 0; i < modeToken.length(); i++) {
      char c = modeToken.charAt(i);
      if (c == '+') {
        adding = true;
        continue;
      }
      if (c == '-') {
        adding = false;
        continue;
      }
      String arg = null;
      if (modeTakesArg(c, adding) && argIdx < argTokens.length) {
        arg = argTokens[argIdx++];
      }
      // Common prefix/status modes (network-dependent).
      if (c == 'a' || c == 'o' || c == 'h' || c == 'v') return true;
      // +q is ambiguous across networks: owner status vs quiet-mask list mode.
      if (c == 'q' && !looksLikeQuietMaskTarget(arg)) return true;
    }
    return false;
  }

  private static boolean modeTakesArg(char mode, boolean adding) {
    return switch (mode) {
      case 'o', 'v', 'h', 'a', 'q', 'y', 'b', 'e', 'I', 'k', 'f', 'j' -> true;
      case 'l' -> adding;
      default -> false;
    };
  }

  private static boolean looksLikeQuietMaskTarget(String arg) {
    String a = (arg == null) ? "" : arg.trim();
    if (a.isEmpty()) return false;
    return a.indexOf('!') >= 0
        || a.indexOf('@') >= 0
        || a.indexOf('*') >= 0
        || a.indexOf('$') >= 0
        || a.indexOf(':') >= 0;
  }

  private static String clip(Object v) {
    if (v == null) return "<null>";
    String s = String.valueOf(v);
    if (s == null) return "<null>";
    s = s.replace('\n', ' ').replace('\r', ' ');
    if (s.length() > 220) return s.substring(0, 217) + "...";
    return s;
  }

  private static String normalizeModeDetailsForSnapshot(String details) {
    if (details == null) return "";
    String d = details.trim();
    if (d.isEmpty()) return "";

    String det = d;
    if (det.startsWith(":")) {
      int sp = det.indexOf(' ');
      if (sp > 0) det = det.substring(sp + 1).trim();
    }

    String[] toks = det.split("\\s+");
    for (int i = 0; i < toks.length; i++) {
      if ("MODE".equalsIgnoreCase(toks[i])) {
        int idx = i + 2; // MODE <channel> <modes...>
        if (idx >= toks.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int j = idx; j < toks.length; j++) {
          if (j > idx) sb.append(' ');
          sb.append(toks[j]);
        }
        det = sb.toString().trim();
        break;
      }
    }

    if (det.isEmpty()) return "";
    String[] modeToks = det.split("\\s+");
    if (modeToks.length == 0) return "";
    String first = modeToks[0];
    if (first.indexOf('+') < 0 && first.indexOf('-') < 0) return "";
    return det;
  }
}
