package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InboundModeEventHandlerTest {

  private static final String SERVER_ID = "libera";
  private static final String CHANNEL = "##llamas";
  private static final String MODE_DETAILS = "+Cgn";
  private static final String SUMMARY = "Channel modes: +C, +g, no outside messages";

  private final UiPort ui = mock(UiPort.class);
  private final ModeRoutingPort modeRoutingState = new InMemoryModeRoutingStatePort();
  private final JoinModeBurstService joinModeBurstService = mock(JoinModeBurstService.class);
  private final ModeFormattingService modeFormattingService = mock(ModeFormattingService.class);
  private final ChannelFlagModeStatePort channelFlagModeState =
      new InMemoryChannelFlagModeStatePort();
  private final RecentStatusModePort recentStatusModeState = new InMemoryRecentStatusModePort();

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

  private static final class InMemoryModeRoutingStatePort implements ModeRoutingPort {
    private final Map<ModeKey, TargetRef> pendingModeTargets = new ConcurrentHashMap<>();

    @Override
    public void putPendingModeTarget(String serverId, String channel, TargetRef target) {
      if (target == null) return;
      pendingModeTargets.put(ModeKey.of(serverId, channel), target);
    }

    @Override
    public TargetRef removePendingModeTarget(String serverId, String channel) {
      return pendingModeTargets.remove(ModeKey.of(serverId, channel));
    }

    @Override
    public TargetRef getPendingModeTarget(String serverId, String channel) {
      return pendingModeTargets.get(ModeKey.of(serverId, channel));
    }

    @Override
    public void clearServer(String serverId) {
      String sid = normalizeServer(serverId);
      pendingModeTargets.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
    }
  }

  private static final class InMemoryChannelFlagModeStatePort implements ChannelFlagModeStatePort {
    private final Map<ModeKey, Set<Character>> channelFlags = new ConcurrentHashMap<>();

    @Override
    public boolean applyDelta(String serverId, String channel, String details) {
      if (serverId == null || channel == null || details == null) return false;
      String d = details.trim();
      if (d.isEmpty() || d.indexOf(' ') >= 0) return false;

      ModeKey key = ModeKey.of(serverId, channel);
      Set<Character> flags =
          channelFlags.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());

      boolean changed = false;
      char sign = '+';
      for (int i = 0; i < d.length(); i++) {
        char c = d.charAt(i);
        if (c == '+' || c == '-') {
          sign = c;
          continue;
        }
        if (!Character.isLetterOrDigit(c)) continue;
        if (sign == '+') {
          if (flags.add(c)) changed = true;
        } else if (flags.remove(c)) {
          changed = true;
        }
      }
      return changed;
    }

    @Override
    public boolean hasAnyState(String serverId, String channel) {
      if (serverId == null || channel == null) return false;
      Set<Character> flags = channelFlags.get(ModeKey.of(serverId, channel));
      return flags != null && !flags.isEmpty();
    }

    @Override
    public String snapshotModeSummary(String serverId, String channel) {
      if (serverId == null || channel == null) return "";
      Set<Character> flags = channelFlags.get(ModeKey.of(serverId, channel));
      if (flags == null || flags.isEmpty()) return "";
      java.util.ArrayList<Character> sorted = new java.util.ArrayList<>(flags);
      java.util.Collections.sort(sorted);
      StringBuilder out = new StringBuilder(sorted.size() + 1);
      out.append('+');
      for (Character flag : sorted) {
        if (flag != null) out.append(flag.charValue());
      }
      return out.length() <= 1 ? "" : out.toString();
    }

    @Override
    public void clearServer(String serverId) {
      String sid = normalizeServer(serverId);
      channelFlags.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
    }

    @Override
    public void clearChannel(String serverId, String channel) {
      if (serverId == null || channel == null) return;
      channelFlags.remove(ModeKey.of(serverId, channel));
    }
  }

  private static final class InMemoryRecentStatusModePort implements RecentStatusModePort {
    private final Map<ModeKey, Long> lastStatusModeMs = new ConcurrentHashMap<>();

    @Override
    public void markStatusMode(String serverId, String channel) {
      if (serverId == null || channel == null) return;
      lastStatusModeMs.put(ModeKey.of(serverId, channel), System.currentTimeMillis());
    }

    @Override
    public boolean isRecent(String serverId, String channel, long withinMs) {
      if (serverId == null || channel == null) return false;
      Long atMs = lastStatusModeMs.get(ModeKey.of(serverId, channel));
      return atMs != null && (System.currentTimeMillis() - atMs.longValue()) <= withinMs;
    }

    @Override
    public void clearServer(String serverId) {
      String sid = normalizeServer(serverId);
      lastStatusModeMs.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
    }

    @Override
    public void clearChannel(String serverId, String channel) {
      if (serverId == null || channel == null) return;
      lastStatusModeMs.remove(ModeKey.of(serverId, channel));
    }
  }

  private static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private record ModeKey(String serverId, String channelLower) {
    ModeKey {
      serverId = normalizeServer(serverId);
      channelLower = Objects.toString(channelLower, "").trim().toLowerCase(Locale.ROOT);
    }

    static ModeKey of(String serverId, String channel) {
      return new ModeKey(serverId, channel);
    }
  }
}
