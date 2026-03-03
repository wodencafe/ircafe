package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.state.ChannelFlagModeState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.RecentStatusModeState;
import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.List;
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
    when(modeFormattingService.prettyModeChange(anyString(), anyString(), anyString()))
        .thenReturn(List.of("mode line"));
    when(joinModeBurstService.shouldSuppressModesListedSummary(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
  }

  @Test
  void suppressesUnsolicitedModesListedSummaryAfterRecentStatusChangeWhenFlagsUnchanged() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);
    recentStatusModeState.markStatusMode(SERVER_ID, CHANNEL);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            MODE_DETAILS,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.NUMERIC_324));

    verify(ui, never())
        .appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }

  @Test
  void keepsModesListedSummaryForExplicitModeQueryAfterRecentStatusChange() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);
    recentStatusModeState.markStatusMode(SERVER_ID, CHANNEL);
    modeRoutingState.putPendingModeTarget(SERVER_ID, CHANNEL, new TargetRef(SERVER_ID, CHANNEL));

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            MODE_DETAILS,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.NUMERIC_324));

    verify(ui).appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }

  @Test
  void keepsUnsolicitedModesListedSummaryWhenNoRecentStatusChange() {
    channelFlagModeState.applyDelta(SERVER_ID, CHANNEL, MODE_DETAILS);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            MODE_DETAILS,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.NUMERIC_324));

    verify(ui).appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(SUMMARY));
  }

  @Test
  void suppressesUnsolicitedLiveSnapshotAfterOperatorModeChange() {
    String opLine = "FurBot gives channel operator privileges to Arca.";
    String snapshotDetails = "+nrf [10j#R10]:5";
    String snapshotSummary = "Channel modes: no outside messages, registered only, +f";
    when(modeFormattingService.prettyModeChange("FurBot", CHANNEL, "+o Arca"))
        .thenReturn(List.of(opLine));
    when(modeFormattingService.describeCurrentChannelModes(snapshotDetails))
        .thenReturn(snapshotSummary);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "FurBot",
            "+o Arca",
            IrcEvent.ChannelModeKind.DELTA,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));
    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            snapshotDetails,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));

    verify(ui).appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(opLine));
    verify(ui, never())
        .appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(snapshotSummary));
  }

  @Test
  void keepsLiveSnapshotDuringActiveJoinBootstrap() {
    String snapshotDetails = "+nrf [10j#R10]:5";
    String snapshotSummary = "Channel modes: no outside messages, registered only, +f";
    when(modeFormattingService.describeCurrentChannelModes(snapshotDetails))
        .thenReturn(snapshotSummary);
    when(joinModeBurstService.hasActiveJoinModeBuffer(SERVER_ID, CHANNEL)).thenReturn(true);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            snapshotDetails,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));

    verify(ui)
        .appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(snapshotSummary));
  }

  @Test
  void keepsLiveSnapshotForExplicitModeQueryTarget() {
    String snapshotDetails = "+nrf [10j#R10]:5";
    String snapshotSummary = "Channel modes: no outside messages, registered only, +f";
    TargetRef explicitTarget = new TargetRef(SERVER_ID, CHANNEL);
    when(modeFormattingService.describeCurrentChannelModes(snapshotDetails))
        .thenReturn(snapshotSummary);
    modeRoutingState.putPendingModeTarget(SERVER_ID, CHANNEL, explicitTarget);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            snapshotDetails,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));

    verify(ui).appendNotice(eq(explicitTarget), eq("(mode)"), eq(snapshotSummary));
  }

  @Test
  void suppressesUnsolicitedLiveSnapshotEvenWithoutRecentStatusMode() {
    String snapshotDetails = "+nrf [10j#R10]:5";
    String snapshotSummary = "Channel modes: no outside messages, registered only, +f";
    when(modeFormattingService.describeCurrentChannelModes(snapshotDetails))
        .thenReturn(snapshotSummary);

    handler.handleChannelModeObserved(
        SERVER_ID,
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            CHANNEL,
            "",
            snapshotDetails,
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));

    verify(ui, never())
        .appendNotice(eq(new TargetRef(SERVER_ID, CHANNEL)), eq("(mode)"), eq(snapshotSummary));
  }
}
