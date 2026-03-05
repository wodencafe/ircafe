package cafe.woden.ircclient.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Tracks outbound chat lines awaiting IRCv3 echo-message reconciliation. */
@Component
@ApplicationLayer
@Lazy
public class PendingEchoMessageState implements PendingEchoMessagePort {

  private final List<PendingEchoMessagePort.PendingOutboundChat> pending = new ArrayList<>();

  public synchronized PendingEchoMessagePort.PendingOutboundChat register(
      TargetRef target, String fromNick, String text, Instant createdAt) {
    Instant at = (createdAt != null) ? createdAt : Instant.now();
    PendingEchoMessagePort.PendingOutboundChat entry =
        new PendingEchoMessagePort.PendingOutboundChat(
            UUID.randomUUID().toString(),
            target,
            Objects.toString(fromNick, "").trim(),
            Objects.toString(text, "").trim(),
            at);
    pending.add(entry);
    return entry;
  }

  public synchronized Optional<PendingEchoMessagePort.PendingOutboundChat> removeById(
      String pendingId) {
    String id = normalizePendingId(pendingId);
    if (id.isEmpty()) return Optional.empty();
    for (int i = 0; i < pending.size(); i++) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      if (!id.equals(normalizePendingId(entry.pendingId()))) continue;
      pending.remove(i);
      return Optional.of(entry);
    }
    return Optional.empty();
  }

  public synchronized Optional<PendingEchoMessagePort.PendingOutboundChat> consumeByTargetAndText(
      TargetRef target, String fromNick, String text) {
    if (target == null) return Optional.empty();
    String textNorm = normalizeText(text);
    for (int i = 0; i < pending.size(); i++) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      if (!targetMatches(entry.target(), target)) continue;
      if (!normalizeText(entry.text()).equals(textNorm)) continue;
      pending.remove(i);
      return Optional.of(entry);
    }
    return Optional.empty();
  }

  public synchronized Optional<PendingEchoMessagePort.PendingOutboundChat> consumeOldestByTarget(
      TargetRef target) {
    if (target == null) return Optional.empty();
    for (int i = 0; i < pending.size(); i++) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      if (!targetMatches(entry.target(), target)) continue;
      pending.remove(i);
      return Optional.of(entry);
    }
    return Optional.empty();
  }

  public synchronized Optional<PendingEchoMessagePort.PendingOutboundChat> consumePrivateFallback(
      String serverId, String fromNick, String text) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return Optional.empty();
    String textNorm = normalizeText(text);
    for (int i = 0; i < pending.size(); i++) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      TargetRef target = entry.target();
      if (target == null || !sid.equals(target.serverId())) continue;
      if (target.isStatus() || target.isChannel()) continue;
      if (!normalizeText(entry.text()).equals(textNorm)) continue;
      pending.remove(i);
      return Optional.of(entry);
    }
    return Optional.empty();
  }

  public synchronized List<PendingEchoMessagePort.PendingOutboundChat> drainServer(
      String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return List.of();
    List<PendingEchoMessagePort.PendingOutboundChat> out = new ArrayList<>();
    for (int i = pending.size() - 1; i >= 0; i--) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      TargetRef target = entry.target();
      if (target == null || !sid.equals(target.serverId())) continue;
      out.add(0, entry);
      pending.remove(i);
    }
    return out;
  }

  public synchronized List<PendingEchoMessagePort.PendingOutboundChat> collectTimedOut(
      Duration timeout, int maxCount, Instant now) {
    Duration safeTimeout =
        (timeout == null || timeout.isZero() || timeout.isNegative())
            ? Duration.ofSeconds(45)
            : timeout;
    int cap = Math.max(1, maxCount);
    Instant anchor = (now != null) ? now : Instant.now();
    Instant cutoff = anchor.minus(safeTimeout);

    List<PendingEchoMessagePort.PendingOutboundChat> out = new ArrayList<>();
    for (int i = 0; i < pending.size(); i++) {
      PendingEchoMessagePort.PendingOutboundChat entry = pending.get(i);
      Instant created =
          entry != null && entry.createdAt() != null ? entry.createdAt() : Instant.EPOCH;
      if (created.isAfter(cutoff)) continue;
      pending.remove(i);
      i--;
      out.add(entry);
      if (out.size() >= cap) {
        break;
      }
    }
    return out;
  }

  private static boolean targetMatches(TargetRef a, TargetRef b) {
    if (a == null || b == null) return false;
    if (!Objects.equals(a.serverId(), b.serverId())) return false;
    String at = Objects.toString(a.target(), "").trim();
    String bt = Objects.toString(b.target(), "").trim();
    return at.equalsIgnoreCase(bt);
  }

  private static String normalizePendingId(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeText(String raw) {
    return Objects.toString(raw, "").trim();
  }
}
