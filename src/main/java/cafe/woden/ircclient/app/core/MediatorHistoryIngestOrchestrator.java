package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState.QueryMode;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Orchestrates history batch persistence side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
public class MediatorHistoryIngestOrchestrator {
  private static final Duration LIVE_REQUEST_MAX_AGE = Duration.ofMinutes(2);
  private static final String HISTORY_STATUS_TAG = "(history)";
  private static final int HISTORY_RENDER_DEDUP_MAX = 20_000;
  private static final long HISTORY_RENDER_DEDUP_TTL_MS = 10L * 60L * 1000L;
  private static final long TEXT_FINGERPRINT_SEED = 0xcbf29ce484222325L;
  private static final long TEXT_FINGERPRINT_PRIME = 0x100000001b3L;

  private final UiPort ui;
  private final ChatHistoryIngestionPort chatHistoryIngestionPort;
  private final ChatHistoryIngestEventsPort chatHistoryIngestEventsPort;
  private final ChatHistoryBatchEventsPort chatHistoryBatchEventsPort;
  private final ZncPlaybackEventsPort zncPlaybackEventsPort;
  private final ChatHistoryRequestRoutingState chatHistoryRequestRoutingState;
  private final ChatTranscriptHistoryPort transcripts;
  private final IrcClientService irc;
  private final Cache<HistoryRenderKey, Boolean> recentRenderedHistory =
      Caffeine.newBuilder()
          .maximumSize(HISTORY_RENDER_DEDUP_MAX)
          .expireAfterAccess(Duration.ofMillis(HISTORY_RENDER_DEDUP_TTL_MS))
          .build();

  public MediatorHistoryIngestOrchestrator(
      UiPort ui,
      ChatHistoryIngestionPort chatHistoryIngestionPort,
      ChatHistoryIngestEventsPort chatHistoryIngestEventsPort,
      ChatHistoryBatchEventsPort chatHistoryBatchEventsPort,
      ZncPlaybackEventsPort zncPlaybackEventsPort,
      ChatHistoryRequestRoutingState chatHistoryRequestRoutingState,
      ChatTranscriptHistoryPort transcripts,
      IrcClientService irc) {
    this.ui = ui;
    this.chatHistoryIngestionPort = chatHistoryIngestionPort;
    this.chatHistoryIngestEventsPort = chatHistoryIngestEventsPort;
    this.chatHistoryBatchEventsPort = chatHistoryBatchEventsPort;
    this.zncPlaybackEventsPort = zncPlaybackEventsPort;
    this.chatHistoryRequestRoutingState = chatHistoryRequestRoutingState;
    this.transcripts = transcripts;
    this.irc = irc;
  }

  public void onChatHistoryBatchReceived(String sid, IrcEvent.ChatHistoryBatchReceived ev) {
    String target = normalizeTarget(ev.target());
    final TargetRef dest = new TargetRef(sid, target);
    ensureTargetExists(dest);

    try {
      long earliest = earliestEpochMs(ev.entries());
      long latest = latestEpochMs(ev.entries());
      chatHistoryBatchEventsPort.publish(
          new ChatHistoryBatchEventsPort.BatchEvent(
              sid, target, ev.batchId(), ev.entries(), earliest, latest));
    } catch (Exception ignored) {
    }

    int n = (ev.entries() == null) ? 0 : ev.entries().size();
    LiveRenderResult live = renderRequestedBatchIfAny(sid, target, dest, ev.entries());
    if (live.displayedCount() > 0) {
      ui.appendStatus(
          live.target(),
          HISTORY_STATUS_TAG,
          "Received "
              + n
              + " history lines (batch "
              + ev.batchId()
              + "). Displayed "
              + live.displayedCount()
              + " now; persisting in background.");
    } else {
      ui.appendStatus(
          dest,
          HISTORY_STATUS_TAG,
          "Received "
              + n
              + " history lines (batch "
              + ev.batchId()
              + "). Persisting for scrollback.");
    }

    chatHistoryIngestionPort.ingestAsync(
        sid,
        target,
        ev.batchId(),
        ev.entries(),
        result -> {
          if (result == null) {
            appendStatus(dest, HISTORY_STATUS_TAG, "Persist finished (no details).");
            return;
          }

          String msg;
          if (!result.enabled()) {
            msg = "History batch not persisted: chat logging is disabled.";
          } else if (result.message() != null) {
            msg = result.message();
          } else {
            msg = "Persisted " + result.inserted() + "/" + result.total() + " history lines.";
          }
          appendStatus(dest, HISTORY_STATUS_TAG, msg);

          try {
            chatHistoryIngestEventsPort.publish(
                new ChatHistoryIngestEventsPort.IngestEvent(
                    sid,
                    target,
                    ev.batchId(),
                    result.total(),
                    result.inserted(),
                    result.earliestInsertedEpochMs(),
                    result.latestInsertedEpochMs()));
          } catch (Exception ignored) {
          }
        });
  }

  public void onZncPlaybackBatchReceived(String sid, IrcEvent.ZncPlaybackBatchReceived ev) {
    String target = normalizeTarget(ev.target());
    final TargetRef dest = new TargetRef(sid, target);
    ensureTargetExists(dest);

    // Deterministic batch id so the DB-backed history service can wait for ingest.
    final String batchId =
        "znc-playback:"
            + (ev.fromInclusive() == null ? 0L : ev.fromInclusive().toEpochMilli())
            + "-"
            + (ev.toInclusive() == null ? 0L : ev.toInclusive().toEpochMilli());

    try {
      long earliest = earliestEpochMs(ev.entries());
      long latest = latestEpochMs(ev.entries());
      zncPlaybackEventsPort.publish(
          new ZncPlaybackEventsPort.PlaybackEvent(
              sid, target, ev.fromInclusive(), ev.toInclusive(), ev.entries(), earliest, latest));
    } catch (Exception ignored) {
    }

    int n = (ev.entries() == null) ? 0 : ev.entries().size();
    LiveRenderResult live = renderRequestedBatchIfAny(sid, target, dest, ev.entries());
    if (live.displayedCount() > 0) {
      ui.appendStatus(
          live.target(),
          HISTORY_STATUS_TAG,
          "Received "
              + n
              + " playback history lines for "
              + target
              + ". Displayed "
              + live.displayedCount()
              + " now; persisting in background.");
    } else if (n > 0) {
      ui.appendStatus(
          dest,
          HISTORY_STATUS_TAG,
          "Received " + n + " playback history lines. Persisting for scrollback.");
    }

    chatHistoryIngestionPort.ingestAsync(
        sid,
        target,
        batchId,
        ev.entries(),
        result -> {
          if (result == null) {
            appendStatus(dest, HISTORY_STATUS_TAG, "Persist finished (no details).");
            return;
          }

          String msg;
          if (!result.enabled()) {
            msg = "History batch not persisted: chat logging is disabled.";
          } else if (result.message() != null) {
            msg = result.message();
          } else {
            msg = "Persisted " + result.inserted() + "/" + result.total() + " history lines.";
          }
          appendStatus(dest, HISTORY_STATUS_TAG, msg);

          try {
            chatHistoryIngestEventsPort.publish(
                new ChatHistoryIngestEventsPort.IngestEvent(
                    sid,
                    target,
                    batchId,
                    result.total(),
                    result.inserted(),
                    result.earliestInsertedEpochMs(),
                    result.latestInsertedEpochMs()));
          } catch (Exception ignored) {
          }
        });
  }

  private static String normalizeTarget(String target) {
    if (target == null || target.isBlank()) return "status";
    return target;
  }

  private LiveRenderResult renderRequestedBatchIfAny(
      String sid,
      String normalizedTarget,
      TargetRef fallbackTarget,
      List<ChatHistoryEntry> entries) {
    ChatHistoryRequestRoutingState.PendingRequest pending =
        chatHistoryRequestRoutingState.consumeIfFresh(sid, normalizedTarget, LIVE_REQUEST_MAX_AGE);
    if (pending == null || pending.originTarget() == null) {
      return new LiveRenderResult(fallbackTarget, 0);
    }

    TargetRef renderTarget =
        normalizeRenderTarget(sid, normalizedTarget, pending.originTarget(), fallbackTarget);
    ensureTargetExists(renderTarget);
    List<ChatHistoryEntry> ordered = orderedEntries(entries);
    if (ordered.isEmpty()) {
      return new LiveRenderResult(renderTarget, 0);
    }

    String myNick = "";
    try {
      myNick = irc.currentNick(sid).orElse("");
    } catch (Exception ignored) {
      myNick = "";
    }
    Set<HistoryFingerprint> seen = new HashSet<>();
    QueryMode mode = pending.queryMode() == null ? QueryMode.BEFORE : pending.queryMode();
    int displayed = 0;
    transcripts.beginHistoryInsertBatch(renderTarget);
    try {
      int insertAt =
          (mode == QueryMode.BEFORE) ? transcripts.loadOlderInsertOffset(renderTarget) : 0;
      for (ChatHistoryEntry entry : ordered) {
        HistoryFingerprint fp = HistoryFingerprint.from(entry);
        if (!seen.add(fp)) {
          continue;
        }
        if (alreadyRenderedRecently(renderTarget, fp)) {
          continue;
        }

        String from = Objects.toString(entry.from(), "");
        String text = Objects.toString(entry.text(), "");
        long tsEpochMs =
            entry.at() == null ? System.currentTimeMillis() : entry.at().toEpochMilli();
        boolean outgoing = !myNick.isBlank() && !from.isBlank() && from.equalsIgnoreCase(myNick);

        ChatHistoryEntry.Kind kind =
            (entry.kind() == null) ? ChatHistoryEntry.Kind.PRIVMSG : entry.kind();
        if (mode == QueryMode.BEFORE) {
          insertAt =
              switch (kind) {
                case ACTION ->
                    transcripts.insertActionFromHistoryAt(
                        renderTarget, insertAt, from, text, outgoing, tsEpochMs);
                case NOTICE ->
                    transcripts.insertNoticeFromHistoryAt(
                        renderTarget, insertAt, from, text, tsEpochMs);
                case PRIVMSG ->
                    transcripts.insertChatFromHistoryAt(
                        renderTarget, insertAt, from, text, outgoing, tsEpochMs);
              };
        } else {
          switch (kind) {
            case ACTION ->
                transcripts.appendActionFromHistory(renderTarget, from, text, outgoing, tsEpochMs);
            case NOTICE -> transcripts.appendNoticeFromHistory(renderTarget, from, text, tsEpochMs);
            case PRIVMSG ->
                transcripts.appendChatFromHistory(renderTarget, from, text, outgoing, tsEpochMs);
          }
        }
        displayed++;
      }
    } finally {
      transcripts.endHistoryInsertBatch(renderTarget);
    }

    return new LiveRenderResult(renderTarget, displayed);
  }

  private static List<ChatHistoryEntry> orderedEntries(List<ChatHistoryEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    List<ChatHistoryEntry> ordered = new ArrayList<>(entries.size());
    for (ChatHistoryEntry entry : entries) {
      if (entry != null) ordered.add(entry);
    }
    if (ordered.size() <= 1) return ordered;
    ordered.sort(Comparator.comparing(ChatHistoryEntry::at));
    return ordered;
  }

  private static TargetRef normalizeRenderTarget(
      String sid, String normalizedTarget, TargetRef origin, TargetRef fallbackTarget) {
    if (origin == null) return fallbackTarget;
    if (!Objects.equals(origin.serverId(), sid)) {
      return new TargetRef(sid, normalizedTarget);
    }
    if (origin.isStatus() || origin.isUiOnly()) {
      return new TargetRef(sid, normalizedTarget);
    }
    if (!Objects.equals(
        origin.target().toLowerCase(Locale.ROOT), normalizedTarget.toLowerCase(Locale.ROOT))) {
      return new TargetRef(sid, normalizedTarget);
    }
    return origin;
  }

  private void appendStatus(TargetRef dest, String tag, String msg) {
    ensureTargetExists(dest);
    ui.appendStatus(dest, tag, msg);
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private static long earliestEpochMs(List<ChatHistoryEntry> entries) {
    if (entries == null || entries.isEmpty()) return 0L;
    return entries.stream().mapToLong(entry -> entry.at().toEpochMilli()).min().orElse(0L);
  }

  private static long latestEpochMs(List<ChatHistoryEntry> entries) {
    if (entries == null || entries.isEmpty()) return 0L;
    return entries.stream().mapToLong(entry -> entry.at().toEpochMilli()).max().orElse(0L);
  }

  private boolean alreadyRenderedRecently(TargetRef target, HistoryFingerprint fp) {
    if (target == null || fp == null) return false;
    HistoryRenderKey key = new HistoryRenderKey(target, fp);
    return recentRenderedHistory.asMap().putIfAbsent(key, Boolean.TRUE) != null;
  }

  private record LiveRenderResult(TargetRef target, int displayedCount) {}

  private record HistoryRenderKey(TargetRef target, HistoryFingerprint fingerprint) {}

  private record HistoryFingerprint(
      long tsEpochMs, ChatHistoryEntry.Kind kind, String from, long textFingerprint, int textLength) {
    static HistoryFingerprint from(ChatHistoryEntry entry) {
      if (entry == null) {
        return new HistoryFingerprint(0L, ChatHistoryEntry.Kind.PRIVMSG, "", 0L, 0);
      }
      long ts = entry.at() == null ? 0L : entry.at().toEpochMilli();
      ChatHistoryEntry.Kind k = entry.kind() == null ? ChatHistoryEntry.Kind.PRIVMSG : entry.kind();
      String text = Objects.toString(entry.text(), "");
      return new HistoryFingerprint(
          ts, k, Objects.toString(entry.from(), ""), computeTextFingerprint(text), text.length());
    }
  }

  private static long computeTextFingerprint(String text) {
    String value = Objects.toString(text, "");
    long hash = TEXT_FINGERPRINT_SEED;
    for (int i = 0; i < value.length(); i++) {
      hash ^= value.charAt(i);
      hash *= TEXT_FINGERPRINT_PRIME;
    }
    return hash;
  }
}
