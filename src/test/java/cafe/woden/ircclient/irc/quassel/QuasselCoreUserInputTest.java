package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import cafe.woden.ircclient.irc.quassel.QuasselCoreUserInput.Plan;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class QuasselCoreUserInputTest {
  private static final String GRACE_PROPERTY = "ircafe.rx.shutdown.grace.ms";
  private static final String CHANNEL = "#room{net:other}";
  private static final String NICK = "alice{net:other}";
  private final ServerCatalog servers = mock(ServerCatalog.class);
  private final QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
  private final QuasselCoreIrcClientService service =
      QuasselRuntimeTestFixtures.service(
          servers,
          mock(QuasselCoreSocketConnector.class),
          mock(QuasselCoreProtocolProbe.class),
          mock(QuasselCoreAuthHandshake.class),
          codec);
  private String previousGrace;

  @BeforeEach
  void shortenShutdownGrace() {
    previousGrace = System.getProperty(GRACE_PROPERTY);
    System.setProperty(GRACE_PROPERTY, "0");
  }

  @AfterEach
  void shutdown() {
    try {
      service.shutdownNow();
      RxVirtualSchedulers.shutdown();
    } finally {
      if (previousGrace == null) System.clearProperty(GRACE_PROPERTY);
      else System.setProperty(GRACE_PROPERTY, previousGrace);
    }
  }

  @ParameterizedTest
  @MethodSource("validInputs")
  void plansKeepCommandRenderingRoutingHintsAndTrimSemantics(
      Command command, String target, String text, Plan expected) {
    assertEquals(expected, command.plan(target, text));
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void invalidPlansKeepErrorMessagesAndValidationPrecedence(
      Command command, String target, String text, String reason) {
    assertEquals(
        reason,
        assertThrows(IllegalArgumentException.class, () -> command.plan(target, text))
            .getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void invalidFacadeCallsReturnImmediateReactiveErrorsBeforeServerValidation(
      Command command, String target, String text, String reason) {
    Completable action = assertDoesNotThrow(() -> command.execute(service, " ", target, text));
    Thread caller = Thread.currentThread();
    AtomicReference<Thread> errorThread = new AtomicReference<>();
    action
        .doOnError(error -> errorThread.set(Thread.currentThread()))
        .test()
        .assertFailure(IllegalArgumentException.class)
        .assertError(error -> reason.equals(error.getMessage()));
    assertSame(caller, errorThread.get());
    verifyNoInteractions(servers);
  }

  @ParameterizedTest
  @MethodSource("validInputs")
  void validFacadeCallsKeepOperationInUnavailableBackendError(
      Command command, String target, String text, Plan expected) {
    command
        .execute(service, " core ", target, text)
        .test()
        .awaitDone(2, java.util.concurrent.TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof BackendNotAvailableException unavailable
                    && unavailable.operation().equals(expected.operation())
                    && unavailable.serverId().equals("core"));
  }

  @ParameterizedTest
  @MethodSource("validInputs")
  void facadeRendersOneNativeInputWithExistingQualifiedBufferRouting(
      Command command, String target, String text, Plan expected) throws Exception {
    ByteArrayOutputStream wire = new ByteArrayOutputStream();
    Socket socket = mock(Socket.class);
    when(socket.getOutputStream()).thenReturn(wire);
    QuasselCoreSession session =
        new QuasselCoreSession(
            "core",
            "me",
            "host",
            4242,
            new QuasselCoreDatastreamSender(codec),
            sid -> {},
            event -> {});
    session.socketRef.set(socket);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "primary");
    session.networks.observe(8, "other");
    var channel = new BufferInfoValue(11, 8, 2, -1, "#room");
    var query = new BufferInfoValue(22, 8, 4, -1, "alice");
    session.buffers.merge(channel);
    session.buffers.merge(query);
    installSession(session);
    Completable action = command.execute(service, " core ", target, text);
    assertEquals(0, wire.size(), "a valid call must wait for subscription before writing");
    action.blockingAwait();
    ByteArrayInputStream in = new ByteArrayInputStream(wire.toByteArray());
    var rpc = codec.readSignalProxyMessage(in);
    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, rpc.requestType());
    assertEquals("2sendInput(BufferInfo,QString)", rpc.slotName());
    BufferInfoValue expectedBuffer;
    if (expected.typeBits() == 1) {
      expectedBuffer = new BufferInfoValue(-1, 7, 1, -1, "");
    } else {
      expectedBuffer = expected.typeBits() == 2 ? channel : query;
      assertEquals(8, session.targetNetworkHints.networkIdForTarget(expected.target()));
    }
    // The inbound decoder skips sendInput params. Compare the entire outbound frame instead.
    ByteArrayOutputStream expectedWire = new ByteArrayOutputStream();
    codec.writeSignalProxyRpcCall(
        expectedWire, "2sendInput(BufferInfo,QString)", List.of(expectedBuffer, expected.input()));
    assertArrayEquals(expectedWire.toByteArray(), wire.toByteArray());
    assertEquals(0, in.available(), "one subscription must render exactly one RPC");
  }

  @SuppressWarnings("unchecked")
  private void installSession(QuasselCoreSession session) {
    Map<String, QuasselCoreSession> sessions =
        (Map<String, QuasselCoreSession>) ReflectionTestUtils.getField(service, "sessions");
    sessions.put("core", session);
  }

  private enum Command {
    NICK_CHANGE,
    AWAY,
    NAMES,
    JOIN,
    WHOIS,
    PART,
    CHANNEL_MESSAGE,
    PRIVATE_MESSAGE,
    CHANNEL_NOTICE,
    PRIVATE_NOTICE;

    Plan plan(String target, String text) {
      return switch (this) {
        case NICK_CHANGE -> QuasselCoreUserInput.changeNick(target);
        case AWAY -> QuasselCoreUserInput.setAway(text);
        case NAMES -> QuasselCoreUserInput.requestNames(target);
        case JOIN -> QuasselCoreUserInput.joinChannel(target);
        case WHOIS -> QuasselCoreUserInput.whois(target);
        case PART -> QuasselCoreUserInput.partChannel(target, text);
        case CHANNEL_MESSAGE -> QuasselCoreUserInput.sendToChannel(target, text);
        case PRIVATE_MESSAGE -> QuasselCoreUserInput.sendPrivateMessage(target, text);
        case CHANNEL_NOTICE -> QuasselCoreUserInput.sendNoticeToChannel(target, text);
        case PRIVATE_NOTICE -> QuasselCoreUserInput.sendNoticePrivate(target, text);
      };
    }

    Completable execute(
        QuasselCoreIrcClientService service, String sid, String target, String text) {
      return switch (this) {
        case NICK_CHANGE -> service.changeNick(sid, target);
        case AWAY -> service.setAway(sid, text);
        case NAMES -> service.requestNames(sid, target);
        case JOIN -> service.joinChannel(sid, target);
        case WHOIS -> service.whois(sid, target);
        case PART -> service.partChannel(sid, target, text);
        case CHANNEL_MESSAGE -> service.sendToChannel(sid, target, text);
        case PRIVATE_MESSAGE -> service.sendPrivateMessage(sid, target, text);
        case CHANNEL_NOTICE -> service.sendNoticeToChannel(sid, target, text);
        case PRIVATE_NOTICE -> service.sendNoticePrivate(sid, target, text);
      };
    }
  }

  private static Stream<Arguments> validInputs() {
    return Stream.of(
        valid(Command.NICK_CHANGE, " newNick ", null, "change nick", 1, "", "/NICK newNick"),
        valid(Command.AWAY, null, " away reason ", "set away", 1, "", "/AWAY away reason"),
        valid(Command.AWAY, null, null, "set away", 1, "", "/AWAY"),
        valid(Command.AWAY, null, " \r\n\t ", "set away", 1, "", "/AWAY"),
        valid(
            Command.NAMES,
            " " + CHANNEL + " ",
            null,
            "request names",
            2,
            CHANNEL,
            "/NAMES " + CHANNEL),
        valid(Command.JOIN, " " + CHANNEL + " ", null, "join channel", 1, "", "/JOIN " + CHANNEL),
        valid(Command.WHOIS, " " + NICK + " ", null, "whois", 4, NICK, "/WHOIS " + NICK),
        valid(
            Command.PART,
            " " + CHANNEL + " ",
            " reason ",
            "part channel",
            2,
            CHANNEL,
            "/PART " + CHANNEL + " reason"),
        valid(Command.PART, CHANNEL, null, "part channel", 2, CHANNEL, "/PART " + CHANNEL),
        valid(
            Command.CHANNEL_MESSAGE,
            " " + CHANNEL + " ",
            " hello ",
            "send message to channel",
            2,
            CHANNEL,
            "hello"),
        valid(
            Command.PRIVATE_MESSAGE,
            " " + NICK + " ",
            " hello ",
            "send private message",
            4,
            NICK,
            "hello"),
        valid(
            Command.CHANNEL_NOTICE,
            CHANNEL,
            " hello ",
            "send notice to channel",
            2,
            CHANNEL,
            "/NOTICE " + CHANNEL + " hello"),
        valid(
            Command.PRIVATE_NOTICE,
            NICK,
            " hello ",
            "send notice",
            4,
            NICK,
            "/NOTICE " + NICK + " hello"),
        valid(
            Command.CHANNEL_MESSAGE,
            "\r\n" + CHANNEL + "\n",
            "\r/ME hello\n",
            "send message to channel",
            2,
            CHANNEL,
            "/ME hello"),
        valid(
            Command.PRIVATE_MESSAGE,
            NICK,
            "\u2003hello\u2003",
            "send private message",
            4,
            NICK,
            "\u2003hello\u2003"));
  }

  private static Arguments valid(
      Command command,
      String target,
      String text,
      String operation,
      int type,
      String buffer,
      String input) {
    return Arguments.of(command, target, text, new Plan(operation, type, buffer, input));
  }

  private static Stream<Arguments> invalidInputs() {
    List<Arguments> cases = new ArrayList<>();
    for (Command command :
        List.of(Command.NICK_CHANGE, Command.NAMES, Command.JOIN, Command.WHOIS)) {
      String name =
          command == Command.NICK_CHANGE
              ? "new nick"
              : command == Command.WHOIS ? "nick" : "channel";
      for (String target : new String[] {null, "", " \t "})
        cases.add(Arguments.of(command, target, null, name + " is blank"));
      for (String target : List.of("a\rb", "a\nb"))
        cases.add(Arguments.of(command, target, null, name + " contains CR/LF"));
    }
    for (String text : List.of("a\rb", "a\nb"))
      cases.add(Arguments.of(Command.AWAY, null, text, "away message contains CR/LF"));
    cases.add(Arguments.of(Command.PART, null, "a\nb", "channel is blank"));
    cases.add(Arguments.of(Command.PART, " ", null, "channel is blank"));
    cases.add(Arguments.of(Command.PART, "a\nb", null, "part parameters contain CR/LF"));
    cases.add(Arguments.of(Command.PART, CHANNEL, "a\rb", "part parameters contain CR/LF"));
    cases.add(Arguments.of(Command.PART, CHANNEL, "a\nb", "part parameters contain CR/LF"));
    for (Command command :
        List.of(
            Command.CHANNEL_MESSAGE,
            Command.PRIVATE_MESSAGE,
            Command.CHANNEL_NOTICE,
            Command.PRIVATE_NOTICE)) {
      boolean channel = command == Command.CHANNEL_MESSAGE || command == Command.CHANNEL_NOTICE;
      boolean notice = command == Command.CHANNEL_NOTICE || command == Command.PRIVATE_NOTICE;
      String blank = (channel ? "channel" : "nick") + " is blank";
      String target = channel ? CHANNEL : NICK;
      String newline = (notice ? "notice" : "message") + " parameters contain CR/LF";
      cases.add(Arguments.of(command, null, "a\nb", blank));
      cases.add(Arguments.of(command, " ", "hello", blank));
      cases.add(Arguments.of(command, target, null, "message is blank"));
      cases.add(Arguments.of(command, "a\nb", " \t ", "message is blank"));
      for (String bad : List.of("a\rb", "a\nb")) {
        cases.add(Arguments.of(command, bad, "hello", newline));
        cases.add(Arguments.of(command, target, bad, newline));
      }
    }
    return cases.stream();
  }
}
