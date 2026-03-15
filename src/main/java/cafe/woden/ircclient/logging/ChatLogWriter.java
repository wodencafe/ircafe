package cafe.woden.ircclient.logging;


@FunctionalInterface
public interface ChatLogWriter {
  void log(LogLine line);
}
