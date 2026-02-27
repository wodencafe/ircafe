package cafe.woden.ircclient.ui.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IgnoreListDialogTest {

  @Test
  void formatHardMaskDisplayReturnsMaskWhenNoMetadataPresent() {
    String display =
        IgnoreListDialog.formatHardMaskDisplay(
            "*!*@bad.host", List.of("ALL"), List.of(), 0L, "", IgnoreTextPatternMode.GLOB, false);

    assertEquals("*!*@bad.host", display);
  }

  @Test
  void formatHardMaskDisplayIncludesConfiguredMetadata() {
    long expiresAt = Instant.parse("2026-03-01T12:34:56Z").toEpochMilli();

    String display =
        IgnoreListDialog.formatHardMaskDisplay(
            "*!*@bad.host",
            List.of("MSGS", "NOTICES"),
            List.of("#ircafe", "#ops"),
            expiresAt,
            "afk|brb",
            IgnoreTextPatternMode.REGEXP,
            true);

    assertEquals(
        "*!*@bad.host [levels=MSGS,NOTICES; channels=#ircafe,#ops; expires=2026-03-01T12:34:56Z; pattern=/afk|brb/ (regexp); replies]",
        display);
  }
}
