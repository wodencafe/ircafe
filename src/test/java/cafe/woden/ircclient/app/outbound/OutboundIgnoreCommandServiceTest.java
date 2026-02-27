package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundIgnoreCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final IgnoreListQueryPort ignoreListQueryPort = mock(IgnoreListQueryPort.class);
  private final IgnoreListCommandPort ignoreListCommandPort = mock(IgnoreListCommandPort.class);

  private final OutboundIgnoreCommandService service =
      new OutboundIgnoreCommandService(
          ui, targetCoordinator, ignoreListQueryPort, ignoreListCommandPort);

  @Test
  void ignoreWithoutArgsListsMasksForActiveServer() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListQueryPort.listMasks("libera")).thenReturn(List.of("alice!*@*", "*!*@bad.host"));

    service.handleIgnore("   ");

    TargetRef status = new TargetRef("libera", "status");
    verify(ui).appendStatus(status, "(ignore)", "Ignore masks (2): ");
    verify(ui).appendStatus(status, "(ignore)", "  1) alice!*@*");
    verify(ui).appendStatus(status, "(ignore)", "  2) *!*@bad.host");
  }

  @Test
  void ignoreListShowsRepliesMetadataWhenConfigured() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListQueryPort.listMasks("libera")).thenReturn(List.of("alice!*@*"));
    when(ignoreListQueryPort.repliesForHardMask("libera", "alice!*@*")).thenReturn(true);

    service.handleIgnore(" ");

    TargetRef status = new TargetRef("libera", "status");
    verify(ui).appendStatus(status, "(ignore)", "Ignore masks (1): ");
    verify(ui).appendStatus(status, "(ignore)", "  1) alice!*@* [replies]");
  }

  @Test
  void ignoreParsesIrssiLevelsAndAddsMask() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS", "NOTICES"),
            List.of(),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("MSGS NOTICES *!*@bad.host");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS", "NOTICES"),
            List.of(),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false);
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: *!*@bad.host");
    verify(ui, never())
        .appendStatus(
            eq(new TargetRef("libera", "status")),
            eq("(ignore)"),
            contains("ignore levels were parsed"));
  }

  @Test
  void ignoreWithNetworkOptionCanWorkWithoutActiveTarget() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-network libera badnick");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false);
    verify(ui).appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: badnick!*@*");
  }

  @Test
  void ignoreParsesChannelsOptionAndStoresChannelScopedRule() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of("#ircafe", "#ops"),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-channels #ircafe,#ops MSGS *!*@bad.host");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of("#ircafe", "#ops"),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false);
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: *!*@bad.host");
  }

  @Test
  void ignoreParsesPatternRegexpAndStoresPatternMetadata() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of(),
            null,
            "afk|brb",
            IgnoreTextPatternMode.REGEXP,
            false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-pattern afk|brb -regexp MSGS *!*@bad.host");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of(),
            null,
            "afk|brb",
            IgnoreTextPatternMode.REGEXP,
            false);
  }

  @Test
  void ignoreParsesTimeOptionAndStoresAbsoluteExpiry() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            eq("libera"),
            eq("*!*@bad.host"),
            eq(List.of("MSGS")),
            eq(List.of()),
            longThat(v -> v > System.currentTimeMillis()),
            eq(""),
            eq(IgnoreTextPatternMode.GLOB),
            eq(false)))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    long before = System.currentTimeMillis();
    service.handleIgnore("-time 10min MSGS *!*@bad.host");
    long after = System.currentTimeMillis();

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            eq("libera"),
            eq("*!*@bad.host"),
            eq(List.of("MSGS")),
            eq(List.of()),
            longThat(v -> v >= before + 9 * 60_000L && v <= after + 10 * 60_000L + 2_000L),
            eq(""),
            eq(IgnoreTextPatternMode.GLOB),
            eq(false));
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: *!*@bad.host");
  }

  @Test
  void ignoreRejectsInvalidTimeOption() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));

    service.handleIgnore("-time nope MSGS *!*@bad.host");

    verify(ignoreListCommandPort, never())
        .addMaskWithLevels(
            eq("libera"),
            eq("*!*@bad.host"),
            eq(List.of("MSGS")),
            eq(List.of()),
            isNull(),
            eq(""),
            eq(IgnoreTextPatternMode.GLOB),
            eq(false));
    verify(ui)
        .appendStatus(
            eq(new TargetRef("libera", "status")), eq("(ignore)"), contains("Invalid -time value"));
  }

  @Test
  void ignoreRejectsInvalidRegexpPattern() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));

    service.handleIgnore("-pattern ( -regexp MSGS *!*@bad.host");

    verify(ignoreListCommandPort, never())
        .addMaskWithLevels(
            eq("libera"),
            eq("*!*@bad.host"),
            eq(List.of("MSGS")),
            eq(List.of()),
            isNull(),
            eq("("),
            eq(IgnoreTextPatternMode.REGEXP),
            eq(false));
    verify(ui)
        .appendStatus(
            eq(new TargetRef("libera", "status")),
            eq("(ignore)"),
            contains("Invalid -pattern regexp"));
  }

  @Test
  void ignoreParsesRepliesOptionAndStoresRepliesMetadata() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of(),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            true))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-replies MSGS *!*@bad.host");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "*!*@bad.host",
            List.of("MSGS"),
            List.of(),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            true);
  }

  @Test
  void unignoreSupportsNumericIndex() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListQueryPort.listMasks("libera")).thenReturn(List.of("a!*@*", "b!*@*"));
    when(ignoreListCommandPort.removeMask("libera", "b!*@*")).thenReturn(true);

    service.handleUnignore("2");

    verify(ignoreListCommandPort).removeMask("libera", "b!*@*");
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(unignore)", "Removed ignore: b!*@*");
  }

  @Test
  void ignoreExceptOptionUsesUnignoreCompatibilityPath() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.removeMask("libera", "badnick")).thenReturn(true);

    service.handleIgnore("-except badnick");

    verify(ignoreListCommandPort).removeMask("libera", "badnick");
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"), "(unignore)", "Removed ignore: badnick!*@*");
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"),
            "(ignore)",
            "Applied irssi-style -except as /unignore (IRCafe compatibility mode).");
  }
}
