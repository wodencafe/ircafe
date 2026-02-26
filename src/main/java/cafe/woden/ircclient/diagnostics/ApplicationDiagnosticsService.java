package cafe.woden.ircclient.diagnostics;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Routes non-IRC application diagnostics into dedicated application buffers. */
@Component
@ApplicationLayer
public class ApplicationDiagnosticsService {
  private static final Logger log = LoggerFactory.getLogger(ApplicationDiagnosticsService.class);
  private static final int MAX_STACK_LINES = 20;
  private static final int MAX_CAUSE_DEPTH = 4;
  private static final int MAX_EVENTS_PER_BUFFER = 1200;

  private final UiPort ui;
  private final AtomicBoolean installed = new AtomicBoolean(false);
  private final Deque<RuntimeDiagnosticEvent> assertjSwingEvents = new ArrayDeque<>();
  private final Deque<RuntimeDiagnosticEvent> jhiccupEvents = new ArrayDeque<>();
  private final FlowableProcessor<Long> assertjSwingChangeSignals =
      PublishProcessor.<Long>create().toSerialized();
  private final FlowableProcessor<Long> jhiccupChangeSignals =
      PublishProcessor.<Long>create().toSerialized();
  private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler =
      this::handleUncaughtException;
  private long assertjSwingChangeSeq;
  private long jhiccupChangeSeq;
  private volatile Thread.UncaughtExceptionHandler previousDefaultHandler;

  public ApplicationDiagnosticsService(@Lazy UiPort ui) {
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

  public synchronized List<RuntimeDiagnosticEvent> recentAssertjSwingEvents(int limit) {
    return recentEvents(assertjSwingEvents, limit);
  }

  public synchronized List<RuntimeDiagnosticEvent> recentJhiccupEvents(int limit) {
    return recentEvents(jhiccupEvents, limit);
  }

  public Flowable<Long> assertjSwingChangeStream() {
    return assertjSwingChangeSignals.onBackpressureLatest();
  }

  public Flowable<Long> jhiccupChangeStream() {
    return jhiccupChangeSignals.onBackpressureLatest();
  }

  public synchronized void clearAssertjSwingEvents() {
    if (assertjSwingEvents.isEmpty()) return;
    assertjSwingEvents.clear();
    emitAssertjSwingChangeLocked();
  }

  public synchronized void clearJhiccupEvents() {
    if (jhiccupEvents.isEmpty()) return;
    jhiccupEvents.clear();
    emitJhiccupChangeLocked();
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
    appendRuntimeEvent(target, from, text, error);
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

  private synchronized void appendRuntimeEvent(
      TargetRef target, String from, String message, boolean error) {
    if (target == null) return;
    Deque<RuntimeDiagnosticEvent> buffer = null;
    boolean assertjTarget = false;
    boolean jhiccupTarget = false;
    if (target.isApplicationAssertjSwing()) {
      buffer = assertjSwingEvents;
      assertjTarget = true;
    } else if (target.isApplicationJhiccup()) {
      buffer = jhiccupEvents;
      jhiccupTarget = true;
    }
    if (buffer == null) return;

    String level = error ? "ERROR" : "INFO";
    String type = normalizeEventType(from);
    RuntimeDiagnosticEvent event = new RuntimeDiagnosticEvent(Instant.now(), level, type, message, "");
    buffer.addLast(event);
    while (buffer.size() > MAX_EVENTS_PER_BUFFER) {
      buffer.removeFirst();
    }
    if (assertjTarget) {
      emitAssertjSwingChangeLocked();
    } else if (jhiccupTarget) {
      emitJhiccupChangeLocked();
    }
  }

  private void emitAssertjSwingChangeLocked() {
    assertjSwingChangeSignals.onNext(++assertjSwingChangeSeq);
  }

  private void emitJhiccupChangeLocked() {
    jhiccupChangeSignals.onNext(++jhiccupChangeSeq);
  }

  private static String normalizeEventType(String from) {
    String raw = Objects.toString(from, "").trim();
    if (raw.isEmpty()) return "diagnostic";
    if (raw.startsWith("(") && raw.endsWith(")") && raw.length() > 2) {
      raw = raw.substring(1, raw.length() - 1).trim();
    }
    return raw.isEmpty() ? "diagnostic" : raw;
  }

  private static List<RuntimeDiagnosticEvent> recentEvents(
      Deque<RuntimeDiagnosticEvent> events, int limit) {
    int max = Math.max(1, Math.min(2000, limit));
    if (events == null || events.isEmpty()) return List.of();
    ArrayList<RuntimeDiagnosticEvent> out = new ArrayList<>(Math.min(max, events.size()));
    Iterator<RuntimeDiagnosticEvent> it = events.descendingIterator();
    while (it.hasNext() && out.size() < max) {
      out.add(it.next());
    }
    return List.copyOf(out);
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
