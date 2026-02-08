package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.ChannelFlagModeState;
import cafe.woden.ircclient.app.state.RecentStatusModeState;
import cafe.woden.ircclient.irc.IrcEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles inbound channel MODE-related events and keeps MODE-specific state out of {@link IrcMediator}.
 */
@Component
public class InboundModeEventHandler {
  private static final Logger log = LoggerFactory.getLogger(InboundModeEventHandler.class);
  private final UiPort ui;
  private final ModeRoutingState modeRoutingState;
  private final JoinModeBurstService joinModeBurstService;
  private final ModeFormattingService modeFormattingService;
  private final ChannelFlagModeState channelFlagModeState;
  private final RecentStatusModeState recentStatusModeState;

  public InboundModeEventHandler(
      UiPort ui,
      ModeRoutingState modeRoutingState,
      JoinModeBurstService joinModeBurstService,
      ModeFormattingService modeFormattingService,
      ChannelFlagModeState channelFlagModeState,
      RecentStatusModeState recentStatusModeState
  ) {
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

  public void handleChannelModeChanged(String serverId, IrcEvent.ChannelModeChanged ev) {
    if (serverId == null || ev == null) return;

    TargetRef chan = new TargetRef(serverId, ev.channel());
    ui.ensureTargetExists(chan);

    String details = ev.details();

    // Weak signal: if this is a status-mode change (+v/+o/+h/+a/+q) we may see a MODE echo right after.
    if (containsStatusMode(details)) {
      recentStatusModeState.markStatusMode(serverId, ev.channel());
    }

    boolean flagOnly = isFlagOnly(details);
    boolean hadFlagState = flagOnly && channelFlagModeState.hasAnyState(serverId, ev.channel());
    boolean changedFlagState = false;
    if (flagOnly) {
      changedFlagState = channelFlagModeState.applyDelta(serverId, ev.channel(), details);
    }

    if (joinModeBurstService.handleChannelModeChanged(serverId, ev.channel(), details)) {
      if (log.isDebugEnabled()) {
        log.debug("MODEDBG handler ChannelModeChanged consumedByJoinBurst serverId={} channel={} by={} details={}",
            serverId, ev.channel(), ev.by(), clip(details));
      }
      return;
    }

    // Suppress no-op flag echoes like "+Cnst" that appear after a status change.
    if (flagOnly) {
      if (!hadFlagState && recentStatusModeState.isRecent(serverId, ev.channel(), 2000L)) {
        if (log.isDebugEnabled()) {
          log.debug("MODEDBG handler suppress echo (no flag state yet) serverId={} channel={} details={}",
              serverId, ev.channel(), clip(details));
        }
        return;
      }
      if (!changedFlagState) {
        if (log.isDebugEnabled()) {
          log.debug("MODEDBG handler suppress no-op flag delta serverId={} channel={} details={}",
              serverId, ev.channel(), clip(details));
        }
        return;
      }
    }

    String byRaw = ev.by();
    var lines = modeFormattingService.prettyModeChange(byRaw, ev.channel(), details);
    if (log.isDebugEnabled()) {
      log.debug("MODEDBG handler ChannelModeChanged serverId={} channel={} by={} details={} -> {} lines",
          serverId, ev.channel(), byRaw, clip(details), lines.size());
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

  private static boolean containsStatusMode(String details) {
    if (details == null) return false;
    String d = details.trim();
    if (d.isEmpty()) return false;

    int sp = d.indexOf(' ');
    if (sp < 0) return false; // no args; not a status-mode change

    String modeToken = d.substring(0, sp);
    for (int i = 0; i < modeToken.length(); i++) {
      char c = modeToken.charAt(i);
      if (c == '+' || c == '-') continue;
      // Common prefix/status modes (may vary by network, but these cover most):
      if (c == 'q' || c == 'a' || c == 'o' || c == 'h' || c == 'v') return true;
    }
    return false;
  }

  private static String clip(Object v) {
    if (v == null) return "<null>";
    String s = String.valueOf(v);
    if (s == null) return "<null>";
    s = s.replace('\n', ' ').replace('\r', ' ');
    if (s.length() > 220) return s.substring(0, 217) + "...";
    return s;
  }

  public void handleChannelModesListed(String serverId, IrcEvent.ChannelModesListed ev) {
    if (serverId == null || ev == null) return;

    // Seed our flag-mode state from the 324 listing (first token is the mode string).
    String listed = ev.details();
    if (listed != null) {
      String d = listed.trim();
      if (!d.isEmpty()) {
        int sp = d.indexOf(' ');
        String token = (sp < 0) ? d : d.substring(0, sp);
        channelFlagModeState.applyDelta(serverId, ev.channel(), token);
      }
    }

    TargetRef chan = new TargetRef(serverId, ev.channel());
    ui.ensureTargetExists(chan);
    joinModeBurstService.discardJoinModeBuffer(serverId, ev.channel());

    TargetRef out = modeRoutingState.removePendingModeTarget(serverId, ev.channel());
    if (out == null) out = chan;
    ui.ensureTargetExists(out);

    String summary = modeFormattingService.describeCurrentChannelModes(ev.details());
    if (summary != null && !summary.isBlank()) {
      if (joinModeBurstService.shouldSuppressModesListedSummary(serverId, ev.channel(), out.equals(chan))) {
        return;
      }
      ui.appendNotice(out, "(mode)", summary);
    }
  }
}
