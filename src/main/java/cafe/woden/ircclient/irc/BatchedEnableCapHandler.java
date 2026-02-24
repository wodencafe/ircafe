package cafe.woden.ircclient.irc;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.CapHandler;
import org.pircbotx.exception.CAPException;

/**
 * Requests multiple optional IRCv3 capabilities in one CAP REQ line.
 *
 * <p>PircBotX's {@link org.pircbotx.cap.EnableCapHandler} sends one CAP REQ per capability. On
 * networks that ACK each request with a small delay, that can noticeably increase connect latency.
 * This handler batches all desired capabilities into a single request while preserving optional
 * semantics (missing caps are ignored).
 */
final class BatchedEnableCapHandler implements CapHandler {

  private final List<String> desiredCaps;
  private final Set<String> pendingCapsLower = new LinkedHashSet<>();

  BatchedEnableCapHandler(List<String> desiredCaps) {
    LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
    for (String cap : desiredCaps) {
      String normalized = normalizeCap(cap);
      if (normalized == null) continue;
      dedup.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
    }
    this.desiredCaps = List.copyOf(dedup.values());
  }

  @Override
  public boolean handleLS(PircBotX bot, ImmutableList<String> serverCaps) throws CAPException {
    pendingCapsLower.clear();
    if (desiredCaps.isEmpty()) return true;

    Set<String> offeredLower = new LinkedHashSet<>();
    if (serverCaps != null) {
      for (String cap : serverCaps) {
        String normalized = normalizeCap(cap);
        if (normalized == null) continue;
        if (normalized.startsWith("-")) normalized = normalized.substring(1);
        if (!normalized.isEmpty()) {
          offeredLower.add(normalized.toLowerCase(Locale.ROOT));
        }
      }
    }

    List<String> toRequest = new ArrayList<>();
    for (String cap : desiredCaps) {
      String key = cap.toLowerCase(Locale.ROOT);
      if (offeredLower.contains(key)) {
        toRequest.add(cap);
        pendingCapsLower.add(key);
      }
    }

    if (toRequest.isEmpty()) return true;
    bot.sendCAP().request(toRequest.toArray(new String[0]));
    return false;
  }

  @Override
  public boolean handleACK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    resolve(caps);
    return pendingCapsLower.isEmpty();
  }

  @Override
  public boolean handleNAK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    resolve(caps);
    return pendingCapsLower.isEmpty();
  }

  @Override
  public boolean handleUnknown(PircBotX bot, String line) throws CAPException {
    return false;
  }

  private void resolve(ImmutableList<String> caps) {
    if (caps == null || caps.isEmpty() || pendingCapsLower.isEmpty()) return;
    for (String cap : caps) {
      String normalized = normalizeCap(cap);
      if (normalized == null) continue;
      if (normalized.startsWith("-")) normalized = normalized.substring(1);
      if (!normalized.isEmpty()) {
        pendingCapsLower.remove(normalized.toLowerCase(Locale.ROOT));
      }
    }
  }

  private static String normalizeCap(String cap) {
    if (cap == null) return null;
    String normalized = cap.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.startsWith(":")) normalized = normalized.substring(1).trim();
    boolean negated = false;
    if (normalized.startsWith("-")) {
      negated = true;
      normalized = normalized.substring(1).trim();
    }
    int eq = normalized.indexOf('=');
    if (eq >= 0) {
      normalized = normalized.substring(0, eq).trim();
    }
    if (normalized.isEmpty()) return null;
    if (negated) normalized = "-" + normalized;
    return normalized.isEmpty() ? null : normalized;
  }

  @Override
  public String toString() {
    return "BatchedEnableCapHandler(desiredCaps=" + desiredCaps + ")";
  }
}
