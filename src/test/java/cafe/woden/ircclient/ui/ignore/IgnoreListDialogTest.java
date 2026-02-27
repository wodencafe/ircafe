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

  @Test
  void parseLevelsInputAcceptsKnownTokensAndAliases() {
    IgnoreListDialog.ParseResult<List<String>> parsed =
        IgnoreListDialog.parseLevelsInput("msgs,+notices,*");

    assertEquals(List.of("MSGS", "NOTICES", "ALL"), parsed.value());
    assertEquals(null, parsed.error());
  }

  @Test
  void parseLevelsInputRejectsUnknownToken() {
    IgnoreListDialog.ParseResult<List<String>> parsed = IgnoreListDialog.parseLevelsInput("msggs");

    assertEquals(null, parsed.value());
    assertEquals("Unknown ignore level: \"msggs\"", parsed.error());
  }

  @Test
  void parseChannelsInputParsesAndDeduplicates() {
    IgnoreListDialog.ParseResult<List<String>> parsed =
        IgnoreListDialog.parseChannelsInput("#ircafe, #ops #IRCAFE");

    assertEquals(List.of("#ircafe", "#ops"), parsed.value());
    assertEquals(null, parsed.error());
  }

  @Test
  void parseChannelsInputRejectsInvalidChannelPrefix() {
    IgnoreListDialog.ParseResult<List<String>> parsed =
        IgnoreListDialog.parseChannelsInput("ircafe");

    assertEquals(null, parsed.value());
    assertEquals("Channel patterns must start with # or &: \"ircafe\"", parsed.error());
  }

  @Test
  void parseExpiryInputAcceptsIsoAndEpoch() {
    IgnoreListDialog.ParseResult<Long> iso =
        IgnoreListDialog.parseExpiryInputEpochMs("2026-03-01T12:34:56Z");
    IgnoreListDialog.ParseResult<Long> epoch =
        IgnoreListDialog.parseExpiryInputEpochMs("1772368496000");

    assertEquals(Long.valueOf(Instant.parse("2026-03-01T12:34:56Z").toEpochMilli()), iso.value());
    assertEquals(null, iso.error());
    assertEquals(Long.valueOf(1_772_368_496_000L), epoch.value());
    assertEquals(null, epoch.error());
  }

  @Test
  void parseExpiryInputRejectsInvalidValue() {
    IgnoreListDialog.ParseResult<Long> parsed =
        IgnoreListDialog.parseExpiryInputEpochMs("tomorrow");

    assertEquals(null, parsed.value());
    assertEquals("Invalid expiry format. Use ISO-8601 instant or epoch millis.", parsed.error());
  }

  @Test
  void parseMaskInputNormalizesNickToHostmask() {
    IgnoreListDialog.ParseResult<String> parsed = IgnoreListDialog.parseMaskInput("badnick");

    assertEquals("badnick!*@*", parsed.value());
    assertEquals(null, parsed.error());
  }

  @Test
  void parseMaskInputRejectsBlankMask() {
    IgnoreListDialog.ParseResult<String> parsed = IgnoreListDialog.parseMaskInput(" ");

    assertEquals(null, parsed.value());
    assertEquals("Mask is required.", parsed.error());
  }

  @Test
  void hardIgnoreModeHintTextReflectsMode() {
    assertEquals(
        "Simple mode: Add is mask-only (legacy).", IgnoreListDialog.hardIgnoreModeHintText(false));
    assertEquals(
        "Advanced mode: Add/Edit uses irssi-style rule fields.",
        IgnoreListDialog.hardIgnoreModeHintText(true));
  }
}
