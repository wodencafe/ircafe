package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiPortDecorator;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.LogLine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link UiPort} decorator that persists everything that reaches the transcript boundary. */
public final class LoggingUiPortDecorator extends UiPortDecorator {

  private static final Logger log = LoggerFactory.getLogger(LoggingUiPortDecorator.class);
  private static final int DEDUP_MAX_ENTRIES = 20_000;
  private static final long DEDUP_TTL_MS = 10L * 60L * 1000L;
  private static final long TEXT_FINGERPRINT_SEED = 0xcbf29ce484222325L;
  private static final long TEXT_FINGERPRINT_PRIME = 0x100000001b3L;

  private final ChatLogWriter writer;
  private final LogLineFactory factory;
  private final LogProperties props;
  private final Cache<LogDedupKey, Boolean> recentDedup =
      Caffeine.newBuilder()
          .maximumSize(DEDUP_MAX_ENTRIES)
          .expireAfterAccess(Duration.ofMillis(DEDUP_TTL_MS))
          .build();

  public LoggingUiPortDecorator(
      UiPort delegate, ChatLogWriter writer, LogLineFactory factory, LogProperties props) {
    super(delegate);
    this.writer = Objects.requireNonNull(writer, "writer");
    this.factory = Objects.requireNonNull(factory, "factory");
    this.props = Objects.requireNonNull(props, "props");
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    tryLog(target, () -> factory.chat(target, from, text, outgoingLocalEcho));
    super.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.chatAt(target, from, text, outgoingLocalEcho, ts));
    super.appendChatAt(target, at, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(
        target,
        () -> factory.chatAt(target, from, text, outgoingLocalEcho, ts, messageId, ircv3Tags));
    super.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(
        target,
        () -> factory.chatAt(target, from, text, outgoingLocalEcho, ts, messageId, ircv3Tags));
    super.appendChatAt(
        target,
        at,
        from,
        text,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    boolean resolved =
        super.resolvePendingOutgoingChat(target, pendingId, at, from, text, messageId, ircv3Tags);
    if (resolved) {
      long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
      tryLog(
          target,
          () ->
              factory.resolvedOutgoingChatAt(
                  target, from, text, ts, pendingId, messageId, ircv3Tags));
    }
    return resolved;
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      tryLog(target, () -> factory.softIgnoredSpoiler(target, from, text));
    }
    super.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
      tryLog(target, () -> factory.softIgnoredSpoilerAt(target, from, text, ts));
    }
    super.appendSpoilerChatAt(target, at, from, text);
  }

  @Override
  public void appendSpoilerChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
      tryLog(
          target, () -> factory.softIgnoredSpoilerAt(target, from, text, ts, messageId, ircv3Tags));
    }
    super.appendSpoilerChatAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    tryLog(target, () -> factory.action(target, from, action, outgoingLocalEcho));
    super.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.actionAt(target, from, action, outgoingLocalEcho, ts));
    super.appendActionAt(target, at, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(
        target,
        () -> factory.actionAt(target, from, action, outgoingLocalEcho, ts, messageId, ircv3Tags));
    super.appendActionAt(target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(
        target,
        () -> factory.actionAt(target, from, action, outgoingLocalEcho, ts, messageId, ircv3Tags));
    super.appendActionAt(
        target,
        at,
        from,
        action,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public void appendPresence(TargetRef target, PresenceEvent event) {
    tryLog(target, () -> factory.presence(target, event));
    super.appendPresence(target, event);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    tryLog(target, () -> factory.notice(target, from, text));
    super.appendNotice(target, from, text);
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.noticeAt(target, from, text, ts));
    super.appendNoticeAt(target, at, from, text);
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.noticeAt(target, from, text, ts, messageId, ircv3Tags));
    super.appendNoticeAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    tryLog(target, () -> factory.status(target, from, text));
    super.appendStatus(target, from, text);
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.statusAt(target, from, text, ts));
    super.appendStatusAt(target, at, from, text);
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.statusAt(target, from, text, ts, messageId, ircv3Tags));
    super.appendStatusAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    tryLog(target, () -> factory.error(target, from, text));
    super.appendError(target, from, text);
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(target, () -> factory.errorAt(target, from, text, ts));
    super.appendErrorAt(target, at, from, text);
  }

  private void tryLog(TargetRef target, Supplier<LogLine> supplier) {
    // Defensive: this decorator should only be wired when enabled.
    if (!Boolean.TRUE.equals(props.enabled())) return;
    if (!shouldLogTarget(target)) return;

    try {
      LogLine line = supplier.get();
      if (line == null) return;
      if (shouldSkipDuplicate(line)) return;
      writer.log(line);
    } catch (Throwable t) {
      // Never let logging failures break the UI.
      log.warn("[ircafe] Failed to persist chat log line", t);
    }
  }

  private boolean shouldSkipDuplicate(LogLine line) {
    if (line == null) return true;
    LogDedupKey key = LogDedupKey.from(line);
    Boolean prior = recentDedup.asMap().putIfAbsent(key, Boolean.TRUE);
    return prior != null;
  }

  private static String extractMessageId(String metaJson) {
    String meta = Objects.toString(metaJson, "").trim();
    if (meta.isEmpty()) return "";
    String key = "\"messageId\"";
    int keyPos = meta.indexOf(key);
    if (keyPos < 0) return "";
    int colon = meta.indexOf(':', keyPos + key.length());
    if (colon < 0) return "";
    int firstQuote = meta.indexOf('"', colon + 1);
    if (firstQuote < 0) return "";

    StringBuilder out = new StringBuilder(32);
    boolean escaped = false;
    for (int i = firstQuote + 1; i < meta.length(); i++) {
      char c = meta.charAt(i);
      if (escaped) {
        out.append(c);
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        break;
      }
      out.append(c);
    }
    return out.toString().trim();
  }

  private record LogDedupKey(
      String serverId,
      String target,
      LogDirection direction,
      LogKind kind,
      String messageId,
      long tsEpochMs,
      String fromNick,
      long textFingerprint,
      int textLength,
      boolean outgoingLocalEcho,
      boolean softIgnored) {
    static LogDedupKey from(LogLine line) {
      String sid = Objects.toString(line.serverId(), "").trim();
      String target = Objects.toString(line.target(), "").trim();
      LogDirection direction = line.direction();
      LogKind kind = line.kind();
      String msgId = extractMessageId(line.metaJson());
      boolean keyedByMessageId = !msgId.isBlank();
      String text = Objects.toString(line.text(), "");
      return new LogDedupKey(
          sid,
          target,
          direction,
          kind,
          msgId,
          keyedByMessageId ? 0L : line.tsEpochMs(),
          keyedByMessageId ? "" : Objects.toString(line.fromNick(), "").trim(),
          keyedByMessageId ? 0L : computeTextFingerprint(text),
          keyedByMessageId ? 0 : text.length(),
          keyedByMessageId ? false : line.outgoingLocalEcho(),
          keyedByMessageId ? false : line.softIgnored());
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

  private boolean shouldLogTarget(TargetRef target) {
    if (target == null) return true;
    if (Boolean.TRUE.equals(props.logPrivateMessages())) return true;
    return target.isStatus() || target.isChannel() || target.isUiOnly();
  }
}
