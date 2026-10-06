package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreIrcEnvelopeTest {
  private final QuasselIrcv3RuntimeSupport runtime =
      new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath());

  @Test
  void decodesTaggedMessageWithoutTreatingTrailingWordsAsParameters() {
    String raw = "  @label=a\\sb;+draft/reply=42 :alice!user@host   privmsg  #cafe :hello :world  ";
    var envelope = QuasselCoreIrcEnvelope.parse(raw, runtime);
    assertTrue(envelope.parsed());
    assertEquals(raw.trim(), envelope.rawLine());
    assertEquals("alice!user@host", envelope.source());
    assertEquals("PRIVMSG", envelope.command());
    assertEquals(List.of("#cafe"), envelope.params());
    assertEquals("a b", envelope.ircv3Tags().get("label"));
    assertEquals("42", envelope.ircv3Tags().get("draft/reply"));
    assertEquals("hello :world", envelope.payloadText(raw));
    assertThrows(UnsupportedOperationException.class, () -> envelope.params().add("extra"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "@label=only",
        ":server",
        "@label=x :server",
        "hello ordinary text",
        "PING :token"
      })
  void leavesOrdinaryTextAndIncompleteEnvelopesUntouched(String raw) {
    var envelope = QuasselCoreIrcEnvelope.parse(raw, runtime);
    assertFalse(envelope.parsed());
    assertEquals(raw == null ? "" : raw, envelope.payloadText(raw));
    assertTrue(envelope.ircv3Tags().isEmpty());
  }

  @Test
  void taggedUnknownCommandRetainsMetadataWithoutChangingItsDisplayText() {
    String raw = "@label=lookup :irc.example 311 me alice user host * :Alice";
    var envelope = QuasselCoreIrcEnvelope.parse(raw, runtime);
    assertTrue(envelope.parsed());
    assertEquals("311", envelope.command());
    assertEquals("lookup", envelope.ircv3Tags().get("label"));
    assertEquals(raw, envelope.payloadText(raw));
  }

  @Test
  void tagOnlyMessageHasNoDisplayTextAndBlankMessagePayloadRetainsFallback() {
    String tagMessage = "@+typing=active TAGMSG #cafe :ignored";
    assertEquals("", QuasselCoreIrcEnvelope.parse(tagMessage, runtime).payloadText(tagMessage));
    String blankNotice = "NOTICE #cafe :";
    assertEquals(
        blankNotice, QuasselCoreIrcEnvelope.parse(blankNotice, runtime).payloadText(blankNotice));
  }

  @Test
  void capabilityListsSupportTrailingAndPositionalForms() {
    var trailing =
        QuasselCoreIrcEnvelope.parse("CAP me ACK :message-tags -draft/multiline", runtime);
    var positional =
        QuasselCoreIrcEnvelope.parse("CAP me ACK message-tags -draft/multiline", runtime);
    assertEquals("ACK", trailing.capSubcommand());
    assertEquals(trailing.capSubcommand(), positional.capSubcommand());
    assertEquals("message-tags -draft/multiline", trailing.capList());
    assertEquals(trailing.capList(), positional.capList());
    assertEquals("", QuasselCoreIrcEnvelope.parse("CAP me UNKNOWN :caps", runtime).capSubcommand());
    assertEquals("LS", QuasselCoreIrcEnvelope.parse("CAP LS", runtime).capSubcommand());
  }
}
