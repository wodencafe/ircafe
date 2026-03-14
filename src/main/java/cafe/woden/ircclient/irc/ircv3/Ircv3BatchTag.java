package cafe.woden.ircclient.irc.ircv3;

import java.util.Optional;

public final class Ircv3BatchTag {

  private Ircv3BatchTag() {}

  public static Optional<String> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Optional.empty();

    String batch = Ircv3Tags.firstTagValue(Ircv3Tags.fromEvent(pircbotxEvent), "batch");
    return batch.isBlank() ? Optional.empty() : Optional.of(batch);
  }

  /** Parse {@code @batch=} from a raw IRC line. */
  public static Optional<String> fromRawLine(String rawLine) {
    String batch = Ircv3Tags.firstTagValue(Ircv3Tags.fromRawLine(rawLine), "batch");
    return batch.isBlank() ? Optional.empty() : Optional.of(batch);
  }
}
