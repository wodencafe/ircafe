package cafe.woden.ircclient.irc;

import java.util.Optional;

final class PircbotxIrcv3BatchTag {

  private PircbotxIrcv3BatchTag() {}

  static Optional<String> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Optional.empty();

    String batch =
        PircbotxIrcv3Tags.firstTagValue(PircbotxIrcv3Tags.fromEvent(pircbotxEvent), "batch");
    return batch.isBlank() ? Optional.empty() : Optional.of(batch);
  }

  /** Parse {@code @batch=} from a raw IRC line. */
  static Optional<String> fromRawLine(String rawLine) {
    String batch = PircbotxIrcv3Tags.firstTagValue(PircbotxIrcv3Tags.fromRawLine(rawLine), "batch");
    return batch.isBlank() ? Optional.empty() : Optional.of(batch);
  }
}
