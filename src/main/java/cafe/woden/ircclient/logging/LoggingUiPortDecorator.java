package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.UiPortDecorator;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.model.LogLine;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UiPort} decorator that persists everything that reaches the transcript boundary.
 *
 */
public final class LoggingUiPortDecorator extends UiPortDecorator {

  private static final Logger log = LoggerFactory.getLogger(LoggingUiPortDecorator.class);

  private final ChatLogWriter writer;
  private final LogLineFactory factory;
  private final LogProperties props;

  public LoggingUiPortDecorator(
      UiPort delegate,
      ChatLogWriter writer,
      LogLineFactory factory,
      LogProperties props
  ) {
    super(delegate);
    this.writer = Objects.requireNonNull(writer, "writer");
    this.factory = Objects.requireNonNull(factory, "factory");
    this.props = Objects.requireNonNull(props, "props");
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    tryLog(() -> factory.chat(target, from, text, outgoingLocalEcho));
    super.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.chatAt(target, from, text, outgoingLocalEcho, ts));
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
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.chatAt(target, from, text, outgoingLocalEcho, ts));
    super.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    boolean resolved = super.resolvePendingOutgoingChat(target, pendingId, at, from, text, messageId, ircv3Tags);
    if (resolved) {
      long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
      tryLog(() -> factory.chatAt(target, from, text, true, ts));
    }
    return resolved;
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      tryLog(() -> factory.softIgnoredSpoiler(target, from, text));
    }
    super.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
      tryLog(() -> factory.softIgnoredSpoilerAt(target, from, text, ts));
    }
    super.appendSpoilerChatAt(target, at, from, text);
  }

  @Override
  public void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    tryLog(() -> factory.action(target, from, action, outgoingLocalEcho));
    super.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.actionAt(target, from, action, outgoingLocalEcho, ts));
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
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.actionAt(target, from, action, outgoingLocalEcho, ts));
    super.appendActionAt(target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendPresence(TargetRef target, PresenceEvent event) {
    tryLog(() -> factory.presence(target, event));
    super.appendPresence(target, event);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    tryLog(() -> factory.notice(target, from, text));
    super.appendNotice(target, from, text);
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.noticeAt(target, from, text, ts));
    super.appendNoticeAt(target, at, from, text);
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.noticeAt(target, from, text, ts));
    super.appendNoticeAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    tryLog(() -> factory.status(target, from, text));
    super.appendStatus(target, from, text);
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.statusAt(target, from, text, ts));
    super.appendStatusAt(target, at, from, text);
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.statusAt(target, from, text, ts));
    super.appendStatusAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    tryLog(() -> factory.error(target, from, text));
    super.appendError(target, from, text);
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    tryLog(() -> factory.errorAt(target, from, text, ts));
    super.appendErrorAt(target, at, from, text);
  }

  private void tryLog(Supplier<LogLine> supplier) {
    // Defensive: this decorator should only be wired when enabled.
    if (!Boolean.TRUE.equals(props.enabled())) return;

    try {
      writer.log(supplier.get());
    } catch (Throwable t) {
      // Never let logging failures break the UI.
      log.warn("[ircafe] Failed to persist chat log line", t);
    }
  }
}
