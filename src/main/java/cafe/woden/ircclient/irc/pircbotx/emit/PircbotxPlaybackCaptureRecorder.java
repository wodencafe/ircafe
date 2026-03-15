package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;

/** Records replayed ZNC playback lines into the active capture window, if one exists. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxPlaybackCaptureRecorder {
  @NonNull private final PircbotxConnectionState conn;

  public boolean maybeCapture(
      String target,
      Instant at,
      ChatHistoryEntry.Kind kind,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    try {
      if (!conn.shouldCapturePlayback(target, at)) return false;
      conn.addPlaybackEntry(
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
