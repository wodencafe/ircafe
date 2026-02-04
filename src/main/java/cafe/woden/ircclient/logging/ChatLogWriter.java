package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.logging.model.LogLine;

@FunctionalInterface
public interface ChatLogWriter {
  void log(LogLine line);
}
