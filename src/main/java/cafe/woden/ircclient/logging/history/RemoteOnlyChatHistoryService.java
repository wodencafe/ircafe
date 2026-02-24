package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.core.Completable;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Remote-only chat history paging (IRCv3 CHATHISTORY), used when DB logging is disabled.
 */
@Component
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RemoteOnlyChatHistoryService implements ChatHistoryService {

  private static final Logger log = LoggerFactory.getLogger(RemoteOnlyChatHistoryService.class);

  private static final int DEFAULT_PAGE_SIZE = 75;
  private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(6);

  private final IrcClientService irc;
  private final ChatHistoryBatchBus batchBus;
  private final ZncPlaybackBus zncPlaybackBus;
  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;

  private final ExecutorService exec;

  private final ConcurrentHashMap<TargetRef, LogCursor> oldestCursor = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<TargetRef, Boolean> noMoreOlder = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<TargetRef, Boolean> inFlight = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<TargetRef, Boolean> handlerInstalled = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<TargetRef, DocumentListener> docListeners = new ConcurrentHashMap<>();

  public RemoteOnlyChatHistoryService(
      IrcClientService irc,
      ChatHistoryBatchBus batchBus,
      ZncPlaybackBus zncPlaybackBus,
      ChatTranscriptStore transcripts,
      UiSettingsBus settingsBus,
      @Qualifier(ExecutorConfig.REMOTE_CHAT_HISTORY_EXECUTOR) ExecutorService exec
  ) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.batchBus = Objects.requireNonNull(batchBus, "batchBus");
    this.zncPlaybackBus = zncPlaybackBus;
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.settingsBus = settingsBus;
    this.exec = Objects.requireNonNull(exec, "exec");
  }

  @Override
  public void onTargetSelected(TargetRef target) {
    if (!shouldOfferPaging(target)) return;

    SwingUtilities.invokeLater(() -> {
      StyledDocument doc = transcripts.document(target);
      if (doc == null) return;

      if (doc.getLength() > 0) {
        ensureLoadOlderControlAndHandler(target);
        return;
      }

      // Don’t show “Load older” on an empty transcript. Arm it for when local messages arrive.
      docListeners.computeIfAbsent(target, t -> {
        DocumentListener l = new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            arm();
          }

          @Override
          public void removeUpdate(DocumentEvent e) {
            // ignore
          }

          @Override
          public void changedUpdate(DocumentEvent e) {
            // ignore
          }

          private void arm() {
            SwingUtilities.invokeLater(() -> {
              StyledDocument d = transcripts.document(target);
              if (d == null || d.getLength() == 0) return;

              ensureLoadOlderControlAndHandler(target);

              DocumentListener existing = docListeners.remove(target);
              if (existing != null) {
                try {
                  d.removeDocumentListener(existing);
                } catch (Exception ignored) {
                }
              }
            });
          }
        };
        try {
          doc.addDocumentListener(l);
        } catch (Exception ignored) {
        }
        return l;
      });
    });
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void reset(TargetRef target) {
    if (target == null) return;
    oldestCursor.remove(target);
    noMoreOlder.remove(target);
    inFlight.remove(target);
    handlerInstalled.remove(target);

    DocumentListener l = docListeners.remove(target);
    if (l != null) {
      try {
        StyledDocument doc = transcripts.document(target);
        if (doc != null) doc.removeDocumentListener(l);
      } catch (Exception ignored) {
      }
    }

    try {
      transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.READY);
    } catch (Exception ignored) {
    }
  }

  @Override
  public boolean canReloadRecent(TargetRef target) {
    if (target == null) return false;
    if (!shouldOfferPaging(target)) return false;
    // Allow reload even when transcript is empty.
    return irc.isChatHistoryAvailable(target.serverId()) || irc.isZncPlaybackAvailable(target.serverId());
  }

  @Override
  public void reloadRecent(TargetRef target) {
    if (!canReloadRecent(target)) return;

    // Seed cursor to "now" so remote history BEFORE now yields the most recent messages.
    LogCursor seed = new LogCursor(System.currentTimeMillis(), 0);
    oldestCursor.put(target, seed);
    noMoreOlder.remove(target);

    int limit = 100;
    try {
      if (settingsBus != null) {
        limit = settingsBus.get().chatHistoryInitialLoadLines();
      }
    } catch (Exception ignored) {
      limit = 100;
    }
    if (limit <= 0) return;
    final int limitFinal = Math.max(1, limit);

    if (Boolean.TRUE.equals(inFlight.putIfAbsent(target, Boolean.TRUE))) return;

    exec.execute(() -> {
      try {
        LoadOlderResult res = fetchRemote(target, limitFinal);
        if (res != null) {
          try {
            oldestCursor.put(target, res.newOldestCursor());
            if (!res.hasMore()) noMoreOlder.put(target, Boolean.TRUE);
          } catch (Exception ignored) {
          }
        }

        SwingUtilities.invokeLater(() -> {
          try {
            if (res == null) return;
            List<LogLine> lines = res.linesOldestFirst();
            if (lines == null || lines.isEmpty()) return;

            // Explicit batch boundary so filtered placeholders/hints don't "bridge" across separate loads.
            transcripts.beginHistoryInsertBatch(target);

            // Now that we have something to show, enable the paging control (if available).
            try {
              ensureLoadOlderControlAndHandler(target);
            } catch (Exception ignored) {
            }

            int insertAt = 0;
            try {
              insertAt = transcripts.loadOlderInsertOffset(target);
            } catch (Exception ignored) {
              insertAt = 0;
            }
            int pos = insertAt;
            int inserted = 0;
            for (LogLine line : lines) {
              pos = insertLineFromHistoryAt(target, pos, line);
              inserted++;
            }

            if (inserted > 0) {
              try {
                transcripts.ensureHistoryDivider(target, pos, "Earlier messages");
              } catch (Exception ignored) {
              }
            }

            try {
              transcripts.setLoadOlderMessagesControlState(
                  target, res.hasMore() ? LoadOlderMessagesComponent.State.READY : LoadOlderMessagesComponent.State.EXHAUSTED
              );
            } catch (Exception ignored) {
            }
          } finally {
            try {
              transcripts.endHistoryInsertBatch(target);
            } catch (Exception ignored) {
            }
          }
        });
      } finally {
        inFlight.remove(target);
      }
    });
  }

  @Override
  public boolean canLoadOlder(TargetRef target) {
    if (!shouldOfferPaging(target)) return false;
    if (!irc.isChatHistoryAvailable(target.serverId())
        && !irc.isZncPlaybackAvailable(target.serverId())) return false;
    if (Boolean.TRUE.equals(noMoreOlder.get(target))) return false;
    if (Boolean.TRUE.equals(inFlight.get(target))) return false;

    // Don’t advertise paging if the transcript is still empty.
    try {
      StyledDocument doc = transcripts.document(target);
      if (doc == null || doc.getLength() == 0) return false;
    } catch (Exception ignored) {
    }
    return true;
  }

  @Override
  public void loadOlder(TargetRef target, int limit, java.util.function.Consumer<LoadOlderResult> callback) {
    if (callback == null) return;
    if (target == null) {
      callback.accept(new LoadOlderResult(List.of(), new LogCursor(0, 0), false));
      return;
    }
    if (!shouldOfferPaging(target)) {
      callback.accept(new LoadOlderResult(List.of(), oldestCursor.getOrDefault(target, new LogCursor(0, 0)), false));
      return;
    }

    final int pageSize = Math.max(1, limit);
    if (Boolean.TRUE.equals(inFlight.putIfAbsent(target, Boolean.TRUE))) {
      callback.accept(new LoadOlderResult(List.of(), oldestCursor.getOrDefault(target, new LogCursor(0, 0)), true));
      return;
    }

    exec.execute(() -> {
      try {
        LoadOlderResult res = fetchRemote(target, pageSize);

        try {
          if (res != null) {
            oldestCursor.put(target, res.newOldestCursor());
            if (!res.hasMore()) noMoreOlder.put(target, Boolean.TRUE);
          }
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> callback.accept(res));
      } finally {
        inFlight.remove(target);
      }
    });
  }

  private void ensureLoadOlderControlAndHandler(TargetRef target) {
    final LoadOlderMessagesComponent control = transcripts.ensureLoadOlderMessagesControl(target);

    if (Boolean.TRUE.equals(noMoreOlder.getOrDefault(target, false))) {
      transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.EXHAUSTED);
      return;
    }

    if (!irc.isChatHistoryAvailable(target.serverId())
        && !irc.isZncPlaybackAvailable(target.serverId())) {
      transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.UNAVAILABLE);
      return;
    }

    transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.READY);
    oldestCursor.computeIfAbsent(target, t -> new LogCursor(seedCursorEpochMs(t), 0));

    if (Boolean.TRUE.equals(handlerInstalled.putIfAbsent(target, Boolean.TRUE))) {
      return;
    }

    transcripts.setLoadOlderMessagesControlHandler(target, () -> {
      if (!canLoadOlder(target)) return false;

      transcripts.setLoadOlderMessagesControlState(target, LoadOlderMessagesComponent.State.LOADING);
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
          int inserted = 0;
          for (LogLine line : res.linesOldestFirst()) {
            pos = insertLineFromHistoryAt(target, pos, line);
            inserted++;
          }

          if (inserted > 0) {
            try {
              transcripts.ensureHistoryDivider(target, pos, "Earlier messages");
            } catch (Exception ignored) {
            }
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
            SwingUtilities.invokeLater(anchor::restoreIfNeeded);
          }
        }
      });

      return true;
    });
  }

  private long seedCursorEpochMs(TargetRef target) {
    try {
      OptionalLong earliest = transcripts.earliestTimestampEpochMs(target);
      if (earliest.isPresent() && earliest.getAsLong() > 0) return earliest.getAsLong();
    } catch (Exception ignored) {
    }
    return System.currentTimeMillis();
  }

  private LoadOlderResult fetchRemote(TargetRef target, int limit) {
    String sid = Objects.toString(target.serverId(), "");
    String tgt = Objects.toString(target.target(), "");
    if (sid.isBlank() || tgt.isBlank()) {
      return new LoadOlderResult(List.of(), new LogCursor(0, 0), false);
    }

    LogCursor cur = oldestCursor.computeIfAbsent(target, t -> new LogCursor(seedCursorEpochMs(t), 0));
    OptionalLong earliestLocal = transcripts.earliestTimestampEpochMs(target);
    if (cur.id() == 0 && earliestLocal.isPresent() && earliestLocal.getAsLong() > 0
        && earliestLocal.getAsLong() < cur.tsEpochMs()) {
      cur = new LogCursor(earliestLocal.getAsLong(), 0);
      oldestCursor.put(target, cur);
    }
    long beforeExclusive = Math.max(0L, cur.tsEpochMs());

    Optional<String> myNickOpt = Optional.empty();
    try {
      myNickOpt = irc.currentNick(sid);
    } catch (Exception ignored) {
      myNickOpt = Optional.empty();
    }
    final String myNick = myNickOpt.orElse("");

    boolean useChatHistory = irc.isChatHistoryAvailable(sid);
    boolean useZnc = !useChatHistory && irc.isZncPlaybackAvailable(sid);

    if (useChatHistory) {
      var wait = batchBus.awaitNext(sid, tgt, REMOTE_TIMEOUT);
      try {
        Completable send = irc.requestChatHistoryBefore(sid, tgt, beforeExclusive, limit);
        if (send != null) {
          try {
            send.blockingAwait(REMOTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
          } catch (Exception ignored) {
          }
        }
      } catch (Exception e) {
        log.debug("remote CHATHISTORY request failed for {} / {}", sid, tgt, e);
      }

      ChatHistoryBatchBus.BatchEvent ev;
      try {
        ev = wait.get(REMOTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException te) {
        return new LoadOlderResult(List.of(), cur, true);
      } catch (Exception e) {
        return new LoadOlderResult(List.of(), cur, true);
      }

      List<ChatHistoryEntry> entries = ev == null ? List.of() : ev.entries();
      if (entries == null || entries.isEmpty()) {
        return new LoadOlderResult(List.of(), cur, false);
      }

      ArrayList<LogLine> lines = new ArrayList<>(entries.size());
      for (ChatHistoryEntry entry : entries) {
        if (entry == null) continue;
        long ts = entry.at() != null ? entry.at().toEpochMilli() : System.currentTimeMillis();
        String from = safe(entry.from());
        String txt = safe(entry.text());
        boolean outgoing = !myNick.isBlank() && from.equalsIgnoreCase(myNick);

        LogKind kind = switch (entry.kind()) {
          case ACTION -> LogKind.ACTION;
          case NOTICE -> LogKind.NOTICE;
          case PRIVMSG -> LogKind.CHAT;
        };
        LogDirection dir = outgoing ? LogDirection.OUT : LogDirection.IN;

        lines.add(new LogLine(
            sid,
            tgt,
            ts,
            dir,
            kind,
            from,
            txt,
            outgoing,
            false,
            metaJson("chathistory", ev.batchId())
        ));
      }

      lines.sort(Comparator
          .comparingLong(LogLine::tsEpochMs)
          .thenComparing(LogLine::target)
          .thenComparing(l -> safe(l.fromNick()))
          .thenComparing(LogLine::text));

      long earliest = lines.get(0).tsEpochMs();
      long nextBefore = earliest > 0 ? Math.max(0, earliest - 1) : 0;

      boolean hasMore = lines.size() >= limit;
      return new LoadOlderResult(lines, new LogCursor(nextBefore, 0), hasMore);
    }

    if (useZnc) {
      if (zncPlaybackBus == null) {
        return new LoadOlderResult(List.of(), cur, true);
      }

      // ZNC playback is time-window based; request a backward window ending at beforeExclusive.
      Duration window = Duration.ofHours(6);
      Instant end = Instant.ofEpochMilli(beforeExclusive);
      Instant start = end.minus(window);

      var wait = zncPlaybackBus.awaitNext(sid, tgt, REMOTE_TIMEOUT);
      try {
        Completable send = irc.requestZncPlaybackRange(sid, tgt, start, end);
        if (send != null) {
          try {
            send.blockingAwait(REMOTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
          } catch (Exception ignored) {
          }
        }
      } catch (IllegalStateException busy) {
        // Another playback capture is in flight; allow retry.
        return new LoadOlderResult(List.of(), cur, true);
      } catch (Exception e) {
        log.debug("remote ZNC playback request failed for {} / {}", sid, tgt, e);
      }

      ZncPlaybackBus.PlaybackEvent pev;
      try {
        pev = wait.get(REMOTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException te) {
        return new LoadOlderResult(List.of(), cur, true);
      } catch (Exception e) {
        return new LoadOlderResult(List.of(), cur, true);
      }

      List<ChatHistoryEntry> entries = (pev == null) ? List.of() : pev.entries();
      if (entries == null) entries = List.of();

      if (entries.isEmpty()) {
        // No lines in this window: advance cursor earlier to avoid repeating the same range.
        long nextBefore = Math.max(0L, beforeExclusive - window.toMillis());
        boolean hasMore = nextBefore > 0;
        return new LoadOlderResult(List.of(), new LogCursor(nextBefore, 0), hasMore);
      }

      ArrayList<LogLine> lines = new ArrayList<>(entries.size());
      for (ChatHistoryEntry entry : entries) {
        if (entry == null) continue;
        long ts = entry.at() != null ? entry.at().toEpochMilli() : System.currentTimeMillis();
        String from = safe(entry.from());
        String txt = safe(entry.text());
        boolean outgoing = !myNick.isBlank() && from.equalsIgnoreCase(myNick);

        LogKind kind = switch (entry.kind()) {
          case ACTION -> LogKind.ACTION;
          case NOTICE -> LogKind.NOTICE;
          case PRIVMSG -> LogKind.CHAT;
        };
        LogDirection dir = outgoing ? LogDirection.OUT : LogDirection.IN;

        lines.add(new LogLine(
            sid,
            tgt,
            ts,
            dir,
            kind,
            from,
            txt,
            outgoing,
            false,
            metaJson("znc-playback", null)
        ));
      }

      lines.sort(Comparator
          .comparingLong(LogLine::tsEpochMs)
          .thenComparing(LogLine::target)
          .thenComparing(l -> safe(l.fromNick()))
          .thenComparing(LogLine::text));

      long earliest = lines.get(0).tsEpochMs();
      long nextBefore = earliest > 0 ? Math.max(0, earliest - 1) : Math.max(0L, beforeExclusive - window.toMillis());

      // We don't know if more exists; if we got any lines, allow more.
      return new LoadOlderResult(lines, new LogCursor(nextBefore, 0), nextBefore > 0);
    }

    // Neither CHATHISTORY nor ZNC playback is available.
    return new LoadOlderResult(List.of(), cur, false);

  }

  private boolean shouldOfferPaging(TargetRef target) {
    if (target == null) return false;
    if (target.isUiOnly()) return false;
    String t = Objects.toString(target.target(), "");
    if (t.isBlank()) return false;
    return !"status".equalsIgnoreCase(t);
  }

  private int insertLineFromHistoryAt(TargetRef target, int insertAt, LogLine line) {
    if (line == null || target == null) return insertAt;
    boolean outgoing = line.outgoingLocalEcho() || line.direction() == LogDirection.OUT;
    LogKind kind = line.kind() == null ? LogKind.STATUS : line.kind();
    return switch (kind) {
      case CHAT -> transcripts.insertChatFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), outgoing, line.tsEpochMs()
      );
      case ACTION -> transcripts.insertActionFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), outgoing, line.tsEpochMs()
      );
      case NOTICE -> transcripts.insertNoticeFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), line.tsEpochMs()
      );
      case STATUS -> transcripts.insertStatusFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), line.tsEpochMs()
      );
      case ERROR -> transcripts.insertErrorFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), line.tsEpochMs()
      );
      case PRESENCE -> transcripts.insertPresenceFromHistoryAt(
          target, insertAt, line.text(), line.tsEpochMs()
      );
      case SPOILER -> transcripts.insertSpoilerChatFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), line.tsEpochMs()
      );
      default -> transcripts.insertStatusFromHistoryAt(
          target, insertAt, line.fromNick(), line.text(), line.tsEpochMs()
      );
    };
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String escapeJson(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          else sb.append(c);
        }
      }
    }
    return sb.toString();
  }

  private static String metaJson(String source, String batchId) {
    String src = escapeJson(source);
    String bid = escapeJson(batchId);
    if (bid == null || bid.isBlank()) return "{\"source\":\"" + src + "\"}";
    return "{\"source\":\"" + src + "\",\"batch\":\"" + bid + "\"}";
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

        int anchorOffset = text.viewToModel2D(new Point(p));
        if (anchorOffset < 0) anchorOffset = 0;
        if (anchorOffset > doc.getLength()) anchorOffset = doc.getLength();

        Position pos;
        try {
          pos = doc.createPosition(anchorOffset);
        } catch (BadLocationException e) {
          return null;
        }

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
      }
    }
  }
}
