package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.*;

import io.reactivex.rxjava3.core.Completable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MatrixRawChatHistoryCommandHandlerTest {
  private static final String SID = "matrix";
  private static final String ROOM = "!room:example.org";
  private static final String LIMIT_ERROR = "CHATHISTORY limit must be a positive integer";
  private final List<Request> requests = new ArrayList<>();
  private final AtomicInteger subscriptions = new AtomicInteger();
  private Completable result = Completable.fromAction(subscriptions::incrementAndGet);
  private RuntimeException invocationFailure;
  private final MatrixRawChatHistoryCommandHandler handler =
      new MatrixRawChatHistoryCommandHandler(
          (sid, target, selector, limit) ->
              invoke(new Request("BEFORE", sid, target, selector, "", limit)),
          (sid, target, selector, limit) ->
              invoke(new Request("LATEST", sid, target, selector, "", limit)),
          (sid, target, start, end, limit) ->
              invoke(new Request("BETWEEN", sid, target, start, end, limit)),
          (sid, target, selector, limit) ->
              invoke(new Request("AROUND", sid, target, selector, "", limit)));

  @ParameterizedTest
  @MethodSource("validRequests")
  void delegatesNormalizedArgumentsAndReturnsUnsubscribedOperation(
      List<String> arguments, Request expected) {
    assertSame(result, handler.handle(SID, arguments));
    assertEquals(List.of(expected), requests);
    assertEquals(0, subscriptions.get());
    result.blockingAwait();
    assertEquals(1, subscriptions.get());
  }

  private static Stream<Arguments> validRequests() {
    return Stream.of(
        valid(List.of("BEFORE", ROOM, "*"), "BEFORE", "*", "", 50),
        valid(
            List.of(" before ", " " + ROOM + " ", " msgid=$event ", " +200 "),
            "BEFORE",
            "msgid=$event",
            "",
            200),
        valid(List.of("LATEST", ROOM), "LATEST", "*", "", 50),
        valid(List.of("LATEST", ROOM, "75"), "LATEST", "*", "", 75),
        valid(List.of("LATEST", ROOM, "+075"), "LATEST", "*", "", 75),
        valid(
            List.of("LATEST", ROOM, "timestamp=2026-10-11T00:00:00Z"),
            "LATEST",
            "timestamp=2026-10-11T00:00:00Z",
            "",
            50),
        valid(List.of("LATEST", ROOM, "msgid=$event", "100"), "LATEST", "msgid=$event", "", 100),
        valid(List.of("LATEST", ROOM, "*", "+5"), "LATEST", "*", "", 5),
        valid(List.of("LATEST", ROOM, "0001"), "LATEST", "*", "", 1),
        valid(List.of("LATEST", ROOM, "-1"), "LATEST", "-1", "", 50),
        valid(List.of("LATEST", ROOM, "+"), "LATEST", "+", "", 50),
        valid(
            List.of("BETWEEN", ROOM, "msgid=$start", " timestamp=2026-10-11T00:00:00Z "),
            "BETWEEN",
            "msgid=$start",
            "timestamp=2026-10-11T00:00:00Z",
            50),
        valid(
            List.of(" between ", ROOM, " * ", " msgid=$end ", "2147483647"),
            "BETWEEN",
            "*",
            "msgid=$end",
            Integer.MAX_VALUE),
        valid(List.of("AROUND", ROOM, "msgid=$anchor"), "AROUND", "msgid=$anchor", "", 50),
        valid(List.of(" around ", ROOM, " * ", "4"), "AROUND", "*", "", 4));
  }

  @ParameterizedTest
  @MethodSource("invalidSyntax")
  void rejectsInvalidSyntaxBeforeInvokingHistory(List<String> arguments, String message) {
    var error = assertThrows(IllegalArgumentException.class, () -> handler.handle(SID, arguments));
    assertEquals(message, error.getMessage());
    assertTrue(requests.isEmpty());
    assertEquals(0, subscriptions.get());
  }

  private static Stream<Arguments> invalidSyntax() {
    var cases = new ArrayList<Arguments>();
    String missingMode = "CHATHISTORY requires a mode (BEFORE|LATEST|BETWEEN|AROUND)";
    cases.add(Arguments.of(null, missingMode));
    cases.add(Arguments.of(List.of(), missingMode));
    String unknownMode = "CHATHISTORY mode must be BEFORE, LATEST, BETWEEN, or AROUND";
    cases.add(Arguments.of(List.of("FUTURE", ROOM), unknownMode));
    cases.add(Arguments.of(List.of(" "), unknownMode));
    for (String mode : List.of("BEFORE", "AROUND")) {
      String syntax = "CHATHISTORY " + mode + " requires <target> <selector> [limit]";
      cases.add(Arguments.of(List.of(mode), syntax));
      cases.add(Arguments.of(List.of(mode, ROOM), syntax));
      cases.add(Arguments.of(List.of(mode, ROOM, "*", "1", "extra"), syntax));
      cases.add(Arguments.of(List.of(mode, " ", "*"), syntax));
      cases.add(Arguments.of(List.of(mode, ROOM, " "), syntax));
    }
    String latest = "CHATHISTORY LATEST requires <target> [selector|*] [limit]";
    cases.add(Arguments.of(List.of("LATEST"), latest));
    cases.add(Arguments.of(List.of("LATEST", ROOM, "*", "1", "extra"), latest));
    cases.add(Arguments.of(List.of("LATEST", " "), latest));
    cases.add(Arguments.of(List.of("LATEST", ROOM, " "), latest));
    String between =
        "CHATHISTORY BETWEEN requires <target> <start-selector> <end-selector> [limit]";
    cases.add(Arguments.of(List.of("BETWEEN", ROOM, "*"), between));
    cases.add(Arguments.of(List.of("BETWEEN", ROOM, "*", "*", "1", "extra"), between));
    cases.add(Arguments.of(List.of("BETWEEN", " ", "*", "*"), between));
    cases.add(Arguments.of(List.of("BETWEEN", ROOM, " ", "*"), between));
    cases.add(Arguments.of(List.of("BETWEEN", ROOM, "*", " "), between));
    return cases.stream();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "0", "-1", "+", "1.5", "1x", "2147483648", "++2", "１２"})
  void rejectsInvalidExplicitLimit(String token) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.handle(SID, List.of("BEFORE", ROOM, "*", token)));
    assertEquals(LIMIT_ERROR, error.getMessage());
    if (token.equals("2147483648")) {
      assertInstanceOf(NumberFormatException.class, error.getCause());
    }
    assertTrue(requests.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"BEFORE", "LATEST", "BETWEEN", "AROUND"})
  void allModesValidateTheirExplicitLimit(String mode) {
    List<String> args =
        mode.equals("BETWEEN") ? List.of(mode, ROOM, "*", "*", "0") : List.of(mode, ROOM, "*", "0");
    var error = assertThrows(IllegalArgumentException.class, () -> handler.handle(SID, args));
    assertEquals(LIMIT_ERROR, error.getMessage());
    assertTrue(requests.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "+0", "2147483648"})
  void latestNumericShorthandMustBeAPositiveInt(String token) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.handle(SID, List.of("LATEST", ROOM, token)));
    assertEquals(LIMIT_ERROR, error.getMessage());
    assertTrue(requests.isEmpty());
  }

  @Test
  void latestWithFourArgumentsTreatsThirdAsSelectorEvenWhenNumeric() {
    handler.handle(SID, List.of("LATEST", ROOM, "123", "5"));
    assertEquals(List.of(new Request("LATEST", SID, ROOM, "123", "", 5)), requests);
  }

  @Test
  void leavesServerIdentityAndSelectorInterpretationToExistingRequestOperations() {
    handler.handle(" matrix ", List.of("BEFORE", "#room", "custom=value"));
    assertEquals(
        List.of(new Request("BEFORE", " matrix ", "#room", "custom=value", "", 50)), requests);
  }

  @Test
  void synchronousRequestFailurePropagatesImmediately() {
    invocationFailure = new IllegalArgumentException("unsupported selector");
    assertSame(
        invocationFailure,
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.handle(SID, List.of("BEFORE", ROOM, "custom=value"))));
    assertEquals(0, subscriptions.get());
  }

  @Test
  void asynchronousRequestFailureIsPreservedWithoutSubscribing() {
    var failure = new IllegalStateException("history unavailable");
    result = Completable.error(failure);
    Completable delegated = handler.handle(SID, List.of("BEFORE", ROOM, "*"));
    assertSame(result, delegated);
    delegated.test().assertError(failure);
  }

  private Completable invoke(Request request) {
    if (invocationFailure != null) throw invocationFailure;
    requests.add(request);
    return result;
  }

  private static Arguments valid(
      List<String> arguments, String mode, String start, String end, int limit) {
    return Arguments.of(arguments, new Request(mode, SID, ROOM, start, end, limit));
  }

  private record Request(
      String mode, String serverId, String target, String start, String end, int limit) {}
}
