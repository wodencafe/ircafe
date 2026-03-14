package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Records replayed ZNC playback lines into the active capture window, if one exists. */
final class PircbotxPlaybackCaptureRecorder {
  private final PircbotxConnectionState conn;

  PircbotxPlaybackCaptureRecorder(PircbotxConnectionState conn) {
    this.conn = Objects.requireNonNull(conn, "conn");
  }

  boolean maybeCapture(
      String target,
      Instant at,
      ChatHistoryEntry.Kind kind,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    try {
      if (!conn.zncPlaybackCapture.shouldCapture(target, at)) return false;
      conn.zncPlaybackCapture.addEntry(
          new ChatHistoryEntry(
              at == null ? Instant.now() : at,
              kind == null ? ChatHistoryEntry.Kind.PRIVMSG : kind,
              target == null ? "" : target,
              from == null ? "" : from,
              text == null ? "" : text,
              messageId,
              ircv3Tags));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
