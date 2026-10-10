package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreInboundMessageCoordinatorTest {
  private static final Instant AT = Instant.parse("2026-10-08T12:00:00Z");
  private static final String TARGET = "#room{net:work}";
  private static final BufferInfoValue BUFFER = new BufferInfoValue(22, 8, 2, -1, "#room");
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<String> calls = new ArrayList<>();
  private Consumer<IrcEvent> onEmit = event -> {};
  private RuntimeException hintFailure;
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "me",
          "host",
          4242,
          (socket, write) -> fail("Inbound coordination must not write"),
          sid -> {},
          this::emit);
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              assertSame(session, observed);
              observed.networks.observe(networkId, "");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              assertSame(session, observed);
              return nick.equalsIgnoreCase(currentNick(networkId));
            }
          });
  private final QuasselCoreReadMarkerCoordinator markers =
      new QuasselCoreReadMarkerCoordinator(
          session,
          new QuasselCoreReadMarkerCoordinator.SessionPort() {
            @Override
            public String currentNick(int networkId) {
              return QuasselCoreInboundMessageCoordinatorTest.this.currentNick(networkId);
            }

            @Override
            public String historyTarget(BufferInfoValue buffer, String from) {
              return targets.targetForBuffer(session, buffer, from);
            }

            @Override
            public String qualifyTarget(String target, int networkId) {
              return targets.qualifyTarget(session, target, networkId);
            }

            @Override
            public void observeTargetNetwork(String target, int networkId) {
              observeHint(target, networkId);
            }

            @Override
            public void emit(IrcEvent.ReadMarkerObserved event) {
              QuasselCoreInboundMessageCoordinatorTest.this.emit(event);
            }
          });
  private final QuasselCoreInboundMessageCoordinator coordinator =
      coordinator(new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath()));

  private QuasselCoreInboundMessageCoordinator coordinator(QuasselIrcv3RuntimeSupport runtime) {
    return new QuasselCoreInboundMessageCoordinator(
        session,
        markers,
        targets,
        runtime,
        new QuasselCoreInboundMessageCoordinator.SessionPort() {
          @Override
          public BufferInfoValue resolveBuffer(BufferInfoValue incoming) {
            calls.add("resolve");
            if (incoming == null) return new BufferInfoValue(-1, 7, 1, -1, "");
            var merged = session.buffers.resolve(incoming);
            session.networks.observe(merged.networkId(), "");
            return merged;
          }

          @Override
          public String currentNick(int networkId) {
            calls.add("current:" + networkId);
            return QuasselCoreInboundMessageCoordinatorTest.this.currentNick(networkId);
          }

          @Override
          public boolean isSelfNick(String nick, int networkId) {
            calls.add("self:" + networkId + ":" + nick);
            return nick.equalsIgnoreCase(
                QuasselCoreInboundMessageCoordinatorTest.this.currentNick(networkId));
          }

          @Override
          public void observeTargetNetwork(String target, int networkId) {
            observeHint(target, networkId);
          }

          @Override
          public void observeNick(int networkId, Instant at, String nick) {
            assertEquals(AT, at);
            calls.add("nick:" + networkId + ":" + nick);
            session.networkCurrentNickByNetworkId.put(networkId, nick);
          }

          @Override
          public void emit(IrcEvent event) {
            QuasselCoreInboundMessageCoordinatorTest.this.emit(event);
          }
        });
  }

  private void prepare() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.networkCurrentNickByNetworkId.put(8, "me-work");
    session.buffers.merge(BUFFER);
  }

  private String currentNick(int networkId) {
    return session.networkCurrentNickByNetworkId.getOrDefault(networkId, session.currentNick.get());
  }

  private void observeHint(String target, int networkId) {
    calls.add("hint:" + networkId + ":" + target);
    if (hintFailure != null) throw hintFailure;
    session.targetNetworkHints.observe(target, networkId);
  }

  private void emit(IrcEvent event) {
    calls.add(event.getClass().getSimpleName());
    events.add(event);
    onEmit.accept(event);
  }

  private MessageValue message(int type, int flags, String content) {
    return new MessageValue(42, AT.getEpochSecond(), type, flags, BUFFER, "alice!u@h", content);
  }

  private List<String> eventTypes() {
    return events.stream().map(event -> event.getClass().getSimpleName()).toList();
  }

  @Test
  void nullMessageDoesNotConsultPortsOrPublish() {
    coordinator.handle(null);
    assertTrue(calls.isEmpty());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 0x80})
  void historyAndPendingMarkerPrecedeTagsAndLiveOrBacklogDelivery(int flags) {
    prepare();
    session.pendingReadMarkers.defer(22, 42);
    String content = "@+draft/reply=9;+draft/react=smile PRIVMSG #room :hello";
    onEmit =
        event -> {
          assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
          assertEquals(8, session.targetNetworkHints.networkIdForTarget(TARGET));
        };
    coordinator.handle(message(1, flags, content));
    assertEquals(
        List.of(
            "ReadMarkerObserved",
            "MessageReplyObserved",
            "MessageReactObserved",
            flags == 0 ? "ChannelMessage" : "ChatHistoryBatchReceived"),
        eventTypes());
    var marker = assertInstanceOf(IrcEvent.ReadMarkerObserved.class, events.getFirst());
    assertEquals(TARGET, marker.target());
    assertEquals("timestamp=2026-10-08T12:00:00.000Z", marker.marker());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    if (flags == 0) {
      assertEquals(
          new IrcEvent.ChannelMessage(
              AT,
              TARGET,
              "alice",
              "hello",
              "42",
              Map.of("draft/reply", "9", "draft/react", "smile")),
          events.getLast());
    } else {
      var batch = assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getLast());
      assertEquals(TARGET, batch.target());
      assertEquals(content, batch.entries().getFirst().text());
      assertEquals("42", batch.entries().getFirst().messageId());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 0x80})
  void tagOnlyMessagesPublishSignalsWithoutTextOrHistoryBatch(int flags) {
    prepare();
    coordinator.handle(message(1, flags, "@+typing=active TAGMSG #room"));
    assertEquals(List.of(new IrcEvent.UserTypingObserved(AT, "alice", TARGET, "active")), events);
    assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TAGMSG #room", "MARKREAD", "REDACT"})
  void consumedCommandsWithoutSignalsStillSuppressNativeAndBacklogFallback(String content) {
    prepare();
    coordinator.handle(message(1, 0x80, content));
    assertTrue(events.isEmpty());
    assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "MARKREAD #room timestamp=2026-10-08T12:00:00.000Z|ReadMarkerObserved",
        "REDACT #room 9 :cleanup|MessageRedactionObserved",
        "FAIL PRIVMSG DENIED #room :blocked|StandardReply"
      },
      delimiter = '|')
  void handledCommandsKeepProviderFilteringAndSuppressNativeTextEvenWhenBacklogged(
      String command, String expected) {
    prepare();
    coordinator.handle(message(1, 0x80, "@+draft/reply=9 " + command));
    assertEquals(List.of(expected), eventTypes());
  }

  @ParameterizedTest
  @ValueSource(ints = {7, 8, -1})
  void capAnnouncementsPrecedeNativeResponseAndKeepProviderFilteringAndNetworkFallback(
      int networkId) {
    prepare();
    coordinator.handle(
        new MessageValue(
            42,
            AT.getEpochSecond(),
            0x400,
            0,
            new BufferInfoValue(-1, networkId, 1, -1, ""),
            "server",
            "@+draft/reply=9 CAP * ACK :message-tags"));
    assertEquals(
        List.of("Ircv3CapabilityChanged", "ConnectionFeaturesUpdated", "ServerResponseLine"),
        eventTypes());
    assertTrue(session.features.hasAnyCapability("message-tags"));
    session.features.removeNetwork(networkId < 0 ? 7 : networkId);
    assertFalse(session.features.hasAnyCapability("message-tags"));
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "CAP * ACK :message-tags|Ircv3CapabilityChanged",
        "MARKREAD #room timestamp=2026-10-08T12:00:00.000Z|ReadMarkerObserved",
        "REDACT #room 9 :cleanup|MessageRedactionObserved",
        "FAIL PRIVMSG DENIED #room :blocked|StandardReply"
      },
      delimiter = '|')
  void customTagProviderSeesHistoryBeforeCommandHandlingOrCapabilityStateChanges(
      String command, String nextEvent) {
    prepare();
    var catalogs = Ircv3RuntimeCatalogs.applicationClasspath();
    var provider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "coordinator-order";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return Set.of(Ircv3InboundTagOperation.REPLY);
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
            assertEquals(8, session.targetNetworkHints.networkIdForTarget(TARGET));
            assertFalse(session.features.hasAnyCapability("message-tags"));
            assertTrue(
                events.isEmpty(), "Commands must not emit before the tag provider is called");
            return List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.REPLY, "9"));
          }
        };
    var custom =
        new QuasselIrcv3RuntimeSupport(
            catalogs.outboundCommands(),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(provider)),
            catalogs.inboundCommands(),
            catalogs.messageTags());
    coordinator(custom).handle(message(0x400, 0, "@+custom=yes " + command));
    assertEquals("MessageReplyObserved", eventTypes().getFirst());
    assertEquals(nextEvent, eventTypes().get(1));
    assertEquals(command.startsWith("CAP") ? 4 : 2, events.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"730", "731"})
  void monitorResponsesAreConsumedBeforeNativeBacklogAndHostmaskPrecedesStatus(String numeric) {
    prepare();
    coordinator.handle(message(1, 0x80, ":server " + numeric + " me :bob!u@h"));
    assertEquals(
        List.of(
            "UserHostmaskObserved",
            numeric.equals("730") ? "MonitorOnlineObserved" : "MonitorOfflineObserved"),
        eventTypes());
  }

  @ParameterizedTest
  @ValueSource(ints = {8, -1})
  void monitorSupportUpdatesBeforeUnconsumedServerResponse(int networkId) {
    prepare();
    int expectedNetwork = networkId < 0 ? 7 : networkId;
    onEmit = event -> assertEquals(150, session.features.monitorLimit(expectedNetwork));
    coordinator.handle(
        new MessageValue(
            42,
            AT.getEpochSecond(),
            0x400,
            0,
            new BufferInfoValue(-1, networkId, 1, -1, ""),
            "server",
            ":server 005 me MONITOR=150 CHANTYPES=# :are supported"));
    assertEquals(List.of("ServerResponseLine"), eventTypes());
    session.features.removeNetwork(expectedNetwork);
    assertFalse(session.features.hasMonitorState());
  }

  @ParameterizedTest
  @CsvSource({"1, PRIVMSG", "2, NOTICE", "4, ACTION"})
  void nativeBacklogTextKindsEmitOneBatchAndAdvanceSessionSequence(
      int type, ChatHistoryEntry.Kind kind) {
    prepare();
    coordinator.handle(message(type, 0x80, "first"));
    coordinator.handle(
        new MessageValue(43, AT.getEpochSecond(), type, 0x80, BUFFER, "alice!u@h", "second"));
    assertEquals(List.of("ChatHistoryBatchReceived", "ChatHistoryBatchReceived"), eventTypes());
    var first = assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getFirst());
    var second = assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getLast());
    assertEquals("quassel-backlog-42-1", first.batchId());
    assertEquals("quassel-backlog-43-2", second.batchId());
    assertEquals(kind, first.entries().getFirst().kind());
  }

  @Test
  void nonTextBacklogFlagStillUsesNativeJoinTranslationAndSuppressesDuplicateSelfJoin() {
    prepare();
    var join =
        new MessageValue(42, AT.getEpochSecond(), 0x20, 0x80, BUFFER, "me-work!u@h", "joined");
    coordinator.handle(join);
    coordinator.handle(join);
    assertEquals(List.of(new IrcEvent.JoinedChannel(AT, TARGET)), events);
    assertTrue(calls.contains("self:8:me-work"));
  }

  @Test
  void selfPartLeavesMembershipBeforePublishingAndNickObservationUsesMessageNetwork() {
    prepare();
    coordinator.handle(
        new MessageValue(41, AT.getEpochSecond(), 0x20, 0, BUFFER, "me-work!u@h", "joined"));
    events.clear();
    calls.clear();
    coordinator.handle(
        new MessageValue(42, AT.getEpochSecond(), 0x40, 0, BUFFER, "me-work!u@h", "parted (bye)"));
    coordinator.handle(
        new MessageValue(43, AT.getEpochSecond(), 8, 0, BUFFER, "me-work!u@h", "next"));
    assertEquals(List.of("LeftChannel", "UserNickChangedChannel"), eventTypes());
    assertTrue(calls.indexOf("UserNickChangedChannel") < calls.indexOf("nick:8:next"));
    assertEquals("next", session.networkCurrentNickByNetworkId.get(8));
    assertEquals("me", session.currentNick.get());
    coordinator.handle(
        new MessageValue(44, AT.getEpochSecond(), 0x20, 0, BUFFER, "next!u@h", "joined"));
    assertInstanceOf(IrcEvent.JoinedChannel.class, events.getLast());
  }

  @Test
  void partialMetadataUsesResolvedNetworkNameAndTypeBeforePreparingObservation() {
    prepare();
    var partial = new BufferInfoValue(22, -1, 0, -1, "");
    coordinator.handle(new MessageValue(42, AT.getEpochSecond(), 1, 0, partial, null, "hello"));
    assertEquals(
        new IrcEvent.ChannelMessage(AT, TARGET, "me-work", "hello", "42", Map.of()),
        events.getFirst());
    assertEquals(List.of("resolve", "current:8", "hint:8:" + TARGET, "ChannelMessage"), calls);
  }

  @Test
  void senderlessDirectTagSignalUsesRecipientsConversationOnMessageNetwork() {
    prepare();
    session.networkCurrentNickByNetworkId.put(8, "");
    coordinator.handle(
        new MessageValue(
            42,
            AT.getEpochSecond(),
            1,
            0x80,
            new BufferInfoValue(-1, 8, 4, -1, ""),
            null,
            "@+draft/react=smile PRIVMSG bob :hello"));
    assertEquals(
        List.of(new IrcEvent.MessageReactObserved(AT, "server", "bob{net:work}", "smile", "42")),
        events);
  }

  @Test
  void missingBufferUsesPrimaryStatusAndNonpositiveTimestampAndIdKeepFallbacks() {
    prepare();
    Instant before = Instant.now();
    coordinator.handle(new MessageValue(0, 0, 0x400, 0, null, null, "status"));
    Instant after = Instant.now();
    var response = assertInstanceOf(IrcEvent.ServerResponseLine.class, events.getFirst());
    assertEquals("", response.messageId());
    assertFalse(response.at().isBefore(before));
    assertFalse(response.at().isAfter(after));
    assertTrue(calls.contains("current:7"));
    assertTrue(calls.contains("hint:7:status"));
  }

  @Test
  void hintObserverFailureStopsHistoryAndLaterTranslationWithoutSwallowingError() {
    prepare();
    hintFailure = new IllegalStateException("hint failed");
    assertSame(
        hintFailure,
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.handle(message(1, 0, "@+typing=active PRIVMSG #room :hello"))));
    assertEquals(-1, session.history.exactTimestampForMsgId(TARGET, 42));
    assertTrue(events.isEmpty());
  }

  @Test
  void pendingMarkerPublicationFailureStopsTagsAndTextAfterHistoryIsRecorded() {
    prepare();
    session.pendingReadMarkers.defer(22, 42);
    var failure = new IllegalStateException("subscriber failed");
    onEmit =
        event -> {
          throw failure;
        };
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.handle(message(1, 0, "@+typing=active PRIVMSG #room :hello"))));
    assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
    assertEquals(List.of("ReadMarkerObserved"), eventTypes());
  }
}
