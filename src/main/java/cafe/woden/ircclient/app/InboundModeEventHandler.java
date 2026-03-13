package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.NegotiatedModeSemantics;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Handles inbound channel MODE-related events and keeps MODE-specific state out of {@link
 * IrcMediator}.
 */
@Component
@ApplicationLayer
public class InboundModeEventHandler {
  private final UiPort ui;
  private final ModeRoutingPort modeRoutingState;
  private final JoinModeBurstService joinModeBurstService;
  private final ModeFormattingService modeFormattingService;
  private final ChannelFlagModeStatePort channelFlagModeState;
  private final RecentStatusModePort recentStatusModeState;
  private final ServerIsupportStatePort serverIsupportState;

  public InboundModeEventHandler(
      UiPort ui,
      ModeRoutingPort modeRoutingState,
      JoinModeBurstService joinModeBurstService,
      ModeFormattingService modeFormattingService,
      ChannelFlagModeStatePort channelFlagModeState,
      RecentStatusModePort recentStatusModeState,
      ServerIsupportStatePort serverIsupportState) {
    this.ui = ui;
    this.modeRoutingState = modeRoutingState;
    this.joinModeBurstService = joinModeBurstService;
    this.modeFormattingService = modeFormattingService;
    this.channelFlagModeState = channelFlagModeState;
    this.recentStatusModeState = recentStatusModeState;
    this.serverIsupportState = serverIsupportState;
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
    IrcEvent.ChannelModeKind effectiveKind = effectiveModeKind(serverId, ev);
    if (effectiveKind == IrcEvent.ChannelModeKind.SNAPSHOT) {
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
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    if (containsStatusMode(vocabulary, details)) {
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
            modeFormattingService.describeCurrentChannelModes(serverId, rawModes));
      }
    }

    if (joinModeBurstService.handleChannelModeChanged(serverId, channel, details)) {
      return;
    }

    // Suppress no-op flag echoes like "+Cnst" that appear after a status change.
    if (flagOnly) {
      if (!hadFlagState && recentStatusModeState.isRecent(serverId, channel, 2000L)) {
        return;
      }
      if (!changedFlagState) {
        return;
      }
    }

    var lines = modeFormattingService.prettyModeChange(serverId, by, channel, details);
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

  private IrcEvent.ChannelModeKind effectiveModeKind(
      String serverId, IrcEvent.ChannelModeObserved ev) {
    if (ev == null) return IrcEvent.ChannelModeKind.DELTA;
    if (ev.provenance() != IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT) return ev.kind();
    if (!java.util.Objects.toString(ev.by(), "").trim().isEmpty()) {
      return IrcEvent.ChannelModeKind.DELTA;
    }
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    return NegotiatedModeSemantics.looksLikeSnapshotModeDetails(vocabulary, ev.details())
        ? IrcEvent.ChannelModeKind.SNAPSHOT
        : IrcEvent.ChannelModeKind.DELTA;
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

    String summary = modeFormattingService.describeCurrentChannelModes(serverId, details);
    String rawModes = normalizeModeDetailsForSnapshot(details);
    if (!rawModes.isBlank() || (summary != null && !summary.isBlank())) {
      ui.setChannelModeSnapshot(serverId, channel, rawModes, summary);
    }
    if (summary == null || summary.isBlank()) {
      return;
    }

    boolean outputIsChannel = out.equals(chan);
    if (joinModeBurstService.shouldSuppressModesListedSummary(serverId, channel, outputIsChannel)) {
      return;
    }

    if (shouldSuppressLiveModeSnapshot(
        provenance, hadPendingModeTarget, outputIsChannel, joinBootstrapActive)) {
      return;
    }

    // Some networks emit a MODE listing (324) immediately after +v/+o updates.
    // If the summary is unsolicited and does not change our tracked channel flags, suppress it.
    if (!hadPendingModeTarget
        && outputIsChannel
        && recentStatusModeState.isRecent(serverId, channel, 2000L)
        && (!hadFlagState || !changedFlagState)) {
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

  private static boolean containsStatusMode(ModeVocabulary vocabulary, String details) {
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
      if (NegotiatedModeSemantics.takesArgument(vocabulary, c, adding)
          && argIdx < argTokens.length) {
        arg = argTokens[argIdx++];
      }
      if (NegotiatedModeSemantics.isStatusMode(vocabulary, c, arg)) return true;
    }
    return false;
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
