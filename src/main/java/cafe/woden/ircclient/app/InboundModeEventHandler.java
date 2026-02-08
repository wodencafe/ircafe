package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.irc.IrcEvent;
import org.springframework.stereotype.Component;

/**
 * Handles inbound channel MODE-related events and keeps MODE-specific state out of {@link IrcMediator}.
 */
@Component
public class InboundModeEventHandler {
  private final UiPort ui;
  private final ModeRoutingState modeRoutingState;
  private final JoinModeBurstService joinModeBurstService;
  private final ModeFormattingService modeFormattingService;

  public InboundModeEventHandler(
      UiPort ui,
      ModeRoutingState modeRoutingState,
      JoinModeBurstService joinModeBurstService,
      ModeFormattingService modeFormattingService
  ) {
    this.ui = ui;
    this.modeRoutingState = modeRoutingState;
    this.joinModeBurstService = joinModeBurstService;
    this.modeFormattingService = modeFormattingService;
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
  }

  public void handleChannelModeChanged(String serverId, IrcEvent.ChannelModeChanged ev) {
    if (serverId == null || ev == null) return;

    TargetRef chan = new TargetRef(serverId, ev.channel());
    ui.ensureTargetExists(chan);

    String details = ev.details();
    if (joinModeBurstService.handleChannelModeChanged(serverId, ev.channel(), details)) {
      return;
    }
    String byRaw = ev.by();
    for (String line : modeFormattingService.prettyModeChange(byRaw, ev.channel(), details)) {
      ui.appendNotice(chan, "(mode)", line);
    }
  }

  public void handleChannelModesListed(String serverId, IrcEvent.ChannelModesListed ev) {
    if (serverId == null || ev == null) return;

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
