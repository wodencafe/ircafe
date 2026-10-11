package cafe.woden.ircclient.irc.matrix;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class MatrixHistoryRequestCoordinatorTest {
  private static final String SID = "matrix";
  private static final String TARGET = "#room";
  private static final String ROOM = "!room:example.org";
  private static final Instant CENTER = Instant.parse("2026-10-11T00:00:00Z");
  private static final String TIMESTAMP = "timestamp=2026-10-11T00:00:00Z";
  private final ServerCatalog catalog = mock(ServerCatalog.class);
  private final IrcProperties.Server server = mock(IrcProperties.Server.class);
  private final MatrixRoomTargetResolver targets = mock(MatrixRoomTargetResolver.class);
  private final MatrixHistoryCursorCoordinator cursors = mock(MatrixHistoryCursorCoordinator.class);
  private final MatrixHistoryCursorCoordinator.SessionView view =
      mock(MatrixHistoryCursorCoordinator.SessionView.class);
  private final MatrixSession session = new MatrixSession("@me:example.org", "token");
  private final Map<String, MatrixSession> sessions = new ConcurrentHashMap<>();
  private final List<Batch> batches = new ArrayList<>();
  private RuntimeException publicationFailure;
  private final MatrixHistoryRequestCoordinator requests =
      new MatrixHistoryRequestCoordinator(
          catalog,
          targets,
          cursors,
          sessions::get,
          ignored -> "offline",
          observed -> {
            assertSame(session, observed);
            return view;
          },
          (sid, target, entries) -> {
            if (publicationFailure != null) throw publicationFailure;
            batches.add(new Batch(sid, target, entries));
          });

  @BeforeEach
  void establishSessionAndHistory() {
    sessions.put(SID, session);
    when(catalog.require(SID)).thenReturn(server);
    when(targets.resolveHistoryRoomId(SID, TARGET, server, session)).thenReturn(ROOM);
    when(cursors.normalizeHistoryLimit(anyInt())).thenCallRealMethod();
    when(cursors.fetchHistoryEntriesBefore(
            eq(SID), eq(server), eq(view), eq(TARGET), eq(ROOM), anyLong(), anyInt()))
        .thenReturn(List.of());
    when(cursors.resolveForwardCursorForTimestamp(
            eq(SID), eq(server), eq(view), eq(ROOM), anyLong()))
        .thenReturn("cursor");
    when(cursors.fetchHistoryEntriesBackwardFromCursor(
            eq(SID),
            eq(server),
            eq(view),
            eq(TARGET),
            eq(ROOM),
            eq("cursor"),
            any(),
            any(),
            anyInt()))
        .thenReturn(List.of());
    when(cursors.fetchHistoryEntriesForwardFromCursor(
            eq(SID),
            eq(server),
            eq(view),
            eq(TARGET),
            eq(ROOM),
            eq("cursor"),
            any(),
            any(),
            anyInt()))
        .thenReturn(List.of());
  }

  @ParameterizedTest
  @CsvSource({"-1, 50", "1, 1", "500, 200"})
  void beforeNormalizesTargetAndLimitAndFetchesOffCallerThreadBeforePublication(
      int limit, int expectedLimit) {
    Thread caller = Thread.currentThread();
    List<ChatHistoryEntry> entries = List.of(entry("$old", -1, "older"));
    when(cursors.fetchHistoryEntriesBefore(
            eq(SID),
            eq(server),
            eq(view),
            eq(TARGET),
            eq(ROOM),
            eq(CENTER.toEpochMilli()),
            anyInt()))
        .thenAnswer(
            ignored -> {
              assertNotSame(caller, Thread.currentThread());
              assertTrue(batches.isEmpty());
              return entries;
            });
    Completable request = requests.requestBefore(" matrix ", " #room ", CENTER, limit);
    verifyNoInteractions(catalog, targets, cursors);
    request.blockingAwait();
    assertEquals(List.of(new Batch(SID, TARGET, entries)), batches);
    verify(cursors)
        .fetchHistoryEntriesBefore(
            SID, server, view, TARGET, ROOM, CENTER.toEpochMilli(), expectedLimit);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void timestampSelectorForBeforeAndLatestUsesExistingBeforeFetch(boolean latest) {
    String selector = " TIMESTAMP=2026-10-11T00:00:00Z ";
    Completable request =
        latest
            ? requests.requestLatest(SID, TARGET, selector, 3)
            : requests.requestBefore(SID, TARGET, selector, 3);
    request.blockingAwait();
    verify(cursors)
        .fetchHistoryEntriesBefore(SID, server, view, TARGET, ROOM, CENTER.toEpochMilli(), 3);
    assertEquals(1, batches.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*"})
  void latestDefaultSelectorUsesCurrentTime(String selector) {
    long earliest = System.currentTimeMillis();
    requests.requestLatest(SID, TARGET, selector, 3).blockingAwait();
    var boundary = ArgumentCaptor.forClass(Long.class);
    verify(cursors)
        .fetchHistoryEntriesBefore(
            eq(SID), eq(server), eq(view), eq(TARGET), eq(ROOM), boundary.capture(), eq(3));
    assertTrue(boundary.getValue() >= earliest);
    assertTrue(boundary.getValue() <= System.currentTimeMillis());
  }

  @Test
  void nullBeforeTimestampUsesCurrentTime() {
    long earliest = System.currentTimeMillis();
    requests.requestBefore(SID, TARGET, (Instant) null, 3).blockingAwait();
    var boundary = ArgumentCaptor.forClass(Long.class);
    verify(cursors)
        .fetchHistoryEntriesBefore(
            eq(SID), eq(server), eq(view), eq(TARGET), eq(ROOM), boundary.capture(), eq(3));
    assertTrue(boundary.getValue() >= earliest);
    assertTrue(boundary.getValue() <= System.currentTimeMillis());
  }

  @ParameterizedTest
  @ValueSource(strings = {"timestamp= ", "timestamp=bad", "msgid= "})
  void malformedRecognizedSelectorFailsImmediatelyForBeforeAndLatest(String selector) {
    for (boolean latest : List.of(false, true)) {
      var error =
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                if (latest) requests.requestLatest(SID, TARGET, selector, 3);
                else requests.requestBefore(SID, TARGET, selector, 3);
              });
      if (selector.equals("timestamp=bad")) {
        assertEquals("timestamp selector is invalid: bad", error.getMessage());
        assertInstanceOf(DateTimeParseException.class, error.getCause());
      } else {
        assertEquals(
            selector.startsWith("timestamp")
                ? "timestamp selector is blank"
                : "msgid selector is blank",
            error.getMessage());
      }
    }
    verifyNoInteractions(catalog, targets, cursors);
  }

  @Test
  void unsupportedBeforeAndLatestSelectorsReturnErrorsWithoutSessionWork() {
    requests
        .requestBefore(SID, TARGET, "*", 3)
        .test()
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && error.getMessage().equals("selector must be msgid=... or timestamp=..."));
    requests
        .requestLatest(SID, TARGET, "unsupported=value", 3)
        .test()
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && error
                        .getMessage()
                        .equals("selector must be *, msgid=..., or timestamp=..."));
    verifyNoInteractions(catalog, targets, cursors);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void messageIdResolutionIsDeferredAndUsesTheModesOperationName(boolean latest) {
    String operation = latest ? "chat-history-latest" : "chat-history-before";
    when(cursors.resolveHistoryMessageIdInstant(SID, server, view, ROOM, "$event", operation))
        .thenReturn(CENTER);
    Completable request =
        latest
            ? requests.requestLatest(SID, TARGET, " MSGID=$event ", 3)
            : requests.requestBefore(SID, TARGET, " MSGID=$event ", 3);
    verifyNoInteractions(catalog, targets, cursors);
    request.blockingAwait();
    verify(cursors).resolveHistoryMessageIdInstant(SID, server, view, ROOM, "$event", operation);
    verify(cursors)
        .fetchHistoryEntriesBefore(SID, server, view, TARGET, ROOM, CENTER.toEpochMilli(), 3);
    assertEquals(1, batches.size());
  }

  @Test
  void messageIdResolutionFailurePreventsFetchAndPublication() {
    var failure = new IllegalStateException("message unknown");
    when(cursors.resolveHistoryMessageIdInstant(
            SID, server, view, ROOM, "$unknown", "chat-history-before"))
        .thenThrow(failure);
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> requests.requestBefore(SID, TARGET, "msgid=$unknown", 3).blockingAwait()));
    verify(cursors, never())
        .fetchHistoryEntriesBefore(any(), any(), any(), any(), any(), anyLong(), anyInt());
    assertTrue(batches.isEmpty());
  }

  @Test
  void betweenUsesResolvedInclusiveStartAndExclusiveEndAndPublishesReturnedEntries() {
    Instant end = CENTER.plusSeconds(10);
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, "msgid=$start", "chat-history-between", "start selector"))
        .thenReturn(CENTER);
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, "msgid=$end", "chat-history-between", "end selector"))
        .thenReturn(end);
    List<ChatHistoryEntry> entries = List.of(entry("$start", 0, "start"));
    when(cursors.fetchHistoryEntriesForwardFromCursor(
            SID, server, view, TARGET, ROOM, "cursor", CENTER, end, 3))
        .thenReturn(entries);
    requests
        .requestBetween(" matrix ", " #room ", " msgid=$start ", " msgid=$end ", 3)
        .blockingAwait();
    verify(cursors)
        .resolveForwardCursorForTimestamp(SID, server, view, ROOM, CENTER.toEpochMilli());
    assertEquals(List.of(new Batch(SID, TARGET, entries)), batches);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void betweenRejectsEqualOrReversedResolvedBoundariesBeforeFetch(int endOffset) {
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, "start", "chat-history-between", "start selector"))
        .thenReturn(CENTER);
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, "end", "chat-history-between", "end selector"))
        .thenReturn(CENTER.plusSeconds(endOffset));
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> requests.requestBetween(SID, TARGET, "start", "end", 3).blockingAwait());
    assertEquals("start selector must be earlier than end selector", error.getMessage());
    verify(cursors, never())
        .resolveForwardCursorForTimestamp(any(), any(), any(), any(), anyLong());
    assertTrue(batches.isEmpty());
  }

  @Test
  void betweenWildcardsUseEpochAndCurrentTime() {
    Instant earliestEnd = Instant.now();
    requests.requestBetween(SID, TARGET, "*", "*", 0).blockingAwait();
    var end = ArgumentCaptor.forClass(Instant.class);
    verify(cursors).resolveForwardCursorForTimestamp(SID, server, view, ROOM, 0L);
    verify(cursors)
        .fetchHistoryEntriesForwardFromCursor(
            eq(SID),
            eq(server),
            eq(view),
            eq(TARGET),
            eq(ROOM),
            eq("cursor"),
            eq(Instant.EPOCH),
            end.capture(),
            eq(50));
    assertFalse(end.getValue().isBefore(earliestEnd));
    assertFalse(end.getValue().isAfter(Instant.now()));
  }

  @Test
  void missingBetweenSelectorReturnsErrorBeforeSessionLookup() {
    requests
        .requestBetween(SID, TARGET, " ", "*", 3)
        .test()
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && error
                        .getMessage()
                        .equals("CHATHISTORY BETWEEN requires <start-selector> <end-selector>"));
    verifyNoInteractions(catalog, targets, cursors);
  }

  @Test
  void aroundMergesDeduplicatesAndSelectsClosestEntriesInChronologicalOrder() {
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, TIMESTAMP, "chat-history-around", "selector"))
        .thenReturn(CENTER);
    ChatHistoryEntry left = entry("$left", -2, "left");
    ChatHistoryEntry center = entry("$center", 0, "original");
    ChatHistoryEntry right = entry("$right", 1, "right");
    when(cursors.fetchHistoryEntriesBackwardFromCursor(
            SID, server, view, TARGET, ROOM, "cursor", null, null, 23))
        .thenReturn(List.of(center, left));
    when(cursors.fetchHistoryEntriesForwardFromCursor(
            SID, server, view, TARGET, ROOM, "cursor", null, null, 23))
        .thenReturn(List.of(entry("$center", 0, "duplicate"), entry("$far", 30, "far"), right));
    requests.requestAround(SID, TARGET, TIMESTAMP, 3).blockingAwait();
    assertEquals(List.of(new Batch(SID, TARGET, List.of(left, center, right))), batches);
  }

  @ParameterizedTest
  @CsvSource({"1, 21", "60, 200", "500, 200"})
  void aroundCandidateScansRemainBounded(int limit, int scanLimit) {
    when(cursors.resolveHistorySelectorInstant(
            SID, server, view, ROOM, TIMESTAMP, "chat-history-around", "selector"))
        .thenReturn(CENTER);
    requests.requestAround(SID, TARGET, TIMESTAMP, limit).blockingAwait();
    verify(cursors)
        .fetchHistoryEntriesBackwardFromCursor(
            SID, server, view, TARGET, ROOM, "cursor", null, null, scanLimit);
    verify(cursors)
        .fetchHistoryEntriesForwardFromCursor(
            SID, server, view, TARGET, ROOM, "cursor", null, null, scanLimit);
    assertEquals(List.of(new Batch(SID, TARGET, List.of())), batches);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*"})
  void aroundDefaultSelectorUsesCurrentTimeWithoutSelectorLookup(String selector) {
    long earliest = System.currentTimeMillis();
    requests.requestAround(SID, TARGET, selector, 3).blockingAwait();
    var center = ArgumentCaptor.forClass(Long.class);
    verify(cursors)
        .resolveForwardCursorForTimestamp(
            eq(SID), eq(server), eq(view), eq(ROOM), center.capture());
    assertTrue(center.getValue() >= earliest);
    assertTrue(center.getValue() <= System.currentTimeMillis());
    verify(cursors, never())
        .resolveHistorySelectorInstant(any(), any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "chat-history",
        "chat-history-between",
        "chat-history-around",
        "chat-history-before",
        "chat-history-latest"
      })
  void sessionLookupIsDeferredAndMissingSessionPreservesOperationAndAvailabilityReason(
      String operation) {
    Completable request = request(operation, SID, TARGET);
    sessions.remove(SID);
    var error = assertThrows(BackendNotAvailableException.class, request::blockingAwait);
    assertEquals(operation, error.operation());
    assertTrue(error.getMessage().contains("offline"));
    verifyNoInteractions(catalog, targets, cursors);
    assertTrue(batches.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"chat-history", "chat-history-between", "chat-history-around"})
  void blankServerOrTargetFailsBeforeBackendWork(String operation) {
    var serverError =
        assertThrows(
            IllegalArgumentException.class, () -> request(operation, " ", TARGET).blockingAwait());
    assertEquals(SERVER_ID_BLANK, serverError.getMessage());
    var targetError =
        assertThrows(
            IllegalArgumentException.class, () -> request(operation, SID, " ").blockingAwait());
    assertEquals("target is blank", targetError.getMessage());
    verifyNoInteractions(catalog, targets, cursors);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void resolutionFetchAndPublicationFailuresPropagate(int stage) {
    var failure = new IllegalStateException("failed");
    if (stage == 0) {
      when(targets.resolveHistoryRoomId(SID, TARGET, server, session)).thenThrow(failure);
    } else if (stage == 1) {
      when(cursors.fetchHistoryEntriesBefore(
              SID, server, view, TARGET, ROOM, CENTER.toEpochMilli(), 3))
          .thenThrow(failure);
    } else {
      publicationFailure = failure;
    }
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> requests.requestBefore(SID, TARGET, CENTER, 3).blockingAwait()));
    assertTrue(batches.isEmpty());
  }

  private Completable request(String operation, String sid, String target) {
    return switch (operation) {
      case "chat-history-between" -> requests.requestBetween(sid, target, "*", "*", 3);
      case "chat-history-around" -> requests.requestAround(sid, target, TIMESTAMP, 3);
      case "chat-history-before" -> requests.requestBefore(sid, target, "msgid=$event", 3);
      case "chat-history-latest" -> requests.requestLatest(sid, target, "msgid=$event", 3);
      default -> requests.requestBefore(sid, target, CENTER, 3);
    };
  }

  private static ChatHistoryEntry entry(String id, long offsetSeconds, String text) {
    return new ChatHistoryEntry(
        CENTER.plusSeconds(offsetSeconds),
        ChatHistoryEntry.Kind.PRIVMSG,
        TARGET,
        "@peer:example.org",
        text,
        id);
  }

  private record Batch(String serverId, String target, List<ChatHistoryEntry> entries) {}
}
