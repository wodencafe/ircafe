package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.atLeastOnce;
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
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  @Test
  void ignoreListMetadataRenderingMatchesDialogFormat() {
    TargetRef status = new TargetRef("libera", "status");
    String mask = "*!*@bad.host";
    long expiresAt = Instant.parse("2026-03-01T12:34:56Z").toEpochMilli();
    List<String> levels = List.of("MSGS", "NOTICES");
    List<String> channels = List.of("#ircafe", "#ops");

    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListQueryPort.listMasks("libera")).thenReturn(List.of(mask));
    when(ignoreListQueryPort.levelsForHardMask("libera", mask)).thenReturn(levels);
    when(ignoreListQueryPort.channelsForHardMask("libera", mask)).thenReturn(channels);
    when(ignoreListQueryPort.expiresAtEpochMsForHardMask("libera", mask)).thenReturn(expiresAt);
    when(ignoreListQueryPort.patternForHardMask("libera", mask)).thenReturn("afk|brb");
    when(ignoreListQueryPort.patternModeForHardMask("libera", mask))
        .thenReturn(IgnoreTextPatternMode.REGEXP);
    when(ignoreListQueryPort.repliesForHardMask("libera", mask)).thenReturn(true);

    service.handleIgnore(" ");

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    verify(ui, atLeastOnce()).appendStatus(eq(status), eq("(ignore)"), messages.capture());
    String commandRow =
        messages.getAllValues().stream()
            .filter(message -> message.startsWith("  1) "))
            .findFirst()
            .orElseThrow();
    String commandDisplay = commandRow.substring("  1) ".length());
    String normalizedCommandDisplay =
        commandDisplay.replaceAll("(expires=[^;\\]]+) \\([^\\)]*\\)", "$1");

    String dialogDisplay =
        formatDialogDisplay(
            mask, levels, channels, expiresAt, "afk|brb", IgnoreTextPatternMode.REGEXP, true);
    assertEquals(dialogDisplay, normalizedCommandDisplay);
  }

  @Test
  void ignoreParsesQuotedPatternAndReportsReasonCompatibility() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "badnick",
            List.of("MSGS"),
            List.of(),
            null,
            "afk brb",
            IgnoreTextPatternMode.FULL,
            false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-pattern \"afk brb\" -full MSGS badnick noisy traffic");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "badnick",
            List.of("MSGS"),
            List.of(),
            null,
            "afk brb",
            IgnoreTextPatternMode.FULL,
            false);
    verify(ui).appendStatus(status, "(ignore)", "Ignoring: badnick!*@*");
    verify(ui)
        .appendStatus(
            eq(status),
            eq("(ignore)"),
            contains("Compatibility: trailing reason text parsed but not persisted"));
  }

  @Test
  void ignoreTreatsPatternModifiersWithoutPatternAsCompatibilityNoOp() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-regexp badnick");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false);
    verify(ui)
        .appendStatus(
            status,
            "(ignore)",
            "Compatibility: -regexp/-full provided without -pattern; modifier ignored.");
  }

  @Test
  void ignoreParsesChannelsByFilteringInvalidAndDuplicateEntries() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListCommandPort.addMaskWithLevels(
            "libera",
            "badnick",
            List.of("MSGS"),
            List.of("#ircafe", "&ops"),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("-channels #ircafe,bad,#IRCAFE,&ops MSGS badnick");

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            "libera",
            "badnick",
            List.of("MSGS"),
            List.of("#ircafe", "&ops"),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false);
  }

  @Test
  void ignoreParsesCompoundDurationValues() {
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
    service.handleIgnore("-time 1h30m MSGS *!*@bad.host");
    long after = System.currentTimeMillis();

    verify(ignoreListCommandPort)
        .addMaskWithLevels(
            eq("libera"),
            eq("*!*@bad.host"),
            eq(List.of("MSGS")),
            eq(List.of()),
            longThat(
                v ->
                    v >= before + (90L * 60_000L) - 2_000L
                        && v <= after + (90L * 60_000L) + 2_000L),
            eq(""),
            eq(IgnoreTextPatternMode.GLOB),
            eq(false));
  }

  private static String formatDialogDisplay(
      String mask,
      List<String> levels,
      List<String> channels,
      long expiresAtEpochMs,
      String pattern,
      IgnoreTextPatternMode mode,
      boolean replies) {
    try {
      Method m =
          IgnoreListDialog.class.getDeclaredMethod(
              "formatHardMaskDisplay",
              String.class,
              List.class,
              List.class,
              long.class,
              String.class,
              IgnoreTextPatternMode.class,
              boolean.class);
      m.setAccessible(true);
      return (String)
          m.invoke(null, mask, levels, channels, expiresAtEpochMs, pattern, mode, replies);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
