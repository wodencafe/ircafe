package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayMessageTranslator.Observation;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayMessageTranslator.SessionPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreDisplayMessageTranslatorTest {
  private static final Instant AT = Instant.parse("2026-10-08T12:00:00Z");
  private static final String TARGET = "#room{net:second}";
  private static final Map<String, String> TAGS = Map.of("label", "test", "msgid", "native");
  private final List<Object> trace = new ArrayList<>();
  private final SessionPort session =
      new SessionPort() {
        @Override
        public boolean isSelfNick(String nick) {
          trace.add("self:" + nick);
          return "self".equalsIgnoreCase(nick);
        }

        @Override
        public String currentNick() {
          trace.add("current-nick");
          return "self";
        }

        @Override
        public String queryTarget() {
          trace.add("query-target");
          return "alice{net:second}";
        }

        @Override
        public void observeJoin(Instant at, String target) {
          assertEquals(AT, at);
          trace.add("join:" + target);
        }

        @Override
        public void leave(String target) {
          trace.add("leave:" + target);
        }

        @Override
        public void observeNick(Instant at, String nick) {
          assertEquals(AT, at);
          trace.add("nick:" + nick);
        }
      };

  @Test
  void selfJoinIsDelegatedWithoutPublishingAUserJoinOrHostmask() {
    translate(observation(0x20, 2, TARGET, "SELF", "self!u@h", "joined"));
    assertEquals(List.of("self:SELF", "join:" + TARGET), trace);
  }

  @Test
  void userJoinPublishesHostmaskBeforeJoinAndKeepsQualifiedTarget() {
    translate(observation(0x20, 2, TARGET, "alice", " alice!u@h ", "joined"));
    assertEquals(
        List.of(
            "self:alice",
            new IrcEvent.UserHostmaskObserved(AT, TARGET, "alice", "alice!u@h"),
            new IrcEvent.UserJoinedChannel(AT, TARGET, "alice")),
        trace);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "alice", "alice!*@*"})
  void uselessHostmasksDoNotAddAnObservation(String hostmask) {
    translate(observation(0x20, 2, TARGET, "alice", hostmask, "joined"));
    assertEquals(List.of("self:alice", new IrcEvent.UserJoinedChannel(AT, TARGET, "alice")), trace);
  }

  @Test
  void emptyDisplayNickDoesNotPublishHostmask() {
    translate(observation(0x80, 2, TARGET, "", "alice!u@h", "(bye)"));
    assertEquals(List.of(new IrcEvent.UserQuitChannel(AT, TARGET, "", "bye")), trace);
  }

  @Test
  void selfPartLeavesMembershipBeforePublishingLeftChannel() {
    translate(observation(0x40, 2, TARGET, "self", "self!u@h", "parted (bye)"));
    assertEquals(
        List.of("self:self", "leave:" + TARGET, new IrcEvent.LeftChannel(AT, TARGET, "bye")),
        trace);
  }

  @Test
  void userPartAndQuitPublishHostmaskBeforeTheirChannelEvents() {
    translate(observation(0x40, 2, TARGET, "alice", "alice!u@h", "(bye)"));
    translate(observation(0x80, 2, TARGET, "alice", "alice!u@h", "Quit: timeout"));
    var hostmask = new IrcEvent.UserHostmaskObserved(AT, TARGET, "alice", "alice!u@h");
    assertEquals(
        List.of(
            "self:alice",
            hostmask,
            new IrcEvent.UserPartedChannel(AT, TARGET, "alice", "bye"),
            hostmask,
            new IrcEvent.UserQuitChannel(AT, TARGET, "alice", "Quit: timeout")),
        trace);
  }

  @Test
  void nickChannelEventPrecedesSelfNickObservation() {
    translate(observation(8, 2, TARGET, "self", "self!u@h", "self is now known as next"));
    assertEquals(
        List.of(
            new IrcEvent.UserNickChangedChannel(AT, TARGET, "self", "next"),
            "self:self",
            "nick:next"),
        trace);
  }

  @Test
  void nativeSelfNickAcknowledgementDoesNotPublishANoopChannelRename() {
    // Core sends the new nick in both fields after its Network sync already updated myNick.
    translate(observation(8, 2, TARGET, "self", "self", "self"));
    assertEquals(List.of("self:self", "nick:self"), trace);
  }

  @Test
  void caseOnlyNickChangeStillPublishesTheChannelRename() {
    translate(observation(8, 2, TARGET, "SELF", "SELF!u@h", "self"));
    assertEquals(
        List.of(
            new IrcEvent.UserNickChangedChannel(AT, TARGET, "SELF", "self"),
            "self:SELF",
            "nick:self"),
        trace);
  }

  @Test
  void selfNickWithoutTargetStillUpdatesSessionWhileOtherNickDoesNot() {
    translate(observation(8, 1, "", "self", "self!u@h", "next"));
    translate(observation(8, 1, "", "alice", "alice!u@h", "other"));
    assertEquals(List.of("self:self", "nick:next", "self:alice"), trace);
  }

  @Test
  void selfKickLeavesBeforePublishingAndUserKickKeepsMembership() {
    translate(observation(0x100, 2, TARGET, "ops", "ops!u@h", "kicked self (gone)"));
    translate(observation(0x100, 2, TARGET, "ops", "ops!u@h", "kicked alice (bye)"));
    assertEquals(
        List.of(
            "self:self",
            "leave:" + TARGET,
            new IrcEvent.KickedFromChannel(AT, TARGET, "ops", "gone"),
            "self:alice",
            new IrcEvent.UserKickedFromChannel(AT, TARGET, "alice", "ops", "bye")),
        trace);
  }

  @Test
  void topicAndModeUsePreparedPayloadWithNativeModeProvenance() {
    translate(observation(0x4000, 2, TARGET, "alice", "alice!u@h", "changed topic to 'New topic'"));
    translate(observation(0x10, 2, TARGET, "ops", "ops!u@h", "set mode +o alice"));
    assertEquals(new IrcEvent.ChannelTopicUpdated(AT, TARGET, "New topic"), trace.getFirst());
    var mode = assertInstanceOf(IrcEvent.ChannelModeObserved.class, trace.getLast());
    assertEquals(AT, mode.at());
    assertEquals(TARGET, mode.channel());
    assertEquals("+o alice", mode.details());
    assertEquals(IrcEvent.ChannelModeKind.DELTA, mode.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.QUASSEL_DISPLAY_MESSAGE, mode.provenance());
  }

  @Test
  void invitesPreferChannelFromPayloadAndReadCurrentNickOnlyWhenPublishing() {
    translate(observation(0x20000, 2, TARGET, "ops", "ops!u@h", "invited to #other"));
    translate(observation(0x20000, 2, TARGET, "ops", "ops!u@h", "invited"));
    assertEquals(
        List.of(
            "current-nick",
            new IrcEvent.InvitedToChannel(AT, "#other", "ops", "self", "", false),
            "current-nick",
            new IrcEvent.InvitedToChannel(AT, TARGET, "ops", "self", "", false)),
        trace);
  }

  @Test
  void noticeResolvesQueryFallbackOnlyForAnEmptyQueryTarget() {
    translate(observation(2, 4, "", "alice", "alice!u@h", "prepared"));
    translate(observation(2, 4, TARGET, "alice", "alice!u@h", "prepared"));
    translate(observation(2, 2, "", "alice", "alice!u@h", "prepared"));
    assertEquals(
        List.of(
            "query-target",
            new IrcEvent.Notice(AT, "alice", "alice{net:second}", "prepared", "42", TAGS),
            new IrcEvent.Notice(AT, "alice", TARGET, "prepared", "42", TAGS),
            new IrcEvent.Notice(AT, "alice", "", "prepared", "42", TAGS)),
        trace);
  }

  @Test
  void plainAndActionChannelEventsKeepTimeIdsAndTagsFromPreparedObservation() {
    translate(observation(1, 2, TARGET, "alice", "alice!u@h", "prepared"));
    translate(observation(4, 2, TARGET, "alice", "alice!u@h", "prepared"));
    assertEquals(
        List.of(
            new IrcEvent.ChannelMessage(AT, TARGET, "alice", "prepared", "42", TAGS),
            new IrcEvent.ChannelAction(AT, TARGET, "alice", "prepared", "42", TAGS)),
        trace);
  }

  @ParameterizedTest
  @CsvSource({"4,alice", "1,status", "0,unknown", "2,''"})
  void nonChannelBuffersAndEmptyChannelTargetsPublishPrivateText(int bufferType, String target) {
    translate(observation(1, bufferType, target, "alice", "alice!u@h", "prepared"));
    translate(observation(4, bufferType, target, "alice", "alice!u@h", "prepared"));
    assertEquals(
        List.of(
            new IrcEvent.PrivateMessage(AT, "alice", "prepared", "42", TAGS),
            new IrcEvent.PrivateAction(AT, "alice", "prepared", "42", TAGS)),
        trace);
  }

  @ParameterizedTest
  @ValueSource(ints = {0x20, 0x40, 0x80})
  void targetlessJoinPartAndQuitAreTerminalEvenWithPlainBit(int bits) {
    translate(observation(bits | 1, 2, "", "alice", "alice!u@h", "prepared"));
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0x4000, 0x10, 0x100, 0x20000})
  void targetlessTopicModeKickAndInviteFallThroughToPlainBit(int bits) {
    translate(observation(bits | 1, 2, "", "alice", "alice!u@h", "prepared"));
    assertEquals(List.of(new IrcEvent.PrivateMessage(AT, "alice", "prepared", "42", TAGS)), trace);
  }

  @Test
  void kickWithNoVictimFallsThroughWithoutChangingMembership() {
    translate(observation(0x100 | 2, 2, TARGET, "ops", "ops!u@h", ""));
    assertEquals(List.of(new IrcEvent.Notice(AT, "ops", TARGET, "", "42", TAGS)), trace);
  }

  @ParameterizedTest
  @CsvSource({
    "96,UserJoinedChannel", "192,UserPartedChannel", "136,UserQuitChannel",
    "16392,UserNickChangedChannel", "16400,ChannelTopicUpdated", "272,ChannelModeObserved",
    "131328,UserKickedFromChannel", "131074,InvitedToChannel", "6,Notice",
    "5,ChannelAction", "4097,ChannelMessage", "5120,Error"
  })
  void combinedNativeTypeFlagsKeepOriginalPrecedence(int bits, String eventType) {
    translate(observation(bits, 2, TARGET, "ops", "ops!u@h", "alice #invite"));
    assertEquals(
        List.of(eventType),
        trace.stream()
            .filter(
                value ->
                    value instanceof IrcEvent && !(value instanceof IrcEvent.UserHostmaskObserved))
            .map(value -> value.getClass().getSimpleName())
            .toList());
  }

  @Test
  void numericResponseUsesRawEnvelopeWhileRetainingPreparedTagsAndId() {
    var base = observation(0x400, 1, "status", "server", "", "display text");
    String raw = "@label=test :irc.test 401 self Ghost :No such nick";
    translate(
        new Observation(
            AT,
            base.bufferInfo(),
            "status",
            "server",
            "",
            "display text",
            "42",
            TAGS,
            new MessageValue(42, AT.getEpochSecond(), 0x400, 0, base.bufferInfo(), "server", raw)));
    var response = assertInstanceOf(IrcEvent.ServerResponseLine.class, trace.getFirst());
    assertEquals(401, response.code());
    assertEquals("No such nick", response.message());
    assertEquals(raw, response.rawLine());
    assertEquals("42", response.messageId());
    assertEquals(TAGS, response.ircv3Tags());
  }

  @ParameterizedTest
  @CsvSource({
    "1024,(server)",
    "2048,(server)",
    "4096,(quassel message type 4096)",
    "0,(quassel message type 0)"
  })
  void blankRawUnknownTypesKeepTargetPrefixAndFallbackText(int bits, String fallback) {
    var base = observation(bits, 2, TARGET, "server", "", " ");
    translate(
        new Observation(
            AT,
            base.bufferInfo(),
            TARGET,
            "server",
            "",
            " ",
            "42",
            TAGS,
            new MessageValue(42, AT.getEpochSecond(), bits, 0, base.bufferInfo(), "server", null)));
    IrcEvent event = assertInstanceOf(IrcEvent.class, trace.getFirst());
    String expected = "[" + TARGET + "] " + fallback;
    if (event instanceof IrcEvent.Error error) assertEquals(expected, error.message());
    else
      assertEquals(expected, assertInstanceOf(IrcEvent.ServerResponseLine.class, event).message());
  }

  private void translate(Observation observation) {
    QuasselCoreDisplayMessageTranslator.translate(observation, session, trace::add);
  }

  private static Observation observation(
      int bits, int bufferType, String target, String from, String hostmask, String payload) {
    var buffer = new BufferInfoValue(10, 2, bufferType, -1, "#room");
    var message = new MessageValue(42, AT.getEpochSecond(), bits, 0, buffer, hostmask, "wire text");
    return new Observation(AT, buffer, target, from, hostmask, payload, "42", TAGS, message);
  }
}
