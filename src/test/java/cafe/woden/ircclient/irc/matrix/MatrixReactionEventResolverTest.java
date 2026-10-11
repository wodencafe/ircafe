package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MatrixReactionEventResolverTest {
  private static final String SID = "matrix";
  private static final String ROOM = "!room:example.org";
  private static final String USER = "@me:example.org";
  private static final String TOKEN = "access-token";
  private static final String TARGET = "$target";
  private static final String REACTION = ":+1:";
  private static final long TARGET_TS = 1_710_000_000_000L;
  private static final URI ENDPOINT = URI.create("https://example.org/_matrix/client/v3/sync");

  private final IrcProperties.Server server = mock(IrcProperties.Server.class);
  private final MatrixRoomHistoryClient historyClient = mock(MatrixRoomHistoryClient.class);
  private final MatrixSyncClient syncClient = mock(MatrixSyncClient.class);
  private final MatrixHistoryCursorCoordinator history = mock(MatrixHistoryCursorCoordinator.class);
  private final MatrixHistoryCursorCoordinator.SessionView historyView =
      mock(MatrixHistoryCursorCoordinator.SessionView.class);
  private final List<MatrixSession> adaptedSessions = new ArrayList<>();
  private final MatrixSession session = new MatrixSession(USER, TOKEN);
  private final MatrixReactionEventResolver resolver =
      new MatrixReactionEventResolver(
          historyClient,
          syncClient,
          history,
          observed -> {
            adaptedSessions.add(observed);
            return historyView;
          });

  @BeforeEach
  void setHistoryAnchor() {
    session.sinceToken.set("since");
    when(history.resolveHistoryFromToken(eq(SID), eq(server), eq(historyView), eq(ROOM), anyLong()))
        .thenReturn("anchor");
  }

  @Test
  void cachedLookupNormalizesInputsAndSelectsLatestMatchingReactionWithoutRemoteWork() {
    session.rememberReactionEvent(ROOM, "$old", TARGET, REACTION, USER);
    session.rememberReactionEvent(ROOM, "$latest", TARGET, REACTION, USER);
    session.rememberReactionEvent(ROOM, "$other-user", TARGET, REACTION, "@other:example.org");
    session.rememberReactionEvent(ROOM, "$other-target", "$other", REACTION, USER);
    session.rememberReactionEvent(ROOM, "$other-key", TARGET, ":smile:", USER);
    session.rememberReactionEvent("!other:example.org", "$other-room", TARGET, REACTION, USER);

    assertEquals(
        "$latest",
        resolver.resolveForUnreact(
            " matrix ", server, session, " " + ROOM + " ", " $target ", " :+1: ", " " + USER));
    assertEquals("$latest", session.findReactionEventId(ROOM, TARGET, REACTION, USER));
    assertTrue(adaptedSessions.isEmpty());
    verifyNoInteractions(syncClient, historyClient, history, historyView);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5})
  void incompleteLookupDoesNotAdaptSessionOrFetch(int missing) {
    assertEquals(
        "",
        resolver.resolveForUnreact(
            missing == 0 ? " " : SID,
            server,
            missing == 1 ? null : session,
            missing == 2 ? null : ROOM,
            missing == 3 ? "" : TARGET,
            missing == 4 ? " " : REACTION,
            missing == 5 ? null : USER));
    assertTrue(adaptedSessions.isEmpty());
    verifyNoInteractions(syncClient, historyClient, history, historyView);
  }

  @Test
  void syncRefreshFiltersRoomAndAppliesRedactionsBeforeRememberingReactions() {
    when(syncClient.sync(SID, server, TOKEN, "since", 0))
        .thenReturn(
            sync(
                List.of(
                    syncReaction(ROOM, "$redacted", USER),
                    syncReaction("!other:example.org", "$other-room", USER),
                    syncReaction(ROOM, "$other-user", "@other:example.org"),
                    syncReaction(ROOM, "$active", USER)),
                List.of(syncRedaction(ROOM, "$redacted"))));

    assertEquals("$active", resolve());
    assertEquals("since", session.sinceToken.get());
    assertNull(session.consumeReactionEvent(ROOM, "$redacted"));
    assertEquals("", session.findReactionEventId("!other:example.org", TARGET, REACTION, USER));
    assertTrue(adaptedSessions.isEmpty());
    verifyNoInteractions(historyClient, history, historyView);
  }

  @Test
  void syncRedactionInOtherRoomDoesNotRemoveTheRequestedRoomsReaction() {
    when(syncClient.sync(SID, server, TOKEN, "since", 0))
        .thenReturn(
            sync(
                List.of(syncReaction(ROOM, "$active", USER)),
                List.of(syncRedaction("!other:example.org", "$active"))));

    assertEquals("$active", resolve());
    verifyNoInteractions(historyClient, history);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingOrFailedSyncStillFallsBackToHistory(boolean failed) {
    when(syncClient.sync(SID, server, TOKEN, "since", 0))
        .thenReturn(failed ? MatrixSyncClient.SyncResult.failed(ENDPOINT, "offline") : null);
    when(before("anchor"))
        .thenReturn(page("", List.of(), List.of(historyReaction("$found", USER)), List.of()));

    assertEquals("$found", resolve());
    assertEquals(List.of(session), adaptedSessions);
    assertEquals("since", session.sinceToken.get());
    verify(historyClient, never()).fetchMessagesAfter(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void backwardScanCarriesRedactionsAcrossPagesAndRemembersMessageTimestamps() {
    when(before("anchor"))
        .thenReturn(page("older", List.of(), List.of(), List.of(historyRedaction("$removed"))));
    when(before("older"))
        .thenReturn(
            page(
                "",
                List.of(message(TARGET, TARGET_TS)),
                List.of(historyReaction("$removed", USER), historyReaction("$active", USER)),
                List.of()));

    assertEquals("$active", resolve());
    assertNull(session.consumeReactionEvent(ROOM, "$removed"));
    assertEquals(TARGET_TS, session.roomEventTimestampMs(ROOM, TARGET));
    var order = inOrder(syncClient, history, historyClient);
    order.verify(syncClient).sync(SID, server, TOKEN, "since", 0);
    order
        .verify(history)
        .resolveHistoryFromToken(eq(SID), eq(server), eq(historyView), eq(ROOM), anyLong());
    order.verify(historyClient).fetchMessagesBefore(SID, server, TOKEN, ROOM, "anchor", 200);
    order.verify(historyClient).fetchMessagesBefore(SID, server, TOKEN, ROOM, "older", 200);
    verify(history, never())
        .resolveHistoryMessageIdInstant(any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void backwardScanStopsOnEmptyOrRepeatedCursorOrMissingOrFailedPage(int outcome) {
    when(before("anchor"))
        .thenReturn(
            switch (outcome) {
              case 0 -> page("", List.of(), List.of(), List.of());
              case 1 -> page("anchor", List.of(), List.of(), List.of());
              case 2 -> null;
              default -> MatrixRoomHistoryClient.HistoryResult.failed(ENDPOINT, "offline");
            });

    assertEquals("", resolve());
    verify(historyClient).fetchMessagesBefore(SID, server, TOKEN, ROOM, "anchor", 200);
    verifyNoMoreInteractions(historyClient);
  }

  @Test
  void backwardReactionScanIsLimitedToTenPagesOfTwoHundredEvents() {
    when(historyClient.fetchMessagesBefore(
            eq(SID), eq(server), eq(TOKEN), eq(ROOM), anyString(), eq(200)))
        .thenAnswer(call -> page(call.getArgument(4) + "-older", List.of(), List.of(), List.of()));

    assertEquals("", resolve());
    verify(historyClient, times(10))
        .fetchMessagesBefore(eq(SID), eq(server), eq(TOKEN), eq(ROOM), anyString(), eq(200));
    verifyNoMoreInteractions(historyClient);
  }

  @Test
  void unresolvedHistoryAnchorSkipsPaginationAndTimestampLookup() {
    when(history.resolveHistoryFromToken(eq(SID), eq(server), eq(historyView), eq(ROOM), anyLong()))
        .thenReturn("");

    assertEquals("", resolve());
    verifyNoInteractions(historyClient);
    verify(history, never())
        .resolveHistoryMessageIdInstant(any(), any(), any(), any(), any(), any());
    verify(history, never())
        .resolveForwardCursorForTimestamp(any(), any(), any(), any(), anyLong());
  }

  @Test
  void forwardFallbackUsesTargetTimestampResolvedFromHistory() {
    when(before("anchor")).thenReturn(page("", List.of(), List.of(), List.of()));
    when(history.resolveHistoryMessageIdInstant(SID, server, historyView, ROOM, TARGET, "unreact"))
        .thenReturn(Instant.ofEpochMilli(TARGET_TS));
    when(history.resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS))
        .thenReturn("near-target");
    when(after("near-target"))
        .thenReturn(page("", List.of(), List.of(historyReaction("$forward", USER)), List.of()));

    assertEquals("$forward", resolve());
    verify(history)
        .resolveHistoryMessageIdInstant(SID, server, historyView, ROOM, TARGET, "unreact");
    verify(history).resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS);
  }

  @Test
  void forwardScanUsesCachedTimestampAndCarriesRedactionsAcrossPages() {
    session.rememberRoomEvent(ROOM, TARGET, TARGET_TS);
    when(before("anchor")).thenReturn(page("", List.of(), List.of(), List.of()));
    when(history.resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS))
        .thenReturn("near-target");
    when(after("near-target"))
        .thenReturn(page("next", List.of(), List.of(), List.of(historyRedaction("$removed"))));
    when(after("next"))
        .thenReturn(
            page(
                "",
                List.of(),
                List.of(historyReaction("$removed", USER), historyReaction("$active", USER)),
                List.of()));

    assertEquals("$active", resolve());
    assertNull(session.consumeReactionEvent(ROOM, "$removed"));
    verify(history, never())
        .resolveHistoryMessageIdInstant(any(), any(), any(), any(), any(), any());
  }

  @Test
  void forwardReactionScanIsLimitedToTenPagesOfTwoHundredEvents() {
    session.rememberRoomEvent(ROOM, TARGET, TARGET_TS);
    when(before("anchor")).thenReturn(page("", List.of(), List.of(), List.of()));
    when(history.resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS))
        .thenReturn("near-target");
    when(historyClient.fetchMessagesAfter(
            eq(SID), eq(server), eq(TOKEN), eq(ROOM), anyString(), eq(200)))
        .thenAnswer(call -> page(call.getArgument(4) + "-newer", List.of(), List.of(), List.of()));

    assertEquals("", resolve());
    verify(historyClient, times(10))
        .fetchMessagesAfter(eq(SID), eq(server), eq(TOKEN), eq(ROOM), anyString(), eq(200));
    verify(historyClient).fetchMessagesBefore(SID, server, TOKEN, ROOM, "anchor", 200);
    verifyNoMoreInteractions(historyClient);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void timestampOrForwardCursorResolutionFailureLeavesReactionUnknown(int outcome) {
    when(before("anchor")).thenReturn(page("", List.of(), List.of(), List.of()));
    if (outcome == 0) {
      when(history.resolveHistoryMessageIdInstant(
              SID, server, historyView, ROOM, TARGET, "unreact"))
          .thenThrow(new IllegalStateException("timestamp missing"));
    } else if (outcome == 1) {
      when(history.resolveHistoryMessageIdInstant(
              SID, server, historyView, ROOM, TARGET, "unreact"))
          .thenReturn(Instant.EPOCH);
    } else {
      session.rememberRoomEvent(ROOM, TARGET, TARGET_TS);
      if (outcome == 2) {
        when(history.resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS))
            .thenThrow(new IllegalStateException("cursor missing"));
      } else {
        when(history.resolveForwardCursorForTimestamp(SID, server, historyView, ROOM, TARGET_TS))
            .thenReturn("");
      }
    }

    assertEquals("", resolve());
    verify(historyClient, never()).fetchMessagesAfter(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void malformedSyncEntriesAndNullHistoryListsAreIgnored() {
    when(syncClient.sync(SID, server, TOKEN, "since", 0))
        .thenReturn(
            new MatrixSyncClient.SyncResult(
                true,
                ENDPOINT,
                "next",
                List.of(),
                List.of(),
                List.of(),
                Arrays.asList(
                    null, syncReaction(ROOM, " ", USER), syncReaction(ROOM, "$invalid", "")),
                Arrays.asList(null, syncRedaction(ROOM, " ")),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                ""));
    when(before("anchor"))
        .thenReturn(
            new MatrixRoomHistoryClient.HistoryResult(true, ENDPOINT, "", null, null, null, ""));

    assertEquals("", resolve());
    assertEquals("", session.findReactionEventId(ROOM, TARGET, REACTION, USER));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void transportExceptionsPropagateToCaller(boolean historyFails) {
    var failure = new IllegalStateException("transport failed");
    if (historyFails) {
      when(before("anchor")).thenThrow(failure);
    } else {
      when(syncClient.sync(SID, server, TOKEN, "since", 0)).thenThrow(failure);
    }

    assertSame(failure, assertThrows(IllegalStateException.class, this::resolve));
  }

  @Test
  void oneResolverKeepsReactionStateInTheSuppliedSession() {
    var other = new MatrixSession(USER, "other-token");
    session.rememberReactionEvent(ROOM, "$mine", TARGET, REACTION, USER);
    other.rememberReactionEvent(ROOM, "$other", TARGET, REACTION, USER);

    assertEquals("$mine", resolve());
    assertEquals(
        "$other", resolver.resolveForUnreact(SID, server, other, ROOM, TARGET, REACTION, USER));
    assertTrue(adaptedSessions.isEmpty());
    verifyNoInteractions(syncClient, historyClient, history);
  }

  private String resolve() {
    return resolver.resolveForUnreact(SID, server, session, ROOM, TARGET, REACTION, USER);
  }

  private MatrixRoomHistoryClient.HistoryResult before(String cursor) {
    return historyClient.fetchMessagesBefore(SID, server, TOKEN, ROOM, cursor, 200);
  }

  private MatrixRoomHistoryClient.HistoryResult after(String cursor) {
    return historyClient.fetchMessagesAfter(SID, server, TOKEN, ROOM, cursor, 200);
  }

  private static MatrixSyncClient.SyncResult sync(
      List<MatrixSyncClient.RoomReactionEvent> reactions,
      List<MatrixSyncClient.RoomRedactionEvent> redactions) {
    return MatrixSyncClient.SyncResult.success(
        ENDPOINT,
        "next",
        List.of(),
        List.of(),
        List.of(),
        reactions,
        redactions,
        Map.of(),
        List.of(),
        List.of());
  }

  private static MatrixSyncClient.RoomReactionEvent syncReaction(
      String room, String id, String user) {
    return new MatrixSyncClient.RoomReactionEvent(room, user, id, TARGET, REACTION, TARGET_TS + 1);
  }

  private static MatrixSyncClient.RoomRedactionEvent syncRedaction(String room, String reactionId) {
    return new MatrixSyncClient.RoomRedactionEvent(
        room, USER, "$redact", reactionId, "", TARGET_TS + 2);
  }

  private static MatrixRoomHistoryClient.HistoryResult page(
      String cursor,
      List<MatrixRoomHistoryClient.RoomHistoryEvent> messages,
      List<MatrixRoomHistoryClient.RoomReactionEvent> reactions,
      List<MatrixRoomHistoryClient.RoomRedactionEvent> redactions) {
    return MatrixRoomHistoryClient.HistoryResult.success(
        ENDPOINT, cursor, messages, reactions, redactions);
  }

  private static MatrixRoomHistoryClient.RoomReactionEvent historyReaction(String id, String user) {
    return new MatrixRoomHistoryClient.RoomReactionEvent(user, id, TARGET, REACTION, TARGET_TS + 1);
  }

  private static MatrixRoomHistoryClient.RoomRedactionEvent historyRedaction(String reactionId) {
    return new MatrixRoomHistoryClient.RoomRedactionEvent(
        USER, "$redact", reactionId, "", TARGET_TS + 2);
  }

  private static MatrixRoomHistoryClient.RoomHistoryEvent message(String id, long timestamp) {
    return new MatrixRoomHistoryClient.RoomHistoryEvent(USER, id, "m.text", "message", timestamp);
  }
}
