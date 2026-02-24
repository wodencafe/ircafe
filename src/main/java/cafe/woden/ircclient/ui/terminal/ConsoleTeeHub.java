package cafe.woden.ircclient.ui.terminal;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Global (static) console mirroring used by the in-app Terminal dock.
 *
 * <p>We install the tee as early as possible (from main) so that Spring Boot startup logs and any
 * other early writes are captured.
 */
public final class ConsoleTeeHub {

  private static final int DEFAULT_MAX_CHARS = 1_000_000; // ~1MB

  private static final TerminalBuffer BUFFER = new TerminalBuffer(DEFAULT_MAX_CHARS);
  private static final CopyOnWriteArrayList<Consumer<String>> LISTENERS =
      new CopyOnWriteArrayList<>();

  private static volatile boolean installed;
  private static volatile PrintStream originalOut;
  private static volatile PrintStream originalErr;

  private ConsoleTeeHub() {}

  /** Install the tee streams (idempotent). Safe to call multiple times. */
  public static synchronized void install() {
    if (installed) return;

    originalOut = System.out;
    originalErr = System.err;

    Charset cs = Charset.defaultCharset();
    PrintStream teeOut =
        new PrintStream(new TeeOutputStream(originalOut, ConsoleTeeHub::publish, cs), true, cs);
    PrintStream teeErr =
        new PrintStream(new TeeOutputStream(originalErr, ConsoleTeeHub::publish, cs), true, cs);

    System.setOut(teeOut);
    System.setErr(teeErr);
    installed = true;
  }

  /** Restore the original streams (best-effort). */
  public static synchronized void restore() {
    try {
      if (originalOut != null) System.setOut(originalOut);
      if (originalErr != null) System.setErr(originalErr);
    } catch (Exception ignored) {
    }
  }

  public static String snapshot() {
    return BUFFER.snapshot();
  }

  public static AutoCloseable addListener(Consumer<String> listener) {
    Objects.requireNonNull(listener, "listener");
    LISTENERS.add(listener);
    return () -> LISTENERS.remove(listener);
  }

  private static void publish(String text) {
    if (text == null || text.isEmpty()) return;
    BUFFER.append(text);
    for (Consumer<String> l : LISTENERS) {
      try {
        l.accept(text);
      } catch (Exception ignored) {
      }
    }
  }
}
