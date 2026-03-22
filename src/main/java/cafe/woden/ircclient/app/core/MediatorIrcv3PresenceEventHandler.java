package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Coordinates IRCv3 typing and read-marker side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorIrcv3PresenceEventHandler {
  private static final Logger log =
      LoggerFactory.getLogger(MediatorIrcv3PresenceEventHandler.class);
  private static final long TYPING_LOG_DEDUP_MS = 5_000;
  private static final int TYPING_LOG_MAX_KEYS = 512;

  interface Callbacks {
    boolean isFromSelf(String serverId, String from);

    void markPrivateMessagePeerOnline(String serverId, String nick);

    TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status);

    TargetRef resolveActiveOrStatus(String sid, TargetRef status);
  }

  private record TypingLogState(String state, long atMs) {}

  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort irc;

  private final IrcTypingPort typingPort;
  private final IrcReadMarkerPort readMarkerPort;
  private final UiPort ui;
  private final UiSettingsPort uiSettingsPort;
  private final Map<String, TypingLogState> lastTypingByKey = new ConcurrentHashMap<>();

  public void handleUserTypingObserved(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.UserTypingObserved event) {
    if (callbacks.isFromSelf(sid, event.from())) {
      return;
    }
    callbacks.markPrivateMessagePeerOnline(sid, event.from());
    TargetRef dest = callbacks.resolveIrcv3Target(sid, event.target(), event.from(), status);
    String from = Objects.toString(event.from(), "").trim();
    if (from.isEmpty()) {
      from = "Someone";
    }
    String state = Objects.toString(event.state(), "").trim().toLowerCase(java.util.Locale.ROOT);
    if (state.isEmpty()) {
      state = "active";
    }

    boolean receiveEnabled = false;
    boolean treeDisplayEnabled = false;
    boolean usersListDisplayEnabled = false;
    boolean transcriptDisplayEnabled = false;
    try {
      var uiSettings = uiSettingsPort.get();
      if (uiSettings != null) {
        receiveEnabled = uiSettings.typingIndicatorsReceiveEnabled();
        treeDisplayEnabled = uiSettings.typingIndicatorsTreeEnabled();
        usersListDisplayEnabled = uiSettings.typingIndicatorsUsersListEnabled();
        transcriptDisplayEnabled = uiSettings.typingIndicatorsTranscriptEnabled();
      }
    } catch (Exception ignored) {
    }
    boolean typingAvailable = false;
    try {
      typingAvailable = typingPort.isTypingAvailable(sid);
    } catch (Exception ignored) {
    }
    maybeLogTypingObserved(
        sid, Objects.toString(event.target(), ""), from, state, receiveEnabled, typingAvailable);

    if (receiveEnabled && transcriptDisplayEnabled) {
      ui.showTypingIndicator(dest, from, state);
    }
    if (receiveEnabled && treeDisplayEnabled) {
      ui.showTypingActivity(dest, state);
    }
    if (receiveEnabled && usersListDisplayEnabled) {
      ui.showUsersTypingIndicator(dest, from, state);
    }
  }

  public void handleReadMarkerObserved(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ReadMarkerObserved event) {
    if (!readMarkerPort.isReadMarkerAvailable(sid)) {
      return;
    }
    if (!shouldApplyReadMarkerEvent(callbacks, sid, event.from())) {
      return;
    }
    TargetRef dest = resolveReadMarkerTarget(callbacks, sid, event.target(), status);
    long markerEpochMs = parseReadMarkerEpochMs(event.marker(), event.at());
    ui.setReadMarker(dest, markerEpochMs);
    ui.clearUnread(dest);
  }

  private boolean shouldApplyReadMarkerEvent(Callbacks callbacks, String sid, String from) {
    String source = Objects.toString(from, "").trim();
    if (source.isEmpty() || "server".equalsIgnoreCase(source)) {
      return true;
    }
    return callbacks.isFromSelf(sid, source);
  }

  private TargetRef resolveReadMarkerTarget(
      Callbacks callbacks, String sid, String target, TargetRef status) {
    String rawTarget = Objects.toString(target, "").trim();
    if (!rawTarget.isEmpty()) {
      String me = irc.currentNick(sid).orElse("");
      if (me.isBlank() || !rawTarget.equalsIgnoreCase(me)) {
        return new TargetRef(sid, rawTarget);
      }
    }
    return callbacks.resolveActiveOrStatus(sid, status);
  }

  private static long parseReadMarkerEpochMs(String marker, Instant fallbackAt) {
    Instant fallback = fallbackAt != null ? fallbackAt : Instant.now();
    String raw = Objects.toString(marker, "").trim();
    if (raw.isEmpty() || "*".equals(raw)) {
      return 0L;
    }

    String value = raw;
    int eq = raw.indexOf('=');
    if (eq > 0 && eq < (raw.length() - 1)) {
      String key = raw.substring(0, eq).trim();
      if ("timestamp".equalsIgnoreCase(key)) {
        value = raw.substring(eq + 1).trim();
      }
    }
    if (value.isEmpty() || "*".equals(value)) {
      return 0L;
    }

    try {
      return Instant.parse(value).toEpochMilli();
    } catch (Exception ignored) {
    }

    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        return fallback.toEpochMilli();
      }
      if (value.length() <= 10) {
        return Math.multiplyExact(parsed, 1000L);
      }
      return parsed;
    } catch (Exception ignored) {
      return fallback.toEpochMilli();
    }
  }

  private void maybeLogTypingObserved(
      String serverId,
      String rawTarget,
      String from,
      String state,
      boolean prefEnabled,
      boolean typingAvailable) {
    if (!log.isInfoEnabled()) {
      return;
    }

    String sid = Objects.toString(serverId, "").trim();
    String target = Objects.toString(rawTarget, "").trim();
    String nick = Objects.toString(from, "").trim();
    String normalizedState = Objects.toString(state, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || nick.isEmpty() || normalizedState.isEmpty()) {
      return;
    }

    String key = sid + "|" + target + "|" + nick;
    long now = System.currentTimeMillis();

    TypingLogState previous = lastTypingByKey.get(key);
    boolean stateChanged = previous == null || !Objects.equals(previous.state(), normalizedState);
    boolean stale = previous == null || (now - previous.atMs()) >= TYPING_LOG_DEDUP_MS;

    if (lastTypingByKey.size() > TYPING_LOG_MAX_KEYS) {
      lastTypingByKey.clear();
    }

    if (stateChanged || stale || "done".equals(normalizedState)) {
      lastTypingByKey.put(key, new TypingLogState(normalizedState, now));
      log.info(
          "[{}] typing observed: from={} target={} state={} (prefsEnabled={} typingAvailable={})",
          sid,
          nick,
          target.isEmpty() ? "(unknown)" : target,
          normalizedState,
          prefEnabled,
          typingAvailable);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "[{}] typing observed (repeat): from={} target={} state={}",
          sid,
          nick,
          target.isEmpty() ? "(unknown)" : target,
          normalizedState);
    }
  }
}
