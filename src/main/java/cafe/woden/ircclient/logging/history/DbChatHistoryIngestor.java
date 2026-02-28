package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.LogLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** DB-backed {@link ChatHistoryIngestor}. */
@Component
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
public class DbChatHistoryIngestor implements ChatHistoryIngestor {

  private static final Logger log = LoggerFactory.getLogger(DbChatHistoryIngestor.class);

  private final ChatLogRepository repo;
  private final TransactionTemplate tx;
  private final LogProperties props;
  private final ExecutorService exec;

  public DbChatHistoryIngestor(
      ChatLogRepository repo,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      LogProperties props,
      @Qualifier(ExecutorConfig.DB_CHAT_HISTORY_INGEST_EXECUTOR) ExecutorService exec) {
    this.repo = Objects.requireNonNull(repo, "repo");
    this.tx = Objects.requireNonNull(tx, "tx");
    this.props = Objects.requireNonNull(props, "props");
    this.exec = Objects.requireNonNull(exec, "exec");
  }

  @Override
  public void ingestAsync(
      String serverId,
      String targetHint,
      String batchId,
      List<ChatHistoryEntry> entries,
      Consumer<ChatHistoryIngestResult> callback) {
    if (callback == null) return;
    final List<ChatHistoryEntry> safeEntries = entries == null ? List.of() : List.copyOf(entries);
    final String sid = safe(serverId);
    final String hint = safe(targetHint);
    final String bid = safe(batchId);

    exec.execute(
        () -> {
          try {
            ChatHistoryIngestResult res =
                tx.execute(status -> ingestNow(sid, hint, bid, safeEntries));
            if (res == null) {
              res =
                  new ChatHistoryIngestResult(
                      true, safeEntries.size(), 0, safeEntries.size(), 0L, 0L, "No result");
            }
            callback.accept(res);
          } catch (Exception e) {
            log.warn("[ircafe] chathistory ingest failed", e);
            callback.accept(
                new ChatHistoryIngestResult(
                    true,
                    safeEntries.size(),
                    0,
                    safeEntries.size(),
                    0L,
                    0L,
                    "Persist failed: "
                        + (e.getMessage() == null
                            ? e.getClass().getSimpleName()
                            : e.getMessage())));
          }
        });
  }

  private ChatHistoryIngestResult ingestNow(
      String serverId, String targetHint, String batchId, List<ChatHistoryEntry> entries) {
    int total = entries == null ? 0 : entries.size();
    if (total == 0) {
      return new ChatHistoryIngestResult(true, 0, 0, 0, 0L, 0L, "No history entries");
    }
    ArrayList<LogLine> candidates = new ArrayList<>(total);
    // Source is currently inferred from the batchId. This keeps the ChatHistoryIngestor
    // interface small while still letting us differentiate IRCv3 CHATHISTORY vs ZNC playback.
    final String source = inferSource(batchId);
    for (ChatHistoryEntry e : entries) {
      if (e == null) continue;
      long ts = e.at() != null ? e.at().toEpochMilli() : System.currentTimeMillis();
      String tgt = normalizeTarget(e.target(), targetHint);
      if (!Boolean.TRUE.equals(props.logPrivateMessages()) && isPrivateMessageTarget(tgt)) {
        continue;
      }
      String from = safe(e.from());
      String text = safe(e.text());

      LogKind kind =
          switch (e.kind()) {
            case ACTION -> LogKind.ACTION;
            case NOTICE -> LogKind.NOTICE;
            case PRIVMSG -> LogKind.CHAT;
          };
      candidates.add(
          new LogLine(
              serverId,
              tgt,
              ts,
              LogDirection.IN,
              kind,
              from,
              text,
              false,
              false,
              metaJson(source, batchId, e.messageId())));
    }

    if (candidates.isEmpty()) {
      return new ChatHistoryIngestResult(
          true, total, 0, total, 0L, 0L, "No parseable history entries");
    }
    candidates.sort(
        Comparator.comparingLong(LogLine::tsEpochMs)
            .thenComparing(LogLine::target)
            .thenComparing(l -> safe(l.fromNick()))
            .thenComparing(LogLine::text));
    record Key(
        String serverId,
        String target,
        long ts,
        LogDirection dir,
        LogKind kind,
        String from,
        String text) {}
    LinkedHashMap<Key, LogLine> uniq = new LinkedHashMap<>();
    for (LogLine l : candidates) {
      if (l == null) continue;
      Key k =
          new Key(
              l.serverId(),
              l.target(),
              l.tsEpochMs(),
              l.direction(),
              l.kind(),
              safe(l.fromNick()),
              l.text());
      uniq.putIfAbsent(k, l);
    }
    int dupInBatch = Math.max(0, candidates.size() - uniq.size());

    ArrayList<LogLine> toInsert = new ArrayList<>(uniq.size());
    int skippedExisting = 0;
    for (LogLine l : uniq.values()) {
      if (repo.existsExact(l)) {
        skippedExisting++;
        continue;
      }
      toInsert.add(l);
    }

    int inserted = 0;
    if (!toInsert.isEmpty()) {
      repo.insertBatch(toInsert);
      inserted = toInsert.size();
    }

    int skipped = Math.max(0, total - inserted);

    long earliest = 0L;
    long latest = 0L;
    if (!toInsert.isEmpty()) {
      earliest = toInsert.get(0).tsEpochMs();
      latest = toInsert.get(toInsert.size() - 1).tsEpochMs();
    }

    String msg = "Persisted " + inserted + "/" + total + " history lines";
    if (skippedExisting > 0 || dupInBatch > 0) {
      msg += " (skipped " + skipped + "; duplicates=" + (skippedExisting + dupInBatch) + ")";
    }
    msg += ".";

    return new ChatHistoryIngestResult(true, total, inserted, skipped, earliest, latest, msg);
  }

  private static String normalizeTarget(String raw, String fallback) {
    String t = safe(raw);
    if (!t.isBlank()) return t;
    String f = safe(fallback);
    if (!f.isBlank()) return f;
    return "status";
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
          if (c < 0x20) {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  private static String metaJson(String source, String batchId, String messageId) {
    String src = escapeJson(source);
    String bid = escapeJson(batchId);
    String mid = escapeJson(messageId);
    if ((bid == null || bid.isBlank()) && (mid == null || mid.isBlank())) {
      return "{\"source\":\"" + src + "\"}";
    }
    if (bid == null || bid.isBlank()) {
      return "{\"source\":\"" + src + "\",\"messageId\":\"" + mid + "\"}";
    }
    if (mid == null || mid.isBlank()) {
      return "{\"source\":\"" + src + "\",\"batch\":\"" + bid + "\"}";
    }
    return
        "{\"source\":\""
            + src
            + "\",\"batch\":\""
            + bid
            + "\",\"messageId\":\""
            + mid
            + "\"}";
  }

  private static String inferSource(String batchId) {
    String bid = safe(batchId).trim();
    if (bid.isEmpty()) return "chathistory";
    String lower = bid.toLowerCase(Locale.ROOT);
    if (lower.startsWith("znc-playback")) return "znc-playback";
    return "chathistory";
  }

  private static boolean isPrivateMessageTarget(String target) {
    String t = safe(target).trim();
    if (t.isEmpty()) return false;
    String key = t.toLowerCase(Locale.ROOT);
    if ("status".equals(key)) return false;
    if (t.startsWith("#") || t.startsWith("&")) return false;
    if ("__notifications__".equals(key)
        || "__channel_list__".equals(key)
        || "__dcc_transfers__".equals(key)) {
      return false;
    }
    return true;
  }
}
