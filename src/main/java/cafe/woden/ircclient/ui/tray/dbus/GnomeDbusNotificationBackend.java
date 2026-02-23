package cafe.woden.ircclient.ui.tray.dbus;

import cafe.woden.ircclient.config.ExecutorConfig;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * GNOME/Linux notification backend using D-Bus (org.freedesktop.Notifications).
 *
 * <p>G1: Probe reachability + capabilities.
 * <p>G2: Send notifications via Notify().
 * <p>G3: Listen for ActionInvoked/NotificationClosed and dispatch our click handler.
 */
@Component
public class GnomeDbusNotificationBackend {
  private static final Logger log = LoggerFactory.getLogger(GnomeDbusNotificationBackend.class);

  // We keep the probe cached; clicking support doesn't change often mid-session.
  private static final Duration PROBE_TTL = Duration.ofMinutes(5);
  private final AtomicReference<ProbeResult> cached = new AtomicReference<>();

  // Live signal handling state (G3).
  private final AtomicBoolean handlersInstalled = new AtomicBoolean(false);
  private final Map<Long, ClickEntry> clickHandlers = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanup;

  private volatile DBusConnection liveConn;
  private volatile FreedesktopNotifications liveSvc;
  private volatile Boolean liveActionsSupported;

  public GnomeDbusNotificationBackend(
      @Qualifier(ExecutorConfig.GNOME_DBUS_CLEANUP_SCHEDULER)
      ScheduledExecutorService cleanup) {
    this.cleanup = cleanup;
  }

  /**
   * Sends a notification via D-Bus and registers a click handler.
   *
   * <p>We use the "default" action key so a body click triggers ActionInvoked("default") on
   * servers that support actions.
   */
  public NotifyResult notifyWithDefaultAction(String summary, String body, int expireMs, Runnable onDefaultClick) {
    if (!ensureLiveConnection()) {
      return NotifyResult.failed("DBus session bus not reachable");
    }
    if (Boolean.FALSE.equals(liveActionsSupported)) {
      return NotifyResult.failed("Notification server does not support actions");
    }

    // Minimal default-click action. Some servers won't show a button for "default", but still emit
    // ActionInvoked("default") when the notification body is clicked.
    String[] actions = new String[] {"default", "Open"};

    Map<String, Variant<?>> hints = new HashMap<>();
    // 0=low, 1=normal, 2=critical
    hints.put("urgency", new Variant<>((byte) 1));
    hints.put("transient", new Variant<>(Boolean.TRUE));
    // Helps some daemons group notifications.
    hints.put("desktop-entry", new Variant<>("ircafe"));

    try {
      UInt32 id = liveSvc.Notify(
          "IRCafe",
          new UInt32(0),
          "",
          Objects.toString(summary, ""),
          Objects.toString(body, ""),
          actions,
          hints,
          expireMs
      );
      long longId = id == null ? 0L : id.longValue();
      if (longId > 0 && onDefaultClick != null) {
        long deadlineMs = System.currentTimeMillis() + Math.max(10_000L, expireMs + 30_000L);
        clickHandlers.put(longId, new ClickEntry(onDefaultClick, deadlineMs));
        long delayMs = Math.max(0L, deadlineMs - System.currentTimeMillis());
        cleanup.schedule(() -> clickHandlers.remove(longId), delayMs, TimeUnit.MILLISECONDS);
      }
      return NotifyResult.sent(longId);
    } catch (Throwable t) {
      log.debug("[ircafe] DBus Notify() failed", t);
      // If the live connection got borked, drop it so the next call can re-open.
      resetLive();
      return NotifyResult.failed(t.toString());
    }
  }

  public ProbeResult probe() {
    ProbeResult existing = cached.get();
    if (existing != null && !existing.isExpired(PROBE_TTL)) {
      return existing;
    }

    ProbeResult next = doProbe();
    cached.set(next);
    return next;
  }

  public boolean isLinux() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("linux");
  }

  private ProbeResult doProbe() {
    if (!isLinux()) {
      return ProbeResult.unsupportedOs();
    }

    // Best-effort. If anything fails, we just mark it unavailable and fall back.
    try (DBusConnection conn = DBusConnectionBuilder.forSessionBus().build()) {
      FreedesktopNotifications svc = conn.getRemoteObject(
          "org.freedesktop.Notifications",
          "/org/freedesktop/Notifications",
          FreedesktopNotifications.class
      );

      List<String> caps;
      try {
        caps = svc.GetCapabilities();
      } catch (Throwable t) {
        // Some servers may deny capability calls; treat as reachable but unknown.
        log.debug("[ircafe] DBus notifications reachable but GetCapabilities failed", t);
        caps = Collections.emptyList();
      }

      boolean actions = caps.stream().filter(Objects::nonNull)
          .map(s -> s.toLowerCase(Locale.ROOT))
          .anyMatch("actions"::equals);

      return new ProbeResult(true, true, actions, caps, Instant.now(), null);
    } catch (Throwable t) {
      // No session bus / not in a graphical session / service missing, etc.
      return new ProbeResult(true, false, false, Collections.emptyList(), Instant.now(), t.toString());
    }
  }

  @PreDestroy
  void shutdown() {
    resetLive();
  }

  private boolean ensureLiveConnection() {
    if (!isLinux()) return false;

    try {
      if (liveConn == null || liveSvc == null) {
        synchronized (this) {
          if (liveConn == null || liveSvc == null) {
            liveConn = DBusConnectionBuilder.forSessionBus().build();
            liveSvc = liveConn.getRemoteObject(
                "org.freedesktop.Notifications",
                "/org/freedesktop/Notifications",
                FreedesktopNotifications.class
            );
            try {
              List<String> caps = liveSvc.GetCapabilities();
              boolean actions = caps != null && caps.stream().filter(Objects::nonNull)
                  .map(s -> s.toLowerCase(Locale.ROOT))
                  .anyMatch("actions"::equals);
              liveActionsSupported = actions;
            } catch (Throwable ignored) {
              // If capabilities can't be queried, assume no actions (safe default).
              liveActionsSupported = Boolean.FALSE;
            }

            installSignalHandlersOnce();
          }
        }
      }
      return true;
    } catch (Throwable t) {
      resetLive();
      return false;
    }
  }

  private void installSignalHandlersOnce() {
    if (liveConn == null) return;
    if (!handlersInstalled.compareAndSet(false, true)) return;

    try {
      liveConn.addSigHandler(FreedesktopNotifications.ActionInvoked.class,
          (DBusSigHandler<FreedesktopNotifications.ActionInvoked>) this::onActionInvoked);
      liveConn.addSigHandler(FreedesktopNotifications.NotificationClosed.class,
          (DBusSigHandler<FreedesktopNotifications.NotificationClosed>) this::onNotificationClosed);
    } catch (Throwable t) {
      // If we can't install handlers, we still allow Notify() (non-clickable).
      log.debug("[ircafe] DBus signal handler install failed", t);
    }
  }

  private void onActionInvoked(FreedesktopNotifications.ActionInvoked sig) {
    if (sig == null) return;
    long id = sig.id() == null ? 0L : sig.id().longValue();
    if (id <= 0L) return;

    String key = Objects.toString(sig.actionKey(), "").toLowerCase(Locale.ROOT);
    if (!(key.isBlank() || "default".equals(key) || "open".equals(key))) {
      // Unknown action key - still treat it like a click.
    }

    ClickEntry entry = clickHandlers.remove(id);
    if (entry == null) return;
    try {
      entry.onClick().run();
    } catch (Throwable ignored) {
    }
  }

  private void onNotificationClosed(FreedesktopNotifications.NotificationClosed sig) {
    if (sig == null) return;
    long id = sig.id() == null ? 0L : sig.id().longValue();
    if (id <= 0L) return;

    // Important: some daemons can emit NotificationClosed before ActionInvoked.
    // So we do NOT eagerly remove the mapping here.
    ClickEntry entry = clickHandlers.get(id);
    if (entry == null) return;

    long now = System.currentTimeMillis();
    long delay = Math.max(2_000L, entry.deadlineMs() - now);
    cleanup.schedule(() -> clickHandlers.remove(id), delay, TimeUnit.MILLISECONDS);
  }

  private void resetLive() {
    try {
      if (liveConn != null) {
        liveConn.close();
      }
    } catch (Exception ignored) {
    } finally {
      liveConn = null;
      liveSvc = null;
      liveActionsSupported = null;
      handlersInstalled.set(false);
      clickHandlers.clear();
    }
  }

  private record ClickEntry(Runnable onClick, long deadlineMs) {
  }

  /**
   * Captures a single probe run.
   */
  public record ProbeResult(
      boolean linux,
      boolean sessionBusReachable,
      boolean actionsSupported,
      List<String> capabilities,
      Instant probedAt,
      String error
  ) {
    static ProbeResult unsupportedOs() {
      return new ProbeResult(false, false, false, Collections.emptyList(), Instant.now(), null);
    }

    boolean isExpired(Duration ttl) {
      if (probedAt == null) return true;
      return probedAt.plus(ttl).isBefore(Instant.now());
    }
  }

  /**
   * Captures the outcome of a single Notify() attempt.
   */
  public record NotifyResult(boolean sent, long id, String error) {
    static NotifyResult sent(long id) {
      return new NotifyResult(true, id, null);
    }

    static NotifyResult failed(String error) {
      return new NotifyResult(false, 0L, error);
    }
  }
}
