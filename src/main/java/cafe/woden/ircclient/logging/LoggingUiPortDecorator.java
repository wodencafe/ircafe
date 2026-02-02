package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.UiPortDecorator;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.model.LogLine;
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
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    if (Boolean.TRUE.equals(props.logSoftIgnoredLines())) {
      tryLog(() -> factory.softIgnoredSpoiler(target, from, text));
    }
    super.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    tryLog(() -> factory.action(target, from, action, outgoingLocalEcho));
    super.appendAction(target, from, action, outgoingLocalEcho);
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
  public void appendStatus(TargetRef target, String from, String text) {
    tryLog(() -> factory.status(target, from, text));
    super.appendStatus(target, from, text);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    tryLog(() -> factory.error(target, from, text));
    super.appendError(target, from, text);
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
