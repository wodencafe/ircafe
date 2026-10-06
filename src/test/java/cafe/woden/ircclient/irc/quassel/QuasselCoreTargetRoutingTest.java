package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreTargetRoutingTest {
  @Test
  void qualifiedTargetsNormalizeTheirTokenAndKeepTheirOriginalSpelling() {
    var target = parseQualifiedTarget("  #Room {net: NETWORK-2 }  ");

    assertEquals("#Room {net: NETWORK-2 }", target.rawTarget());
    assertEquals("#Room", target.baseTarget());
    assertEquals("network-2", target.networkToken());
    assertEquals("#room", normalizeTargetHintKey(target.rawTarget()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "#room{net:}",
        "#room{net: }",
        "{net:two}",
        "#room{net:two",
        "#room{NET:two}",
        "#room{net:two}tail"
      })
  void malformedOrNonterminalQualifiersRemainLiteralTargets(String raw) {
    assertEquals(new QualifiedTarget(raw, raw, ""), parseQualifiedTarget(raw));
  }

  @Test
  void onlyTheLastQualifierIsInterpreted() {
    assertEquals(
        new QualifiedTarget("#room{net:one}{net:TWO}", "#room{net:one}", "two"),
        parseQualifiedTarget("#room{net:one}{net:TWO}"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void absentTargetsHaveNoHintOrMembershipKey(String target) {
    assertEquals(new QualifiedTarget("", "", ""), parseQualifiedTarget(target));
    assertEquals("", normalizeTargetHintKey(target));
    assertEquals("", normalizeMembershipKey(target, 2));
    assertEquals("", normalizeMembershipKey(target, -1));
    assertEquals(
        "target is blank",
        assertThrows(IllegalArgumentException.class, () -> sanitizeHistoryTarget(target))
            .getMessage());
  }

  @Test
  void hintsUseTheBaseTargetWhileMembershipsRemainDistinctAcrossNetworks() {
    assertEquals(
        normalizeTargetHintKey("#Room{net:ONE}"), normalizeTargetHintKey("#room{net:two}"));
    assertEquals("0|#room", normalizeMembershipKey("#Room{net:two}", 0));
    assertEquals("2|#room", normalizeMembershipKey("#Room{net:one}", 2));
    assertEquals("net:two|#room", normalizeMembershipKey("#Room{net:TWO}", -1));
    assertEquals("global|#room", normalizeMembershipKey("#Room", -1));
  }

  @ParameterizedTest
  @CsvSource({
    "'#room name',target contains spaces",
    "'#room\nname',target contains CR/LF",
    "'#room\rname',target contains CR/LF"
  })
  void historyValidationRejectsInvalidBaseTargets(String target, String reason) {
    assertEquals(
        reason,
        assertThrows(IllegalArgumentException.class, () -> sanitizeHistoryTarget(target))
            .getMessage());
  }

  @Test
  void historyValidationKeepsExistingQualifierAndTabHandling() {
    assertEquals("#room", sanitizeHistoryTarget("#room{net:two words}").baseTarget());
    assertEquals("#room\tname", sanitizeHistoryTarget("#room\tname").baseTarget());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PRIVMSG", "NOTICE", "TAGMSG", "MARKREAD", "REDACT"})
  void rewritingOnlyReplacesTheTargetAndPreservesTheRestOfTheEnvelope(String command) {
    String prefix =
        "@label=#room{net:two};+draft/reply=42  :nick!user@host   "
            + command.toLowerCase(java.util.Locale.ROOT)
            + "  ";
    String suffix = "   msgid=42 :keep #room{net:two} and  spaces";
    var route = routeOutboundRawLine(prefix + "#room{net:TWO}" + suffix);

    assertEquals(command, route.command());
    assertEquals(new QualifiedTarget("#room{net:TWO}", "#room", "two"), route.requestedTarget());
    assertEquals(0x02, route.targetTypeBitsHint());
    assertEquals(prefix + "#room" + suffix, route.rewrittenRawLine());
  }

  @ParameterizedTest
  @ValueSource(strings = {"#room", "&room", "+room", "!room"})
  void channelPrefixesSelectChannelBuffers(String target) {
    assertEquals(0x02, routeOutboundRawLine("NOTICE " + target).targetTypeBitsHint());
  }

  @Test
  void privateTargetsSelectQueryBuffersAndKeepUnqualifiedRawLines() {
    var route = routeOutboundRawLine("NOTICE Someone :hello");
    assertEquals("Someone", route.requestedTarget().baseTarget());
    assertEquals(0x04, route.targetTypeBitsHint());
    assertEquals("NOTICE Someone :hello", route.rewrittenRawLine());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "@label=42",
        ":source",
        "@label=42 :source",
        "PRIVMSG",
        "NOTICE :trailing",
        "JOIN #room{net:two}",
        "CAP REQ :message-tags"
      })
  void missingTargetsAndUnroutedCommandsKeepTheirRawLineAndUseStatusBuffers(String line) {
    var route = routeOutboundRawLine(line);
    assertNull(route.requestedTarget());
    assertEquals(0x01, route.targetTypeBitsHint());
    assertEquals(line, route.rewrittenRawLine());
  }

  @Test
  void unknownTokensAreStillParsedAndMalformedQualifiersAreNotRemoved() {
    var unknown = routeOutboundRawLine("TAGMSG #room{net:unknown}");
    assertEquals("unknown", unknown.requestedTarget().networkToken());
    assertEquals("TAGMSG #room", unknown.rewrittenRawLine());

    var malformed = routeOutboundRawLine("TAGMSG #room{net:}");
    assertEquals("#room{net:}", malformed.requestedTarget().baseTarget());
    assertEquals("TAGMSG #room{net:}", malformed.rewrittenRawLine());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\u2003"})
  void emptyRawLinesUseStatusBuffers(String line) {
    assertEquals(new OutboundRawRoute("", null, "", 0x01), routeOutboundRawLine(line));
  }

  @Test
  void rawRoutingStripsOuterUnicodeWhitespaceWithoutChangingInnerSpacing() {
    assertEquals(
        "TAGMSG  #room",
        routeOutboundRawLine("\u2003TAGMSG  #room{net:two}\u2003").rewrittenRawLine());
  }

  @Test
  void qualificationRendersOnlyNonemptyTargetsAndTokens() {
    assertEquals("#Room{net:two}", qualifyTarget(" #Room ", " two "));
    assertEquals("#Room", qualifyTarget(" #Room ", null));
    assertEquals("", qualifyTarget(null, "two"));
  }
}
