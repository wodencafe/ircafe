package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Routes non-IRC application diagnostics into dedicated application buffers. */
@Component
@ApplicationLayer
public class ApplicationDiagnosticsService {
  private static final Logger log = LoggerFactory.getLogger(ApplicationDiagnosticsService.class);
  private static final int MAX_STACK_LINES = 20;
  private static final int MAX_CAUSE_DEPTH = 4;

  private final UiPort ui;
  private final AtomicBoolean installed = new AtomicBoolean(false);
  private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler =
      this::handleUncaughtException;
  private volatile Thread.UncaughtExceptionHandler previousDefaultHandler;

  public ApplicationDiagnosticsService(UiPort ui) {
    this.ui = ui;
  }

  @PostConstruct
  void install() {
    if (!installed.compareAndSet(false, true)) return;
    previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
  }

  @PreDestroy
  void uninstall() {
    if (!installed.compareAndSet(true, false)) return;
    if (Thread.getDefaultUncaughtExceptionHandler() == uncaughtExceptionHandler) {
      Thread.setDefaultUncaughtExceptionHandler(previousDefaultHandler);
    }
  }

  public void appendAssertjSwingStatus(String message) {
    appendDiagnostic(TargetRef.applicationAssertjSwing(), "(assertj-swing)", message, false);
  }

  public void appendAssertjSwingError(String message) {
    appendDiagnostic(TargetRef.applicationAssertjSwing(), "(assertj-swing)", message, true);
  }

  public void appendJhiccupStatus(String message) {
    appendDiagnostic(TargetRef.applicationJhiccup(), "(jhiccup)", message, false);
  }

  public void appendJhiccupError(String message) {
    appendDiagnostic(TargetRef.applicationJhiccup(), "(jhiccup)", message, true);
  }

  public void notifyUiStallRecovered(long stallMs) {
    long ms = Math.max(0L, stallMs);
    String message = "Recovered from UI stall (" + ms + " ms).";
    appendDiagnostic(TargetRef.applicationAssertjSwing(), "(assertj-swing)", message, false);
    ui.enqueueStatusNotice(message, TargetRef.applicationAssertjSwing());
  }

  private void handleUncaughtException(Thread thread, Throwable error) {
    try {
      String threadName = normalizeThreadName(thread);
      String summary = summarize(error);
      if (isAssertjSwingViolation(error)) {
        appendDiagnostic(
            TargetRef.applicationAssertjSwing(),
            "(assertj-swing)",
            threadName + ": " + summary,
            true);
        appendStack(TargetRef.applicationAssertjSwing(), error);
      }
      appendDiagnostic(
          TargetRef.applicationUnhandledErrors(), "(uncaught)", threadName + ": " + summary, true);
      appendStack(TargetRef.applicationUnhandledErrors(), error);
    } catch (Throwable t) {
      log.error("[ircafe] failed to publish uncaught exception to diagnostics buffer", t);
    }

    // Keep the exception visible in logs regardless of UI state.
    try {
      if (error != null) {
        log.error("[ircafe] uncaught exception on {}", normalizeThreadName(thread), error);
      } else {
        log.error(
            "[ircafe] uncaught exception on {}: (null throwable)", normalizeThreadName(thread));
      }
    } catch (Throwable ignored) {
    }

    Thread.UncaughtExceptionHandler prev = previousDefaultHandler;
    if (prev != null && prev != uncaughtExceptionHandler) {
      try {
        prev.uncaughtException(thread, error);
      } catch (Throwable ignored) {
      }
    }
  }

  private void appendDiagnostic(TargetRef target, String from, String message, boolean error) {
    if (target == null) return;
    String text = Objects.toString(message, "").trim();
    if (text.isEmpty()) return;
    ui.ensureTargetExists(target);
    if (error) {
      ui.appendError(target, from, text);
    } else {
      ui.appendStatus(target, from, text);
    }
  }

  private void appendStack(TargetRef target, Throwable error) {
    if (target == null || error == null) return;
    ui.ensureTargetExists(target);

    int emitted = 0;
    StackTraceElement[] stack = error.getStackTrace();
    if (stack != null && stack.length > 0) {
      int limit = Math.min(stack.length, MAX_STACK_LINES);
      for (int i = 0; i < limit; i++) {
        StackTraceElement ste = stack[i];
        ui.appendStatus(target, "(stack)", Objects.toString(ste, ""));
        emitted++;
      }
      if (stack.length > limit) {
        ui.appendStatus(target, "(stack)", "... " + (stack.length - limit) + " more");
      }
    }

    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable cause = error.getCause();
    int depth = 0;
    while (cause != null && !seen.contains(cause) && depth < MAX_CAUSE_DEPTH) {
      seen.add(cause);
      ui.appendStatus(target, "(cause)", summarize(cause));
      StackTraceElement[] causeStack = cause.getStackTrace();
      if (causeStack != null && causeStack.length > 0) {
        int limit = Math.min(causeStack.length, MAX_STACK_LINES);
        for (int i = 0; i < limit; i++) {
          StackTraceElement ste = causeStack[i];
          ui.appendStatus(target, "(stack)", Objects.toString(ste, ""));
          emitted++;
        }
        if (causeStack.length > limit) {
          ui.appendStatus(target, "(stack)", "... " + (causeStack.length - limit) + " more");
        }
      }
      cause = cause.getCause();
      depth++;
    }

    if (emitted == 0) {
      ui.appendStatus(target, "(stack)", "(no stack trace)");
    }
  }

  private static String normalizeThreadName(Thread thread) {
    String name = thread == null ? "" : Objects.toString(thread.getName(), "").trim();
    if (!name.isEmpty()) return name;
    return thread == null ? "(unknown-thread)" : "(unnamed-thread)";
  }

  private static String summarize(Throwable error) {
    if (error == null) return "(null throwable)";
    String type = error.getClass().getSimpleName();
    if (type == null || type.isBlank()) {
      type = error.getClass().getName();
    }
    String msg = Objects.toString(error.getMessage(), "").trim();
    return msg.isEmpty() ? type : (type + ": " + msg);
  }

  private static boolean isAssertjSwingViolation(Throwable error) {
    if (error == null) return false;
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable cur = error;
    int depth = 0;
    while (cur != null && depth < 8 && !seen.contains(cur)) {
      seen.add(cur);
      String cn = cur.getClass().getName();
      if ("org.assertj.swing.exception.EdtViolationException".equals(cn)) return true;
      if (cn != null
          && cn.startsWith("org.assertj.swing.")
          && cn.toLowerCase(java.util.Locale.ROOT).contains("violation")) {
        return true;
      }
      cur = cur.getCause();
      depth++;
    }
    return false;
  }
}
