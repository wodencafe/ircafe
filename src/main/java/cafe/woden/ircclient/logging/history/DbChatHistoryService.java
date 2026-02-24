package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.logging.model.LogRow;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads recent log lines from the database and replays them into the transcript when a target is selected. */
public final class DbChatHistoryService implements ChatHistoryService {

  private static final Logger log = LoggerFactory.getLogger(DbChatHistoryService.class);

  
  private static final int DEFAULT_PAGE_SIZE = 200;

  private static final DateTimeFormatter HISTORY_DIVIDER_DATE_FMT =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private final ChatLogRepository repo;
  private final LogProperties props;
  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;

  // Optional remote fill when DB runs out:
  //  - Prefer IRCv3 CHATHISTORY (soju / servers that support it)
  //  - Fall back to ZNC playback (znc.in/playback) when available
  private final IrcClientService irc;
  private final ChatHistoryIngestBus ingestBus;

  private static final Duration CHATHISTORY_REMOTE_FILL_TIMEOUT = Duration.ofSeconds(4);
  private static final Duration ZNC_PLAYBACK_REMOTE_FILL_TIMEOUT = Duration.ofSeconds(18);
  private static final Duration ZNC_PLAYBACK_WINDOW = Duration.ofHours(6);

  
  private final ConcurrentHashMap<TargetRef, LogCursor> oldestCursor = new ConcurrentHashMap<>();
  
  private final ConcurrentHashMap<TargetRef, Boolean> noMoreOlder = new ConcurrentHashMap<>();
  
  private final Set<TargetRef> loading = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private final ExecutorService exec;

  public DbChatHistoryService(ChatLogRepository repo,
                              LogProperties props,
                              ChatTranscriptStore transcripts,
                              UiSettingsBus settingsBus,
                              IrcClientService irc,
                              ChatHistoryIngestBus ingestBus,
                              ExecutorService exec) {
    this.repo = repo;
    this.props = props;
    this.transcripts = transcripts;
    this.settingsBus = settingsBus;
    this.irc = irc;
    this.ingestBus = ingestBus;
    this.exec = Objects.requireNonNull(exec, "exec");
  }

  /**
   * Filter which persisted lines are allowed to appear when replaying chat history.
   *
   * <p>Note: this is distinct from logging itself; it only affects history replay (prefill + paging).
   */
  private boolean shouldIncludeInHistory(LogLine line) {
    if (line == null) return false;
    if (line.softIgnored() && props != null && !props.logSoftIgnoredLines()) {
      return false;
    }

    LogKind kind = line.kind();
    if (kind == null) kind = LogKind.STATUS;
    if (kind == LogKind.STATUS) {
      String from = line.fromNick();
      if (from != null) {
        String f = from.trim().toLowerCase();
        if (f.equals("join") || f.equals("mode")) {
          return false;
        }
      }
    }

    return true;
  }

  private static String historyDividerLabel(long tsEpochMs) {
    String date;
    try {
      date = HISTORY_DIVIDER_DATE_FMT.format(Instant.ofEpochMilli(tsEpochMs).atZone(ZoneId.systemDefault()).toLocalDate());
    } catch (Exception ignored) {
      date = "unknown";
    }
    // Keep it simple: a single-line divider. (Component will be inserted as an embedded Swing widget.)
    return "────────  History — " + date + "  ────────";
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean canLoadOlder(TargetRef target) {
    if (target == null) return false;
    LogCursor c = oldestCursor.get(target);
    if (c == null) return false;
    if (Boolean.TRUE.equals(noMoreOlder.get(target))) return false;
    return !loading.contains(target);
  }

  @Override
  public void loadOlder(TargetRef target, int limit, Consumer<LoadOlderResult> callback) {
    if (callback == null) return;
    if (target == null || limit <= 0) {
      LogCursor c = target != null ? oldestCursor.get(target) : null;
      LogCursor safe = c != null ? c : new LogCursor(0, 0);
      SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(List.of(), safe, false)));
      return;
    }

    LogCursor cursor = oldestCursor.get(target);
    if (cursor == null || Boolean.TRUE.equals(noMoreOlder.get(target))) {
      LogCursor safe = cursor != null ? cursor : new LogCursor(0, 0);
      SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(List.of(), safe, false)));
      return;
    }

    // Guard: one in-flight load per target.
    if (!loading.add(target)) {
      SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(List.of(), cursor, true)));
      return;
    }

    final String serverId = target.serverId();
    final String tgt = target.target();
    final long beforeTs = cursor.tsEpochMs();
    final long beforeId = cursor.id();
    final int limitFinal = limit;

    exec.execute(() -> {
      try {
        ArrayList<LogRow> rows = new ArrayList<>();
        List<LogRow> first = repo.fetchOlderRows(serverId, tgt, beforeTs, beforeId, limitFinal);
        if (first != null && !first.isEmpty()) rows.addAll(first);

        // If nothing local, try a remote fill once, then re-query local.
        if (rows.isEmpty()) {
          boolean requested = requestRemoteHistoryAndWait(serverId, tgt, safeInstant(beforeTs), limitFinal);
          if (requested) {
            List<LogRow> after = repo.fetchOlderRows(serverId, tgt, beforeTs, beforeId, limitFinal);
            if (after != null && !after.isEmpty()) rows.addAll(after);
          }
        }

        if (rows.isEmpty()) {
          noMoreOlder.put(target, Boolean.TRUE);
          SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(List.of(), cursor, false)));
          return;
        }

        // Rows are newest-first. The oldest in this combined batch is the last.
        LogRow batchOldest = rows.get(rows.size() - 1);
        LogCursor newCursor = new LogCursor(batchOldest.line().tsEpochMs(), batchOldest.id());

        boolean hasMore = safeHasMore(serverId, tgt, newCursor);

        // If we hit the DB floor and still haven't filled the page, ask the bouncer for more.
        if (!hasMore && rows.size() < limitFinal) {
          int missing = Math.max(1, limitFinal - rows.size());
          boolean requested = requestRemoteHistoryAndWait(serverId, tgt, safeInstant(newCursor.tsEpochMs()), missing);
          if (requested) {
            List<LogRow> more = repo.fetchOlderRows(serverId, tgt, newCursor.tsEpochMs(), newCursor.id(), missing);
            if (more != null && !more.isEmpty()) {
              rows.addAll(more);
              LogRow combinedOldest = rows.get(rows.size() - 1);
              newCursor = new LogCursor(combinedOldest.line().tsEpochMs(), combinedOldest.id());
            }
            hasMore = safeHasMore(serverId, tgt, newCursor);
          }
        }

        // Advance cursor regardless of filtering, to avoid infinite loops.
        oldestCursor.put(target, newCursor);
        if (!hasMore) {
          noMoreOlder.put(target, Boolean.TRUE);
        } else {
          noMoreOlder.remove(target);
        }

        // Convert to chronological order (oldest-first).
        Collections.reverse(rows);
        List<LogLine> lines = new ArrayList<>(rows.size());
        for (LogRow r : rows) {
          if (r == null) continue;
          LogLine line = r.line();
          if (line == null) continue;
          if (!shouldIncludeInHistory(line)) continue;
          lines.add(line);
        }

        final boolean hasMoreFinal = hasMore;
        final LogCursor newCursorFinal = newCursor;
        final List<LogLine> linesFinal = List.copyOf(lines);
        SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(linesFinal, newCursorFinal, hasMoreFinal)));
      } catch (Exception e) {
        log.warn("Older-history fetch failed for {} / {}", serverId, tgt, e);
        SwingUtilities.invokeLater(() -> callback.accept(new LoadOlderResult(List.of(), cursor, true)));
      } finally {
        loading.remove(target);
      }
    });
  }

  private boolean safeHasMore(String serverId, String tgt, LogCursor cursor) {
    try {
      return repo.hasOlderRows(serverId, tgt, cursor.tsEpochMs(), cursor.id());
    } catch (Exception e) {
      log.debug("Older-history hasMore check failed for {} / {}", serverId, tgt, e);
      return true;
    }
  }

  private static Instant safeInstant(long epochMs) {
    if (epochMs <= 0) return Instant.now();
    try {
      return Instant.ofEpochMilli(epochMs);
    } catch (Exception ignored) {
      return Instant.now();
    }
  }

  /**
   *
   * <p>This is best-effort: if the server doesn't support chathistory or no response arrives,
   * we fail open (return false) and let DB-only behavior continue.
   */
  private boolean requestRemoteHistoryAndWait(String serverId, String target, Instant beforeExclusive, int limit) {
    if (irc == null || ingestBus == null) return false;
    if (serverId == null || serverId.isBlank()) return false;
    if (target == null || target.isBlank()) return false;
    // CHATHISTORY is defined for channel/query targets; avoid spamming status.
    if ("status".equalsIgnoreCase(target.trim())) return false;
    if (limit <= 0) return false;

    boolean canChatHistory = false;
    boolean canZncPlayback = false;
    try {
      canChatHistory = irc.isChatHistoryAvailable(serverId);
    } catch (Exception ignored) {
      canChatHistory = false;
    }
    try {
      canZncPlayback = irc.isZncPlaybackAvailable(serverId);
    } catch (Exception ignored) {
      canZncPlayback = false;
    }

    if (!canChatHistory && !canZncPlayback) return false;

    // ZNC playback completion is best-effort and may take longer than CHATHISTORY.
    final Duration timeout = canZncPlayback ? ZNC_PLAYBACK_REMOTE_FILL_TIMEOUT : CHATHISTORY_REMOTE_FILL_TIMEOUT;

    try {
      var waiter = ingestBus.awaitNext(serverId, target, timeout);

      boolean requested = false;

      if (canChatHistory) {
        try {
          // Sends the CHATHISTORY request (or throws if caps weren't negotiated).
          irc.requestChatHistoryBefore(serverId, target, beforeExclusive, limit).blockingAwait();
          requested = true;
        } catch (Exception e) {
          log.debug("Remote CHATHISTORY request failed for {} / {}", serverId, target, e);
          requested = false;
        }
      }

      // Fall back to ZNC playback if CHATHISTORY isn't usable or the send failed.
      if (!requested && canZncPlayback) {
        try {
          irc.requestZncPlaybackBefore(serverId, target, beforeExclusive, ZNC_PLAYBACK_WINDOW).blockingAwait();
          requested = true;
        } catch (Exception e) {
          log.debug("Remote ZNC playback request failed for {} / {}", serverId, target, e);
          requested = false;
        }
      }

      if (!requested) {
        waiter.cancel(true);
        return false;
      }

      try {
        waiter.get(timeout.toMillis() + 250, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        // Timeout is okay; we'll just re-query DB and return whatever we have.
        log.debug("Remote history ingest wait timed out for {} / {}", serverId, target);
      }
      return true;
    } catch (Exception e) {
      log.debug("Remote history coordination failed for {} / {}", serverId, target, e);
      return false;
    }
  }

  @Override
  public void reset(TargetRef target) {
    if (target == null) return;
    oldestCursor.remove(target);
    noMoreOlder.remove(target);
    loading.remove(target);
  }

  @Override
  public void onTargetSelected(TargetRef target) {
    if (target == null) return;
    if (target.isUiOnly()) return;

    // Don't preload the same target multiple times during a single app run.
    // (Prevents duplicate inserts when users re-select the same node.)
    if (oldestCursor.containsKey(target)) return;

    int limit = 100;
    try {
      if (settingsBus != null) {
        limit = settingsBus.get().chatHistoryInitialLoadLines();
      }
    } catch (Exception ignored) {
      limit = 100;
    }
    if (limit <= 0) return;

    final int limitFinal = limit;

    // If the transcript already has content, avoid re-inserting the most recent DB rows
    // (which will include lines from *this* session). Instead, only fetch rows strictly
    // older than what the user already has on screen.
    final java.util.OptionalLong earliestExistingTs = transcripts.earliestTimestampEpochMs(target);

    // NOTE: We intentionally do *not* require an empty transcript.
    // In practice, live join/mode/status lines can arrive quickly and make the transcript non-empty
    // before the async history fetch completes, which would otherwise race and skip history entirely.
    // We prepend history at the top so chronology remains correct.

    final String serverId = target.serverId();
    final String tgt = target.target();

    exec.execute(() -> {
      List<LogRow> rows;
      LogCursor cursorCandidate;
      boolean hasMore;
      try {
        if (earliestExistingTs.isPresent()) {
          long beforeTs = earliestExistingTs.getAsLong();
          // beforeId=0 => exclude rows at exactly beforeTs, ensuring we only get strictly older.
          rows = repo.fetchOlderRows(serverId, tgt, beforeTs, 0L, limitFinal);
        } else {
          rows = repo.fetchRecentRows(serverId, tgt, limitFinal);
        }
      } catch (Exception e) {
        log.warn("History fetch failed for {} / {}", serverId, tgt, e);
        return;
      }

      if (rows == null || rows.isEmpty()) {
        // Prevent repeated selection events (e.g. UI refreshes) from later causing a confusing
        // "History" divider when there isn't actually any older history to insert.
        if (earliestExistingTs.isPresent()) {
          oldestCursor.put(target, new LogCursor(earliestExistingTs.getAsLong(), 0L));
          noMoreOlder.put(target, Boolean.TRUE);
        }
        return;
      }

      // Oldest in this batch is the last (since newest-first ordering).
      LogRow batchOldest = rows.get(rows.size() - 1);
      cursorCandidate = new LogCursor(batchOldest.line().tsEpochMs(), batchOldest.id());

      try {
        hasMore = repo.hasOlderRows(serverId, tgt, cursorCandidate.tsEpochMs(), cursorCandidate.id());
      } catch (Exception e) {
        log.debug("Initial-history hasMore check failed for {} / {}", serverId, tgt, e);
        hasMore = true;
      }

      // Convert to lines in chronological order (oldest-first).
      Collections.reverse(rows);
      List<LogLine> lines = new ArrayList<>(rows.size());
      for (LogRow r : rows) {
        if (r == null) continue;
        LogLine line = r.line();
        if (line == null) continue;
        if (!shouldIncludeInHistory(line)) continue;
        lines.add(line);
      }

      final boolean hasMoreFinal = hasMore;
      final LogCursor cursorCandidateFinal = cursorCandidate;
      final List<LogLine> linesFinal = List.copyOf(lines);
      SwingUtilities.invokeLater(() -> {
        try {
          // Explicit batch boundary so filtered placeholders/hints don't "bridge" across separate loads.
          transcripts.beginHistoryInsertBatch(target);

          // If older rows exist beyond this batch, render the in-transcript paging control first,
          // so history gets inserted *below* it.
          if (hasMoreFinal) {
            try {
              transcripts.ensureLoadOlderMessagesControl(target);
              installLoadOlderHandler(target);
            } catch (Exception ignored) {
            }
          }

          int insertAt = hasMoreFinal ? transcripts.loadOlderInsertOffset(target) : 0;
          int pos = insertAt;
          int inserted = 0;
          long newestHistoryTs = 0L;
          for (LogLine line : linesFinal) {
            pos = insertLineFromHistoryAt(target, pos, line);
            inserted++;
            newestHistoryTs = line.tsEpochMs();
          }

          if (inserted > 0) {
            try {
              String label = historyDividerLabel(newestHistoryTs);
              boolean hasAfter = transcripts.hasContentAfterOffset(target, pos);
              log.debug("[history] {} insertedHistoryLines={} posAfter={} hasAfter={} divider={}",
                  target, inserted, pos, hasAfter, hasAfter ? "insert" : "pending");
              if (hasAfter) {
                transcripts.ensureHistoryDivider(target, pos, label);
              } else {
                // If there's nothing below the history we just inserted (e.g., transcript rebuild),
                // defer the divider until the next live append.
                transcripts.markHistoryDividerPending(target, label);
              }
            } catch (Exception ignored) {
            }
          }

          oldestCursor.put(target, cursorCandidateFinal);
          if (!hasMoreFinal) {
            noMoreOlder.put(target, Boolean.TRUE);
          } else {
            noMoreOlder.remove(target);
          }
        } catch (Exception e) {
          log.debug("History replay error for {} / {}", serverId, tgt, e);
        } finally {
          try {
            transcripts.endHistoryInsertBatch(target);
          } catch (Exception ignored) {
          }
        }
      });
    });
  }

  /**
   * Wire the in-transcript "Load older messages…" control to the history paging API.
   *
   * <p>This runs on the EDT.
   */
  private void installLoadOlderHandler(TargetRef target) {
    if (target == null) return;

    // Capture a reference to the embedded control so we can anchor scroll position during prepend.
    final LoadOlderMessagesComponent control = transcripts.ensureLoadOlderMessagesControl(target);

    // Ensure READY state when installed.
    transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.READY);

    transcripts.setLoadOlderMessagesControlHandler(target, () -> {
      // Decline if paging isn't currently possible.
      if (!canLoadOlder(target)) return false;

      // Flip to LOADING immediately.
      transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.LOADING);

      // If the user isn't pinned to y=0, preserve their viewport anchor while we prepend lines above.
      final ScrollAnchor anchor = ScrollAnchor.capture(control);

      int pageSize = DEFAULT_PAGE_SIZE;
      try {
        if (settingsBus != null) pageSize = settingsBus.get().chatHistoryPageSize();
      } catch (Exception ignored) {
        pageSize = DEFAULT_PAGE_SIZE;
      }
      final int limit = Math.max(1, pageSize);

      loadOlder(target, limit, res -> {
        try {
          // Explicit batch boundary so filtered placeholders/hints don't "bridge" across separate paging operations.
          transcripts.beginHistoryInsertBatch(target);

          int insertAt = transcripts.loadOlderInsertOffset(target);
          int pos = insertAt;
          for (LogLine line : res.linesOldestFirst()) {
            pos = insertLineFromHistoryAt(target, pos, line);
          }

          if (res.hasMore()) {
            transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.READY);
          } else {
            transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.EXHAUSTED);
          }
        } catch (Exception e) {
          // Fail open: allow retry.
          transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.READY);
        } finally {
          try {
            transcripts.endHistoryInsertBatch(target);
          } catch (Exception ignored) {
          }
          if (anchor != null) {
            // Run after document/layout updates so preferred size reflects the inserted content.
            SwingUtilities.invokeLater(anchor::restoreIfNeeded);
          }
        }
      });

      return true;
    });
  }

  private static final class ScrollAnchor {
    private final JViewport viewport;
    private final Point beforePos;
    private final Position anchor;
    private final int intraLineDeltaY;

    private ScrollAnchor(JViewport viewport, Point beforePos, Position anchor, int intraLineDeltaY) {
      this.viewport = viewport;
      this.beforePos = beforePos;
      this.anchor = anchor;
      this.intraLineDeltaY = intraLineDeltaY;
    }

    static ScrollAnchor capture(LoadOlderMessagesComponent control) {
      if (control == null) return null;
      try {
        JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, control);
        if (vp == null) return null;
        var view = vp.getView();
        if (view == null) return null;
        if (!(view instanceof javax.swing.text.JTextComponent text)) return null;
        Document doc = text.getDocument();
        if (doc == null) return null;

        Point p = vp.getViewPosition();
        if (p == null) p = new Point(0, 0);

        // Find the first visible model position for the top-left of the viewport.
        int anchorOffset = text.viewToModel2D(new Point(p));
        if (anchorOffset < 0) anchorOffset = 0;
        if (anchorOffset > doc.getLength()) anchorOffset = doc.getLength();

        Position pos;
        try {
          pos = doc.createPosition(anchorOffset);
        } catch (BadLocationException e) {
          return null;
        }

        // Preserve sub-line pixel offset so we don't "snap" to the top of a wrapped line.
        int rectY = 0;
        try {
          Rectangle r = text.modelToView2D(anchorOffset).getBounds();
          rectY = r != null ? r.y : 0;
        } catch (Exception ignored) {
          rectY = 0;
        }
        int deltaY = p.y - rectY;
        if (deltaY < 0) deltaY = 0;

        return new ScrollAnchor(vp, new Point(p), pos, deltaY);
      } catch (Exception ignored) {
        return null;
      }
    }

    void restoreIfNeeded() {
      try {
        if (viewport == null) return;
        var view = viewport.getView();
        if (view == null) return;
        if (!(view instanceof javax.swing.text.JTextComponent text)) return;

        // If the user was pinned at the very top (y=0), keep them there so they can see newly loaded lines.
        if (beforePos == null || beforePos.y <= 0) return;

        if (anchor == null) return;

        int off = anchor.getOffset();
        if (off < 0) off = 0;
        Document doc = text.getDocument();
        if (doc != null && off > doc.getLength()) off = doc.getLength();

        Rectangle r;
        try {
          r = text.modelToView2D(off).getBounds();
        } catch (Exception e) {
          return;
        }
        if (r == null) return;

        int newY = r.y + intraLineDeltaY;

        int viewH = view.getPreferredSize() != null ? view.getPreferredSize().height : r.y;
        int extentH = viewport.getExtentSize() != null ? viewport.getExtentSize().height : 0;
        int maxY = Math.max(0, viewH - extentH);
        if (newY > maxY) newY = maxY;
        if (newY < 0) newY = 0;

        viewport.setViewPosition(new Point(beforePos.x, newY));
      } catch (Exception ignored) {
        // Best-effort: never break the UI.
      }
    }
  }

  
  private int insertLineFromHistoryAt(TargetRef target, int insertAt, LogLine line) {
    if (line == null) return insertAt;
    if (target == null) return insertAt;

    if (!shouldIncludeInHistory(line)) return insertAt;

    boolean outgoing = line.outgoingLocalEcho() || line.direction() == LogDirection.OUT;
    LogKind kind = line.kind();
    if (kind == null) kind = LogKind.STATUS;

    return switch (kind) {
      case CHAT -> transcripts.insertChatFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          outgoing,
          line.tsEpochMs()
      );
      case ACTION -> transcripts.insertActionFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          outgoing,
          line.tsEpochMs()
      );
      case NOTICE -> transcripts.insertNoticeFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case STATUS -> transcripts.insertStatusFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case ERROR -> transcripts.insertErrorFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case PRESENCE -> transcripts.insertPresenceFromHistoryAt(
          target,
          insertAt,
          line.text(),
          line.tsEpochMs()
      );
      case SPOILER -> transcripts.insertSpoilerChatFromHistoryAt(
          target,
          insertAt,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
    };
  }

  private boolean isTranscriptEmpty(TargetRef target) {
    try {
      return transcripts.document(target).getLength() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private void replayLine(TargetRef target, LogLine line) {
    if (line == null) return;

    if (!shouldIncludeInHistory(line)) return;

    boolean outgoing = line.outgoingLocalEcho() || line.direction() == LogDirection.OUT;

    LogKind kind = line.kind();
    if (kind == null) kind = LogKind.STATUS;

    switch (kind) {
      case CHAT -> transcripts.appendChatFromHistory(
          target,
          line.fromNick(),
          line.text(),
          outgoing,
          line.tsEpochMs()
      );
      case ACTION -> transcripts.appendActionFromHistory(
          target,
          line.fromNick(),
          line.text(),
          outgoing,
          line.tsEpochMs()
      );
      case NOTICE -> transcripts.appendNoticeFromHistory(
          target,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case STATUS -> transcripts.appendStatusFromHistory(
          target,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case ERROR -> transcripts.appendErrorFromHistory(
          target,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
      case PRESENCE -> transcripts.appendPresenceFromHistory(
          target,
          line.text(),
          line.tsEpochMs()
      );
      case SPOILER -> transcripts.appendSpoilerChatFromHistory(
          target,
          line.fromNick(),
          line.text(),
          line.tsEpochMs()
      );
    }
  }

}
