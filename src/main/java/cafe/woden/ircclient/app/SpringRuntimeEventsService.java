package cafe.woden.ircclient.app;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

/** Captures Spring framework/runtime events into a rolling in-memory feed for UI diagnostics. */
@Service
@Lazy(false)
@ApplicationLayer
@NamedInterface("diagnostics")
public class SpringRuntimeEventsService implements ApplicationListener<ApplicationEvent> {
  private static final int MAX_EVENTS = 1200;
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private final Deque<RuntimeDiagnosticEvent> events = new ArrayDeque<>();

  @PostConstruct
  public void onStart() {
    appendEvent(
        Instant.now(),
        "INFO",
        "SpringRuntimeEventsService",
        "Spring runtime event capture initialized.",
        "Listening for ApplicationEvent emissions from the Spring context.");
  }

  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (event == null) return;
    Instant at = Instant.ofEpochMilli(event.getTimestamp());
    String type = event.getClass().getName();
    String level = "INFO";
    String summary = summarizeEvent(event);
    String details = describeEvent(event);

    if (containsErrorSignal(event, summary, details)) {
      level = "ERROR";
    } else if (containsWarningSignal(summary)) {
      level = "WARN";
    }

    appendEvent(at, level, type, summary, details);
  }

  public synchronized List<RuntimeDiagnosticEvent> recentEvents(int limit) {
    int max = Math.max(1, Math.min(2000, limit));
    if (events.isEmpty()) return List.of();
    ArrayList<RuntimeDiagnosticEvent> out = new ArrayList<>(Math.min(max, events.size()));
    Iterator<RuntimeDiagnosticEvent> it = events.descendingIterator();
    while (it.hasNext() && out.size() < max) {
      out.add(it.next());
    }
    return List.copyOf(out);
  }

  private synchronized void appendEvent(
      Instant at, String level, String type, String summary, String details) {
    events.addLast(new RuntimeDiagnosticEvent(at, level, type, summary, details));
    while (events.size() > MAX_EVENTS) {
      events.removeFirst();
    }
  }

  private static String summarizeEvent(ApplicationEvent event) {
    if (event instanceof AvailabilityChangeEvent<?> availability) {
      return "Availability changed: " + Objects.toString(availability.getState(), "(unknown)");
    }
    if (event instanceof PayloadApplicationEvent<?> payloadEvent) {
      Object payload = payloadEvent.getPayload();
      String payloadType = payload == null ? "null" : payload.getClass().getSimpleName();
      String payloadText = payload == null ? "" : Objects.toString(payload, "").trim();
      if (payloadText.length() > 96) payloadText = payloadText.substring(0, 96) + "...";
      if (payloadText.isEmpty() || looksLikeIdentityToString(payloadText)) {
        return "Payload event: " + payloadType;
      }
      return "Payload event: " + payloadType + " - " + payloadText;
    }

    String simple = event.getClass().getSimpleName();
    if (simple == null || simple.isBlank()) simple = event.getClass().getName();
    return switch (simple) {
      case "ApplicationStartingEvent" -> "Application starting.";
      case "ApplicationEnvironmentPreparedEvent" -> "Environment prepared.";
      case "ApplicationContextInitializedEvent" -> "Application context initialized.";
      case "ApplicationPreparedEvent" -> "Application context prepared.";
      case "ApplicationStartedEvent" -> "Application started.";
      case "ApplicationReadyEvent" -> "Application ready.";
      case "ApplicationFailedEvent" -> "Application failed.";
      case "ContextRefreshedEvent" -> "Application context refreshed.";
      case "ContextClosedEvent" -> "Application context closed.";
      case "ContextStartedEvent" -> "Application context started.";
      case "ContextStoppedEvent" -> "Application context stopped.";
      default -> simple;
    };
  }

  private static String describeEvent(ApplicationEvent event) {
    StringBuilder out = new StringBuilder(1024);
    out.append("timestamp=")
        .append(TS_FMT.format(Instant.ofEpochMilli(event.getTimestamp())))
        .append('\n');

    Object source = event.getSource();
    out.append("sourceType=")
        .append(source == null ? "null" : source.getClass().getName())
        .append('\n');

    String sourceText = Objects.toString(source, "").trim();
    if (!sourceText.isEmpty() && !looksLikeIdentityToString(sourceText)) {
      if (sourceText.length() > 600) sourceText = sourceText.substring(0, 600) + "...";
      out.append("source=").append(sourceText).append('\n');
    }
    if (source instanceof ApplicationContext appCtx) {
      out.append("contextId=").append(Objects.toString(appCtx.getId(), "")).append('\n');
      out.append("contextDisplayName=")
          .append(Objects.toString(appCtx.getDisplayName(), ""))
          .append('\n');
    }

    if (event instanceof AvailabilityChangeEvent<?> availability) {
      out.append("availabilityState=")
          .append(Objects.toString(availability.getState(), "(unknown)"))
          .append('\n');
    }

    if (event instanceof PayloadApplicationEvent<?> payloadEvent) {
      Object payload = payloadEvent.getPayload();
      out.append("payloadType=")
          .append(payload == null ? "null" : payload.getClass().getName())
          .append('\n');
      String payloadText = Objects.toString(payload, "").trim();
      if (!payloadText.isEmpty()) {
        if (payloadText.length() > 1000) payloadText = payloadText.substring(0, 1000) + "...";
        out.append("payload=").append(payloadText).append('\n');
      }
      if (payload instanceof Throwable throwable) {
        out.append('\n').append(stackTrace(throwable));
      }
    }

    if (source instanceof Throwable throwable) {
      out.append('\n').append(stackTrace(throwable));
    }

    return out.toString();
  }

  private static boolean containsErrorSignal(
      ApplicationEvent event, String summary, String details) {
    if (event instanceof PayloadApplicationEvent<?> payloadEvent
        && payloadEvent.getPayload() instanceof Throwable) {
      return true;
    }
    if (event.getSource() instanceof Throwable) return true;
    String s =
        (Objects.toString(summary, "") + "\n" + Objects.toString(details, ""))
            .toLowerCase(Locale.ROOT);
    return s.contains("exception") || s.contains("error") || s.contains("failed");
  }

  private static boolean containsWarningSignal(String summary) {
    String s = Objects.toString(summary, "").toLowerCase(Locale.ROOT);
    return s.contains("warn");
  }

  private static String stackTrace(Throwable t) {
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  private static boolean looksLikeIdentityToString(String text) {
    String s = Objects.toString(text, "").trim();
    if (s.isEmpty()) return false;
    int at = s.lastIndexOf('@');
    if (at <= 0 || at >= (s.length() - 1)) return false;
    String suffix = s.substring(at + 1);
    for (int i = 0; i < suffix.length(); i++) {
      char c = suffix.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) return false;
    }
    return true;
  }
}
