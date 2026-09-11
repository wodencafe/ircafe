package cafe.woden.ircclient.ui.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class MacAlerterBackendTest {
  @TempDir Path tempDir;

  private final TestScheduler timers = new TestScheduler();
  private final TestScheduler io = new TestScheduler();
  private final MacAlerterBackend.ProcessStarter starter =
      mock(MacAlerterBackend.ProcessStarter.class);
  private final Runnable click = mock(Runnable.class);
  private final Runnable fallback = mock(Runnable.class);
  private MacAlerterBackend backend;
  private Path executable;
  private String launcher;

  @BeforeEach
  void setUp() throws Exception {
    Path contents = tempDir.resolve("IRCafe.app/Contents");
    Files.createDirectories(contents.resolve("MacOS"));
    Files.createDirectories(contents.resolve("Resources"));
    executable = Files.createFile(contents.resolve("Resources/alerter"));
    launcher = contents.resolve("MacOS/IRCafe").toString();
    backend = new MacAlerterBackend(timers, io, () -> launcher, starter);
  }

  @AfterEach
  void tearDown() {
    backend.close();
  }

  @Test
  void launchPreservesOptionLikeTextAndSeparatesDiagnostics() throws Exception {
    Process process = process("@CLOSED", 0);
    when(starter.start(any())).thenReturn(process);
    assertTrue(backend.tryNotify("-title\nline", "--help", click, fallback));

    ArgumentCaptor<ProcessBuilder> captor = ArgumentCaptor.forClass(ProcessBuilder.class);
    verify(starter).start(captor.capture());
    ProcessBuilder builder = captor.getValue();
    assertEquals(
        List.of(
            executable.toFile().getCanonicalPath(),
            "--title=-title line",
            "--message=--help",
            "--sender=cafe.woden.ircafe",
            "--group=cafe.woden.ircafe",
            "--timeout=5",
            "--actions=Open IRCafe"),
        builder.command());
    assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectError());
    assertFalse(builder.redirectErrorStream());
  }

  @Test
  void notificationsWithoutClickHandlerOmitAction() throws Exception {
    send(process("@CONTENTCLICKED", 0), null);
    ArgumentCaptor<ProcessBuilder> captor = ArgumentCaptor.forClass(ProcessBuilder.class);
    verify(starter).start(captor.capture());
    assertFalse(captor.getValue().command().stream().anyMatch(arg -> arg.startsWith("--actions=")));
    io.triggerActions();
    verifyNoInteractions(fallback);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Open IRCafe", "@CONTENTCLICKED"})
  void successfulClicksRunOnEdtAndCancelTimeout(String result) throws Exception {
    Process process = process(result, 0);
    AtomicInteger edtClicks = new AtomicInteger();
    send(
        process,
        () -> {
          if (SwingUtilities.isEventDispatchThread()) edtClicks.incrementAndGet();
        });
    io.triggerActions();
    flushEdt();
    assertEquals(1, edtClicks.get());
    timers.advanceTimeBy(10, TimeUnit.SECONDS);
    verify(process, never()).destroyForcibly();
    verifyNoInteractions(fallback);
  }

  @ParameterizedTest
  @ValueSource(strings = {"@CLOSED", "@TIMEOUT", "", "unexpected output"})
  void dismissedOrUnknownResultsDoNotActivate(String result) throws Exception {
    send(process(result, 0), click);
    io.triggerActions();
    flushEdt();
    verifyNoInteractions(click, fallback);
  }

  @Test
  void failureFallsBackEvenWhenOutputLooksLikeAClick() throws Exception {
    send(process("@CONTENTCLICKED", 1), click);
    verifyNoInteractions(fallback);
    io.triggerActions();
    flushEdt();
    verify(fallback).run();
    verifyNoInteractions(click);
  }

  @Test
  void stalledHelperIsKilledWithoutDeliveringLateCallbacks() throws Exception {
    Process process = process("@CONTENTCLICKED", 1);
    send(process, click);
    timers.advanceTimeBy(6, TimeUnit.SECONDS);
    verify(process, never()).destroyForcibly();
    timers.advanceTimeBy(1, TimeUnit.SECONDS);
    verify(process).destroyForcibly();
    io.triggerActions();
    flushEdt();
    verifyNoInteractions(click, fallback);
  }

  @Test
  void closeKillsProcessesAndSuppressesFallback() throws Exception {
    Process process = process("error", 1);
    send(process, click);
    backend.close();
    io.triggerActions();
    verify(process).destroyForcibly();
    verifyNoInteractions(click, fallback);
  }

  @Test
  void closeSuppressesClickAlreadyQueuedOnEdt() throws Exception {
    send(process("@CONTENTCLICKED", 0), click);
    SwingUtilities.invokeAndWait(
        () -> {
          io.triggerActions();
          backend.close();
        });
    flushEdt();
    verifyNoInteractions(click, fallback);
  }

  @Test
  void capacityIsBoundedAndReleasedAfterTimeout() throws Exception {
    for (int i = 0; i < 10; i++) {
      send(process("@CLOSED", 0), click);
    }
    assertFalse(backend.tryNotify("title", "body", click, fallback));
    verify(starter, times(10)).start(any());
    timers.advanceTimeBy(7, TimeUnit.SECONDS);
    send(process("@CLOSED", 0), click);
    verify(starter, times(11)).start(any());
  }

  @Test
  void closedBackendDoesNotLaunchOrRequestFallback() {
    backend.close();
    assertTrue(backend.tryNotify("title", "body", click, fallback));
    verifyNoInteractions(starter, fallback);
  }

  @Test
  void oversizedOutputIsDrainedAndNotTreatedAsAClick() throws Exception {
    ByteArrayInputStream input =
        new ByteArrayInputStream("x".repeat(128 * 1024).getBytes(StandardCharsets.UTF_8));
    Process process = process("", 0);
    when(process.getInputStream()).thenReturn(input);
    send(process, click);
    io.triggerActions();
    flushEdt();
    assertEquals(0, input.available());
    verify(process).waitFor();
    verifyNoInteractions(click, fallback);
  }

  @Test
  void missingPackagedLauncherRequestsImmediateFallback() {
    launcher = null;
    assertFalse(backend.tryNotify("title", "body", click, fallback));
    verifyNoInteractions(starter, fallback);
  }

  @Test
  void missingExecutableRequestsImmediateFallback() throws Exception {
    Files.delete(executable);
    assertFalse(backend.tryNotify("title", "body", click, fallback));
    verifyNoInteractions(starter, fallback);
  }

  @Test
  void launchFailureRequestsImmediateFallback() throws Exception {
    when(starter.start(any())).thenThrow(new IOException("cannot execute"));
    assertFalse(backend.tryNotify("title", "body", click, fallback));
    timers.advanceTimeBy(10, TimeUnit.SECONDS);
    io.triggerActions();
    verifyNoInteractions(click, fallback);
  }

  private void send(Process process, Runnable onClick) throws Exception {
    when(starter.start(any())).thenReturn(process);
    assertTrue(backend.tryNotify("title", "body", onClick, fallback));
  }

  private static Process process(String output, int exitCode) throws Exception {
    Process process = mock(Process.class);
    when(process.getInputStream())
        .thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
    when(process.waitFor()).thenReturn(exitCode);
    return process;
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}
