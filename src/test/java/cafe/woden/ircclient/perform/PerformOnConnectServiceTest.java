package cafe.woden.ircclient.perform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PerformOnConnectServiceTest {

  private IrcClientService irc;
  private ServerCatalog serverCatalog;
  private CommandParser commandParser;
  private UiPort ui;
  private PublishProcessor<ServerIrcEvent> events;
  private PerformOnConnectService service;

  @BeforeEach
  void setUp() {
    irc = Mockito.mock(IrcClientService.class);
    serverCatalog = Mockito.mock(ServerCatalog.class);
    commandParser = Mockito.mock(CommandParser.class);
    ui = Mockito.mock(UiPort.class);
    events = PublishProcessor.create();
    when(irc.events()).thenReturn(events);
    service = new PerformOnConnectService(irc, serverCatalog, commandParser, ui);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void unknownCommandIsReportedAndLaterLinesStillRun() {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/weird command", "RAW NEXT"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Unknown("/weird command")).when(commandParser).parse("/weird command");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "RAW NEXT");

    fireConnected();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_000))
        .appendStatus(status, "(perform)", "Unknown perform command: /weird command");
    verify(irc, timeout(2_000)).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void perLineFailureIsSurfacedAndRunContinues() {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/join #ircafe", "RAW NEXT"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Join("#ircafe")).when(commandParser).parse("/join #ircafe");
    doReturn(Completable.error(new IllegalStateException("join failed")))
        .when(irc)
        .joinChannel("libera", "#ircafe");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "RAW NEXT");

    fireConnected();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_500))
        .appendError(eq(status), eq("(perform)"), contains("Error running: /join #ircafe"));
    verify(irc, timeout(2_000)).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void reconnectStormCancelsPriorRunAndPreventsDuplicates() throws Exception {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 700", "RAW ONCE"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger rawCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              rawCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(irc)
        .sendRaw("libera", "RAW ONCE");

    fireConnected();
    Thread.sleep(100);
    fireConnected();

    Thread.sleep(1_300);
    assertEquals(1, rawCalls.get(), "reconnect should keep only the latest perform run active");
  }

  private void fireConnected() {
    events.onNext(
        new ServerIrcEvent("libera", new IrcEvent.Connected(Instant.now(), "irc", 6697, "me")));
  }

  private static IrcProperties.Server serverWithPerform(String id, List<String> perform) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "tester",
        "tester",
        "Tester",
        null,
        List.of(),
        perform,
        null);
  }
}
