package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.state.ChannelFlagModeState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.RecentStatusModeState;
import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InboundModeEventHandlerTest {

  private static final String SERVER_ID = "libera";
  private static final String CHANNEL = "##llamas";
  private static final String MODE_DETAILS = "+Cgn";
  private static final String SUMMARY = "Channel modes: +C, +g, no outside messages";

  private final UiPort ui = mock(UiPort.class);
  private final ModeRoutingState modeRoutingState = new ModeRoutingState();
  private final JoinModeBurstService joinModeBurstService = mock(JoinModeBurstService.class);
  private final ModeFormattingService modeFormattingService = mock(ModeFormattingService.class);
  private final ChannelFlagModeState channelFlagModeState = new ChannelFlagModeState();
  private final RecentStatusModeState recentStatusModeState = new RecentStatusModeState();

  private final InboundModeEventHandler handler =
      new InboundModeEventHandler(
          ui,
          modeRoutingState,
          joinModeBurstService,
          modeFormattingService,
          channelFlagModeState,
          recentStatusModeState);

  @BeforeEach
  void setUp() {
    when(modeFormattingService.describeCurrentChannelModes(anyString())).thenReturn(SUMMARY);
    when(joinModeBurstService.shouldSuppressModesListedSummary(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
  }

  @Test
  void suppressesUnsolicitedModesListedSummaryAfterRecentStatusChangeWhenFlagsUnchanged() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);
    recentStatusModeState.markStatusMode(SERVER_ID, CHANNEL);

    handler.handleChannelModesListed(
        SERVER_ID, new IrcEvent.ChannelModesListed(Instant.now(), CHANNEL, MODE_DETAILS));

    verify(ui, never())
        .appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }

  @Test
  void keepsModesListedSummaryForExplicitModeQueryAfterRecentStatusChange() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);
    recentStatusModeState.markStatusMode(SERVER_ID, CHANNEL);
    modeRoutingState.putPendingModeTarget(SERVER_ID, CHANNEL, new TargetRef(SERVER_ID, CHANNEL));

    handler.handleChannelModesListed(
        SERVER_ID, new IrcEvent.ChannelModesListed(Instant.now(), CHANNEL, MODE_DETAILS));

    verify(ui).appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }

  @Test
  void keepsUnsolicitedModesListedSummaryWhenNoRecentStatusChange() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);

    handler.handleChannelModesListed(
        SERVER_ID, new IrcEvent.ChannelModesListed(Instant.now(), CHANNEL, MODE_DETAILS));

    verify(ui).appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }
}
