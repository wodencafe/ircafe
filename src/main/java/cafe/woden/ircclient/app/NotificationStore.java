package cafe.woden.ircclient.app;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** In-memory store of per-server "highlight" notifications. */
@Component
@ApplicationLayer
public class NotificationStore {

  /**
   * A single highlight/mention event.
   *
   * @param serverId server identifier
   * @param channel channel name (e.g. #libera)
   * @param fromNick nick that triggered the highlight
   * @param at timestamp (Instant)
   */
  public record HighlightEvent(String serverId, String channel, String fromNick, Instant at) {}

  /**
   * A rule match event (WORD/REGEX) from a channel message/action.
   *
   * @param serverId server identifier
   * @param channel channel name (e.g. #libera)
   * @param fromNick nick that triggered the notification
   * @param ruleLabel configured rule label
   * @param snippet short excerpt of the message around the match
   * @param at timestamp (Instant)
   */
  public record RuleMatchEvent(
      String serverId,
      String channel,
      String fromNick,
      String ruleLabel,
      String snippet,
      Instant at) {}

  /** A configured IRC event notification entry (kick/invite/mode/etc). */
  public record IrcEventRuleEvent(
      String serverId, String channel, String fromNick, String title, String body, Instant at) {}

  /** Notification store update signal (used by the UI to refresh). */
  public record Change(String serverId) {}

  /** Hard cap to prevent unbounded memory growth. */
  public static final int DEFAULT_MAX_EVENTS_PER_SERVER = 2000;

  /** Default cooldown to avoid spamming rule-match notifications. */
  public static final int DEFAULT_RULE_MATCH_COOLDOWN_SECONDS = 15;

  private final int maxEventsPerServer;
  private final UiSettingsPort uiSettingsPort;

  private final ConcurrentHashMap<String, List<HighlightEvent>> eventsByServer =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, List<RuleMatchEvent>> ruleEventsByServer =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, List<IrcEventRuleEvent>> ircEventRuleEventsByServer =
      new ConcurrentHashMap<>();

  private record RuleMatchKey(String serverId, String channel, String ruleLabel) {}

  private final ConcurrentHashMap<RuleMatchKey, Instant> lastRuleMatchAt =
      new ConcurrentHashMap<>();

  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public NotificationStore() {
    this(null, DEFAULT_MAX_EVENTS_PER_SERVER);
  }

  public NotificationStore(int maxEventsPerServer) {
    this(null, maxEventsPerServer);
  }

  @Autowired
  public NotificationStore(UiSettingsPort uiSettingsPort) {
    this(uiSettingsPort, DEFAULT_MAX_EVENTS_PER_SERVER);
  }

  public NotificationStore(UiSettingsPort uiSettingsPort, int maxEventsPerServer) {
    this.uiSettingsPort = uiSettingsPort;
    this.maxEventsPerServer = Math.max(50, maxEventsPerServer);
  }

  /** Emits a signal whenever notifications change for a server. */
  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  /** Record a new highlight event. */
  public void recordHighlight(TargetRef channelTarget, String fromNick) {
    if (channelTarget == null) return;
    if (channelTarget.isUiOnly()) return;
    if (!channelTarget.isChannel()) return;

    String sid = normalizeServerId(channelTarget.serverId());
    if (sid.isEmpty()) return;

    String channel = normalizeChannel(channelTarget.target());
    if (channel.isEmpty()) return;

    String nick = normalizeNick(fromNick);
    Instant now = Instant.now();

    List<HighlightEvent> list =
        eventsByServer.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()));

    synchronized (list) {
      list.add(new HighlightEvent(sid, channel, nick, now));
      // Enforce cap (drop oldest first).
      int overflow = list.size() - maxEventsPerServer;
      if (overflow > 0) {
        list.subList(0, overflow).clear();
      }
    }

    changes.onNext(new Change(sid));
  }

  /** Record a new rule match event. */
  public void recordRuleMatch(
      TargetRef channelTarget, String fromNick, String ruleLabel, String snippet) {
    if (channelTarget == null) return;
    if (channelTarget.isUiOnly()) return;
    if (!channelTarget.isChannel()) return;

    String sid = normalizeServerId(channelTarget.serverId());
    if (sid.isEmpty()) return;

    String channel = normalizeChannel(channelTarget.target());
    if (channel.isEmpty()) return;

    String nick = normalizeNick(fromNick);
    String label = normalizeLabel(ruleLabel);
    String snip = normalizeSnippet(snippet);
    Instant now = Instant.now();

    RuleMatchKey key =
        new RuleMatchKey(
            sid.toLowerCase(Locale.ROOT),
            channel.toLowerCase(Locale.ROOT),
            label.toLowerCase(Locale.ROOT));

    if (!allowRuleMatch(key, now)) {
      return;
    }

    List<RuleMatchEvent> list =
        ruleEventsByServer.computeIfAbsent(
            sid, k -> Collections.synchronizedList(new ArrayList<>()));

    synchronized (list) {
      list.add(new RuleMatchEvent(sid, channel, nick, label, snip, now));
      int overflow = list.size() - maxEventsPerServer;
      if (overflow > 0) {
        list.subList(0, overflow).clear();
      }
    }

    changes.onNext(new Change(sid));
  }

  /** Record a configured IRC event notification for the Notifications node. */
  public void recordIrcEvent(
      String serverId, String target, String fromNick, String title, String body) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    String chan = normalizeChannel(target);
    if (chan.isEmpty()) chan = "status";

    String nick = normalizeNick(fromNick);
    String normalizedTitle = normalizeLabel(title);
    String normalizedBody = normalizeSnippet(body);
    Instant now = Instant.now();

    List<IrcEventRuleEvent> list =
        ircEventRuleEventsByServer.computeIfAbsent(
            sid, k -> Collections.synchronizedList(new ArrayList<>()));

    synchronized (list) {
      list.add(new IrcEventRuleEvent(sid, chan, nick, normalizedTitle, normalizedBody, now));
      int overflow = list.size() - maxEventsPerServer;
      if (overflow > 0) {
        list.subList(0, overflow).clear();
      }
    }

    changes.onNext(new Change(sid));
  }

  /** Returns a defensive copy of all highlight events for a server, oldest to newest. */
  public List<HighlightEvent> listAll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /** Returns a defensive copy of all rule-match events for a server, oldest to newest. */
  public List<RuleMatchEvent> listAllRuleMatches(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<RuleMatchEvent> list = ruleEventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /**
   * Returns a defensive copy of all configured IRC event notifications for a server, oldest to
   * newest.
   */
  public List<IrcEventRuleEvent> listAllIrcEventRules(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<IrcEventRuleEvent> list = ircEventRuleEventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /** Returns up to {@code max} most recent highlight events for a server (newest last). */
  public List<HighlightEvent> listRecent(String serverId, int max) {
    if (max <= 0) return List.of();
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      int n = list.size();
      int from = Math.max(0, n - max);
      return List.copyOf(list.subList(from, n));
    }
  }

  public int count(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    int total = 0;

    List<HighlightEvent> highlights = eventsByServer.get(sid);
    if (highlights != null) {
      synchronized (highlights) {
        total += highlights.size();
      }
    }

    List<RuleMatchEvent> rules = ruleEventsByServer.get(sid);
    if (rules != null) {
      synchronized (rules) {
        total += rules.size();
      }
    }

    List<IrcEventRuleEvent> ircEvents = ircEventRuleEventsByServer.get(sid);
    if (ircEvents != null) {
      synchronized (ircEvents) {
        total += ircEvents.size();
      }
    }

    return total;
  }

  /** Clears all highlight events for a specific channel on a server. */
  public void clearChannel(TargetRef channelTarget) {
    if (channelTarget == null) return;
    if (channelTarget.isUiOnly()) return;
    if (!channelTarget.isChannel()) return;

    String sid = normalizeServerId(channelTarget.serverId());
    if (sid.isEmpty()) return;
    String channel = normalizeChannel(channelTarget.target());
    if (channel.isEmpty()) return;

    boolean changed = false;

    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list != null) {
      synchronized (list) {
        changed = list.removeIf(ev -> ev != null && channel.equalsIgnoreCase(ev.channel()));
      }
    }
    List<RuleMatchEvent> rules = ruleEventsByServer.get(sid);
    if (rules != null) {
      synchronized (rules) {
        changed |= rules.removeIf(ev -> ev != null && channel.equalsIgnoreCase(ev.channel()));
      }
    }

    List<IrcEventRuleEvent> ircEvents = ircEventRuleEventsByServer.get(sid);
    if (ircEvents != null) {
      synchronized (ircEvents) {
        changed |= ircEvents.removeIf(ev -> ev != null && channel.equalsIgnoreCase(ev.channel()));
      }
    }
    clearRuleMatchCooldownForChannel(sid, channel);

    if (changed) {
      changes.onNext(new Change(sid));
    }
  }

  /** Clears all highlight events for a server. */
  public void clearServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list != null) {
      synchronized (list) {
        list.clear();
      }
    }

    List<RuleMatchEvent> rules = ruleEventsByServer.get(sid);
    if (rules != null) {
      synchronized (rules) {
        rules.clear();
      }
    }

    List<IrcEventRuleEvent> ircEvents = ircEventRuleEventsByServer.get(sid);
    if (ircEvents != null) {
      synchronized (ircEvents) {
        ircEvents.clear();
      }
    }

    clearRuleMatchCooldownForServer(sid);
    changes.onNext(new Change(sid));
  }

  private int currentRuleMatchCooldownSeconds() {
    try {
      if (uiSettingsPort == null) return DEFAULT_RULE_MATCH_COOLDOWN_SECONDS;
      int v = uiSettingsPort.get().notificationRuleCooldownSeconds();
      // Allow 0 to mean "no cooldown".
      if (v < 0) return DEFAULT_RULE_MATCH_COOLDOWN_SECONDS;
      if (v > 3600) return 3600;
      return v;
    } catch (Exception ignored) {
      return DEFAULT_RULE_MATCH_COOLDOWN_SECONDS;
    }
  }

  private boolean allowRuleMatch(RuleMatchKey key, Instant now) {
    if (key == null || now == null) return false;

    long cooldownMs = (long) currentRuleMatchCooldownSeconds() * 1000L;
    final boolean[] allowed = new boolean[] {false};

    lastRuleMatchAt.compute(
        key,
        (k, prev) -> {
          if (prev != null && (now.toEpochMilli() - prev.toEpochMilli()) < cooldownMs) {
            return prev;
          }
          allowed[0] = true;
          return now;
        });

    return allowed[0];
  }

  private void clearRuleMatchCooldownForChannel(String serverId, String channel) {
    String sid = normalizeServerId(serverId);
    String chan = normalizeChannel(channel);
    if (sid.isEmpty() || chan.isEmpty()) return;

    String sidKey = sid.toLowerCase(Locale.ROOT);
    String chanKey = chan.toLowerCase(Locale.ROOT);

    lastRuleMatchAt
        .keySet()
        .removeIf(k -> k != null && sidKey.equals(k.serverId()) && chanKey.equals(k.channel()));
  }

  private void clearRuleMatchCooldownForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    String sidKey = sid.toLowerCase(Locale.ROOT);
    lastRuleMatchAt.keySet().removeIf(k -> k != null && sidKey.equals(k.serverId()));
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeChannel(String channel) {
    return Objects.toString(channel, "").trim();
  }

  private static String normalizeNick(String nick) {
    String s = Objects.toString(nick, "").trim();
    return s.isEmpty() ? "?" : s;
  }

  private static String normalizeLabel(String label) {
    String s = Objects.toString(label, "").trim();
    return s.isEmpty() ? "(rule)" : s;
  }

  private static String normalizeSnippet(String snippet) {
    String s = Objects.toString(snippet, "").trim();
    if (s.isEmpty()) return "";
    // Keep this conservative; the producer should already truncate.
    if (s.length() > 400) {
      return s.substring(0, 399) + "…";
    }
    return s;
  }
}
