package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.model.LogLine;

@FunctionalInterface
public interface ChatLogWriter {
  void log(LogLine line);
}
