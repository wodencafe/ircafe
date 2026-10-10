package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNick;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseLong;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Native backlog decoding and batch assembly, with a sequence owned by one session. */
final class QuasselCoreBacklogTranslator {
  private static final int MESSAGE_TYPE_PLAIN = 0x0001;
  private static final int MESSAGE_TYPE_NOTICE = 0x0002;
  private static final int MESSAGE_TYPE_ACTION = 0x0004;

  private final AtomicLong batchSequence = new AtomicLong();
  private final Supplier<Instant> now;

  QuasselCoreBacklogTranslator() {
    this(Instant::now);
  }

  QuasselCoreBacklogTranslator(Supplier<Instant> now) {
    this.now = Objects.requireNonNull(now, "now");
  }

  IrcEvent.ChatHistoryBatchReceived sync(
      List<Object> values,
      IntFunction<BufferInfoValue> knownBuffer,
      UnaryOperator<BufferInfoValue> resolveBuffer,
      BiFunction<BufferInfoValue, String, String> historyTarget,
      BiConsumer<String, Integer> observeTarget,
      BiConsumer<MessageValue, ChatHistoryEntry> observeHistory) {
    if (values == null || values.isEmpty()) return null;

    BufferInfoValue bufferInfo = null;
    if (values.getFirst() instanceof Number bufferId) {
      bufferInfo = knownBuffer.apply(bufferId.intValue());
      // A late response for a removed buffer must not complete another target's request.
      if (bufferInfo == null) return null;
    }
    ArrayList<MessageValue> messages = new ArrayList<>();
    for (Object value : values) {
      if (bufferInfo == null && value instanceof BufferInfoValue info) {
        bufferInfo = resolveBuffer.apply(info);
      }
      collectMessages(value, messages);
    }
    if (bufferInfo == null && !messages.isEmpty()) {
      bufferInfo = resolveBuffer.apply(messages.getFirst().bufferInfo());
    }
    if (bufferInfo == null) return null;
    String from = messages.isEmpty() ? "" : extractNick(messages.getFirst().sender());
    String target = historyTarget.apply(bufferInfo, from);
    if (target.isEmpty()) return null;
    observeTarget.accept(target, bufferInfo.networkId());

    ArrayList<ChatHistoryEntry> entries = new ArrayList<>(messages.size());
    for (MessageValue message : messages) {
      ChatHistoryEntry entry = toHistoryEntry(message, target, historyTarget);
      if (entry != null) {
        observeHistory.accept(message, entry);
        entries.add(entry);
      }
    }
    // Core backlog queries return newest first; consumers replay in chronological order.
    entries.sort(Comparator.comparingLong(entry -> tryParseLong(entry.messageId())));

    String batchId = "quassel-backlog-sync-" + batchSequence.incrementAndGet();
    return new IrcEvent.ChatHistoryBatchReceived(now.get(), target, batchId, List.copyOf(entries));
  }

  IrcEvent.ChatHistoryBatchReceived display(
      Instant at,
      String targetFromBuffer,
      MessageValue message,
      String messageId,
      BiFunction<BufferInfoValue, String, String> historyTarget) {
    ChatHistoryEntry entry = toHistoryEntry(message, targetFromBuffer, historyTarget);
    if (entry == null) return null;
    String base = messageId.isEmpty() ? Long.toString(message.timestampEpochSeconds()) : messageId;
    if (base.isBlank()) base = Long.toString(now.get().toEpochMilli());
    String batchId = "quassel-backlog-" + base + "-" + batchSequence.incrementAndGet();
    return new IrcEvent.ChatHistoryBatchReceived(at, entry.target(), batchId, List.of(entry));
  }

  private ChatHistoryEntry toHistoryEntry(
      MessageValue message,
      String targetFromBuffer,
      BiFunction<BufferInfoValue, String, String> historyTarget) {
    if (message == null) return null;
    int typeBits = message.typeBits();
    if (!isHistoryTextMessage(typeBits)) return null;

    Instant at =
        message.timestampEpochSeconds() > 0
            ? Instant.ofEpochSecond(message.timestampEpochSeconds())
            : now.get();
    String from = extractNick(message.sender());
    String text = Objects.toString(message.content(), "");
    String target = historyTarget.apply(message.bufferInfo(), from);
    if (target.isEmpty()) target = Objects.toString(targetFromBuffer, "").trim();
    if (target.isEmpty()) return null;

    String messageId = message.messageId() > 0 ? Long.toString(message.messageId()) : "";
    ChatHistoryEntry.Kind kind;
    if (isActionMessage(typeBits)) {
      kind = ChatHistoryEntry.Kind.ACTION;
    } else if (isNoticeMessage(typeBits)) {
      kind = ChatHistoryEntry.Kind.NOTICE;
    } else {
      kind = ChatHistoryEntry.Kind.PRIVMSG;
    }
    return new ChatHistoryEntry(at, kind, target, from, text, messageId, Map.of());
  }

  private static void collectMessages(Object raw, List<MessageValue> out) {
    if (raw == null || out == null) return;
    if (raw instanceof MessageValue message) {
      out.add(message);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) collectMessages(value, out);
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Object value : map.values()) collectMessages(value, out);
    }
  }

  static boolean isPlainMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_PLAIN) != 0;
  }

  static boolean isNoticeMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_NOTICE) != 0;
  }

  static boolean isActionMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_ACTION) != 0;
  }

  static boolean isHistoryTextMessage(int typeBits) {
    return isPlainMessage(typeBits) || isActionMessage(typeBits) || isNoticeMessage(typeBits);
  }
}
