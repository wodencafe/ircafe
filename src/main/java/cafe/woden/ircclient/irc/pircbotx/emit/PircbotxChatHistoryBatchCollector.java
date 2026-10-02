package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.ircv3.spi.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks in-flight IRCv3 chat-history batches for a single connection.
 *
 * <p>The bridge listener still decides how live PircBotX events map into {@link ChatHistoryEntry},
 * but this collector owns the batch lifecycle and buffering so the listener no longer manages that
 * state directly.
 */
public final class PircbotxChatHistoryBatchCollector {
  private static final Logger log =
      LoggerFactory.getLogger(PircbotxChatHistoryBatchCollector.class);
  private static final int MAX_PLAYBACK_DIAGNOSTIC_BATCHES = 64;

  private final String serverId;
  private final Consumer<ServerIrcEvent> emit;
  private final Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog;
  private final Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog;
  private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;
  private final Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport;
  private final Map<String, ChatHistoryBatchBuffer> activeBatches = new HashMap<>();
  // Metadata only; messages in native ZNC batches continue through the regular event path.
  private final LinkedHashMap<String, PlaybackBatchDiagnostics> playbackBatches =
      new LinkedHashMap<>();

  public PircbotxChatHistoryBatchCollector(
      String serverId,
      Consumer<ServerIrcEvent> emit,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.inboundCommandRuntimeCatalog =
        Objects.requireNonNull(inboundCommandRuntimeCatalog, "inboundCommandRuntimeCatalog");
    this.inboundTagRuntimeCatalog =
        Objects.requireNonNull(inboundTagRuntimeCatalog, "inboundTagRuntimeCatalog");
    this.serverTimeRuntimeSupport =
        Objects.requireNonNull(serverTimeRuntimeSupport, "serverTimeRuntimeSupport");
    this.messageTagsRuntimeSupport =
        Objects.requireNonNull(messageTagsRuntimeSupport, "messageTagsRuntimeSupport");
  }

  public boolean handleBatchControlLine(String normalizedLine) {
    List<Ircv3InboundCommandSignal> signals =
        inboundCommandRuntimeCatalog.parse(
            Ircv3InboundCommandOperation.HISTORY_BATCH_CONTROL,
            new Ircv3InboundCommandRequest("", "BATCH", normalizedLine, List.of(), Map.of()));
    if (signals.isEmpty()) return false;

    for (Ircv3InboundCommandSignal signal : signals) {
      if (signal instanceof Ircv3InboundCommandSignal.HistoryBatchStarted start) {
        if ("znc.in/playback".equalsIgnoreCase(start.type())) {
          if (playbackBatches.size() >= MAX_PLAYBACK_DIAGNOSTIC_BATCHES
              && !playbackBatches.containsKey(start.batchId())) {
            var evicted = playbackBatches.pollFirstEntry();
            log.warn(
                "[{}] ZNC playback diagnostics evicted unfinished batch id={}",
                serverId,
                evicted.getKey());
          }
          playbackBatches.put(start.batchId(), new PlaybackBatchDiagnostics(start.target()));
          log.info(
              "[{}] ZNC playback batch started id={} target={}",
              serverId,
              start.batchId(),
              start.target());
        }
        if (isChatHistoryBatch(start.type())) {
          activeBatches.put(start.batchId(), new ChatHistoryBatchBuffer(start.target()));
          log.debug(
              "[{}] CHATHISTORY BATCH start id={} target={} raw={}",
              serverId,
              start.batchId(),
              start.target(),
              normalizedLine);
        }
        continue;
      }

      if (signal instanceof Ircv3InboundCommandSignal.HistoryBatchEnded end) {
        PlaybackBatchDiagnostics playback = playbackBatches.remove(end.batchId());
        if (playback != null) {
          log.info(
              "[{}] ZNC playback batch ended id={} target={} observedMessages={} earliest={} latest={}",
              serverId,
              end.batchId(),
              playback.target,
              playback.observedMessages,
              playback.earliest,
              playback.latest);
        }
        ChatHistoryBatchBuffer buf = activeBatches.remove(end.batchId());
        if (buf != null) {
          int n = buf.entries.size();
          log.info(
              "[{}] CHATHISTORY BATCH end id={} target={} lines={}",
              serverId,
              end.batchId(),
              buf.target,
              n);
          emit.accept(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.ChatHistoryBatchReceived(
                      Instant.now(), buf.target, end.batchId(), List.copyOf(buf.entries))));
        }
      }
    }
    return true;
  }

  public Optional<String> batchId(Map<String, String> ircv3Tags) {
    Ircv3InboundTagRequest request = new Ircv3InboundTagRequest("", "", "", List.of(), ircv3Tags);
    for (Ircv3InboundTagSignal signal :
        inboundTagRuntimeCatalog.parse(Ircv3InboundTagOperation.HISTORY_BATCH_REFERENCE, request)) {
      if (signal.type() == Ircv3InboundTagSignalType.HISTORY_BATCH_REFERENCE
          && !signal.primaryValue().isBlank()) {
        return Optional.of(signal.primaryValue());
      }
    }
    return Optional.empty();
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
    PlaybackBatchDiagnostics playback = playbackBatches.get(batchId);
    if (playback != null) {
      playback.observe(at);
      if (log.isDebugEnabled()) {
        log.debug(
            "[{}] ZNC playback message observed batch={} target={} kind={} at={} msgid={}",
            serverId,
            batchId,
            playback.target.isBlank() ? fallbackTarget : playback.target,
            kind,
            at,
            messageId);
      }
    }
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
    Map<String, String> ircv3Tags = messageTagsRuntimeSupport.fromRawLine(originalLineWithTags);
    Optional<String> maybeBatchId = batchId(ircv3Tags);
    if (maybeBatchId.isEmpty()) return false;

    ParsedIrcLine pl = PircbotxInboundLineParsers.parseIrcLine(normalizedLine);
    if (pl == null || pl.command() == null) return false;
    String cmd = pl.command().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd) && !"NOTICE".equals(cmd)) return false;

    Instant at = serverTimeRuntimeSupport.resolveRawLineOrNow(originalLineWithTags);

    String from = PircbotxInboundLineParsers.nickFromPrefix(pl.prefix());
    String text = pl.trailing();
    if (text == null) text = "";
    String messageId = messageTagsRuntimeSupport.messageId(ircv3Tags);
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

  private static boolean isChatHistoryBatch(String type) {
    return Objects.toString(type, "").toLowerCase(Locale.ROOT).contains("chathistory");
  }

  public void clear() {
    activeBatches.clear();
    if (!playbackBatches.isEmpty()) {
      log.info(
          "[{}] ZNC playback diagnostics cleared unfinishedBatches={}",
          serverId,
          playbackBatches.size());
    }
    playbackBatches.clear();
  }

  private static final class PlaybackBatchDiagnostics {
    private final String target;
    private long observedMessages;
    private Instant earliest;
    private Instant latest;

    private PlaybackBatchDiagnostics(String target) {
      this.target = Objects.toString(target, "");
    }

    private void observe(Instant at) {
      observedMessages++;
      if (at == null) return;
      if (earliest == null || at.isBefore(earliest)) earliest = at;
      if (latest == null || at.isAfter(latest)) latest = at;
    }
  }

  private static final class ChatHistoryBatchBuffer {
    private final String target;
    private final ArrayList<ChatHistoryEntry> entries = new ArrayList<>();

    private ChatHistoryBatchBuffer(String target) {
      this.target = target == null ? "" : target;
    }
  }
}
