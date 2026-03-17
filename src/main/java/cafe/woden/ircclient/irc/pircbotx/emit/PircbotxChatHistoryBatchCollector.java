package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks in-flight IRCv3 chat-history batches for a single connection.
 *
 * <p>The bridge listener still decides how live PircBotX events map into {@link ChatHistoryEntry},
 * but this collector owns the batch lifecycle and buffering so the listener no longer manages that
 * state directly.
 */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxChatHistoryBatchCollector {
  private static final Logger log =
      LoggerFactory.getLogger(PircbotxChatHistoryBatchCollector.class);

  @NonNull private final String serverId;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  private final Map<String, ChatHistoryBatchBuffer> activeBatches = new HashMap<>();

  public boolean handleBatchControlLine(String normalizedLine) {
    ParsedIrcLine pl = PircbotxInboundLineParsers.parseIrcLine(normalizedLine);
    if (pl == null || pl.command() == null) return false;
    if (!"BATCH".equalsIgnoreCase(pl.command())) return false;

    java.util.List<String> params = pl.params();
    String trailing = pl.trailing();
    if (params == null || params.isEmpty()) {
      return true;
    }

    String first = params.getFirst();
    if (first == null || first.isBlank()) return true;
    if (first.startsWith("+")) {
      String id = first.substring(1);
      String type = (params.size() >= 2) ? params.get(1) : "";

      if (isChatHistoryBatchType(type)) {
        String target = (params.size() >= 3) ? params.get(2) : "";
        if ((target == null || target.isBlank()) && trailing != null && !trailing.isBlank()) {
          target = trailing;
        }

        activeBatches.put(id, new ChatHistoryBatchBuffer(target));
        log.debug(
            "[{}] CHATHISTORY BATCH start id={} target={} raw={}",
            serverId,
            id,
            target,
            normalizedLine);
      }
      return true;
    }
    if (first.startsWith("-")) {
      String id = first.substring(1);
      ChatHistoryBatchBuffer buf = activeBatches.remove(id);
      if (buf != null) {
        int n = buf.entries.size();
        log.info(
            "[{}] CHATHISTORY BATCH end id={} target={} lines={}", serverId, id, buf.target, n);
        emit.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.ChatHistoryBatchReceived(
                    Instant.now(), buf.target, id, java.util.List.copyOf(buf.entries))));
      }
      return true;
    }

    return true;
  }

  public boolean appendIfActive(
      String batchId,
      ChatHistoryEntry.Kind kind,
      Instant at,
      String fallbackTarget,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (batchId == null || batchId.isBlank()) return false;
    ChatHistoryBatchBuffer buf = activeBatches.get(batchId);
    if (buf == null) return false;

    String target = (buf.target == null || buf.target.isBlank()) ? fallbackTarget : buf.target;
    buf.entries.add(
        new ChatHistoryEntry(
            at == null ? Instant.now() : at,
            kind == null ? ChatHistoryEntry.Kind.PRIVMSG : kind,
            target == null ? "" : target,
            from == null ? "" : from,
            text == null ? "" : text,
            messageId,
            ircv3Tags == null ? Map.of() : ircv3Tags));
    return true;
  }

  public boolean maybeCaptureUnknownLine(String originalLineWithTags, String normalizedLine) {
    Optional<String> maybeBatchId = Ircv3BatchTag.fromRawLine(originalLineWithTags);
    if (maybeBatchId.isEmpty()) return false;

    ParsedIrcLine pl = PircbotxInboundLineParsers.parseIrcLine(normalizedLine);
    if (pl == null || pl.command() == null) return false;
    String cmd = pl.command().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd) && !"NOTICE".equals(cmd)) return false;

    Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(originalLineWithTags);
    if (at == null) at = Instant.now();

    String from = PircbotxInboundLineParsers.nickFromPrefix(pl.prefix());
    String text = pl.trailing();
    if (text == null) text = "";
    Map<String, String> ircv3Tags = Ircv3Tags.fromRawLine(originalLineWithTags);
    String messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
    String fallbackTarget =
        pl.params() != null && !pl.params().isEmpty()
            ? Objects.toString(pl.params().getFirst(), "")
            : "";

    if ("PRIVMSG".equals(cmd)) {
      String action = PircbotxUtil.parseCtcpAction(text);
      if (action != null) {
        return appendIfActive(
            maybeBatchId.get(),
            ChatHistoryEntry.Kind.ACTION,
            at,
            fallbackTarget,
            from,
            action,
            messageId,
            ircv3Tags);
      }
      return appendIfActive(
          maybeBatchId.get(),
          ChatHistoryEntry.Kind.PRIVMSG,
          at,
          fallbackTarget,
          from,
          text,
          messageId,
          ircv3Tags);
    }

    return appendIfActive(
        maybeBatchId.get(),
        ChatHistoryEntry.Kind.NOTICE,
        at,
        fallbackTarget,
        from,
        text,
        messageId,
        ircv3Tags);
  }

  public void clear() {
    activeBatches.clear();
  }

  private static boolean isChatHistoryBatchType(String type) {
    if (type == null) return false;
    String t = type.toLowerCase(Locale.ROOT);
    return t.contains("chathistory");
  }

  private static final class ChatHistoryBatchBuffer {
    private final String target;
    private final ArrayList<ChatHistoryEntry> entries = new ArrayList<>();

    private ChatHistoryBatchBuffer(String target) {
      this.target = target == null ? "" : target;
    }
  }
}
