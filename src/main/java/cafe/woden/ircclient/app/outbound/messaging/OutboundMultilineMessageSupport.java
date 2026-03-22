package cafe.woden.ircclient.app.outbound.messaging;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared multiline payload planning and fallback handling for outbound message sends. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundMultilineMessageSupport {

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  @NonNull private final IrcNegotiatedFeaturePort negotiatedFeaturePort;
  @NonNull private final UiPort ui;

  MultilineSendPlan plan(TargetRef target, String message, String statusPrefix) {
    String payload = Objects.toString(message, "").trim();
    List<String> lines = normalizeMessageLines(payload);
    if (target == null || lines.size() <= 1) {
      return MultilineSendPlan.send(payload);
    }

    int lineCount = lines.size();
    long payloadUtf8Bytes = multilinePayloadUtf8Bytes(lines);
    String reason =
        multilineUnavailableOrLimitReason(target.serverId(), lineCount, payloadUtf8Bytes);
    if (reason.isBlank()) {
      return MultilineSendPlan.send(joinMessageLines(lines));
    }

    boolean sendSplit = false;
    try {
      sendSplit = ui.confirmMultilineSplitFallback(target, lineCount, payloadUtf8Bytes, reason);
    } catch (Exception ignored) {
      sendSplit = false;
    }

    if (!sendSplit) {
      ui.appendStatus(target, statusPrefix, "Send canceled.");
      return MultilineSendPlan.cancel();
    }

    ui.appendStatus(target, statusPrefix, reason + " Sending as " + lineCount + " separate lines.");
    return MultilineSendPlan.split(lines);
  }

  private String multilineUnavailableOrLimitReason(
      String serverId, int lineCount, long payloadUtf8Bytes) {
    String backendUnavailableReason =
        backendCapabilityPolicy.featureUnavailableMessage(serverId, "");
    if (!backendUnavailableReason.isBlank()) {
      return backendUnavailableReason;
    }

    if (!backendCapabilityPolicy.supportsMultiline(serverId)) {
      return "IRCv3 multiline is not negotiated on this server.";
    }

    int maxLines = negotiatedFeaturePort.negotiatedMultilineMaxLines(serverId);
    if (maxLines > 0 && lineCount > maxLines) {
      return "Message has "
          + lineCount
          + " lines; negotiated multiline max-lines is "
          + maxLines
          + ".";
    }

    long maxBytes = negotiatedFeaturePort.negotiatedMultilineMaxBytes(serverId);
    if (maxBytes > 0L && payloadUtf8Bytes > maxBytes) {
      return "Message is "
          + payloadUtf8Bytes
          + " UTF-8 bytes; negotiated multiline max-bytes is "
          + maxBytes
          + ".";
    }

    return "";
  }

  private static List<String> normalizeMessageLines(String raw) {
    String input = Objects.toString(raw, "");
    if (input.isEmpty()) return List.of();
    String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.indexOf('\n') < 0) {
      return List.of(normalized);
    }
    String[] parts = normalized.split("\n", -1);
    List<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      out.add(Objects.toString(part, ""));
    }
    return out;
  }

  private static String joinMessageLines(List<String> lines) {
    if (lines == null || lines.isEmpty()) return "";
    return String.join("\n", lines);
  }

  private static long multilinePayloadUtf8Bytes(List<String> lines) {
    if (lines == null || lines.isEmpty()) return 0L;
    long total = 0L;
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      total = addSaturated(total, line.getBytes(StandardCharsets.UTF_8).length);
      if (i < lines.size() - 1) {
        total = addSaturated(total, 1L);
      }
    }
    return total;
  }

  private static long addSaturated(long left, long right) {
    if (right <= 0L) return left;
    if (left >= Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  record MultilineSendPlan(Decision decision, String payload, List<String> lines) {

    static MultilineSendPlan send(String payload) {
      return new MultilineSendPlan(Decision.SEND, Objects.toString(payload, ""), List.of());
    }

    static MultilineSendPlan split(List<String> lines) {
      return new MultilineSendPlan(
          Decision.SPLIT_LINES, "", lines == null ? List.of() : List.copyOf(lines));
    }

    static MultilineSendPlan cancel() {
      return new MultilineSendPlan(Decision.CANCEL, "", List.of());
    }

    boolean shouldCancel() {
      return decision == Decision.CANCEL;
    }

    boolean shouldSplitLines() {
      return decision == Decision.SPLIT_LINES;
    }
  }

  enum Decision {
    SEND,
    SPLIT_LINES,
    CANCEL
  }
}
