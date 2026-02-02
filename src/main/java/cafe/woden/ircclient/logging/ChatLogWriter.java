package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.logging.model.LogLine;

/**
 * Simple sink for persisted chat log lines.
 *
 * <p>When logging is disabled, a no-op implementation is provided.
 */
@FunctionalInterface
public interface ChatLogWriter {
  void log(LogLine line);
}
