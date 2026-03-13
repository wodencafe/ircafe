package cafe.woden.ircclient.state;

import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Mutable per-server cache of RPL_ISUPPORT tokens and derived mode vocabulary. */
@Component
@ApplicationLayer
public class ServerIsupportState implements ServerIsupportStatePort {

  private final ConcurrentHashMap<String, ServerEntry> entriesByServer = new ConcurrentHashMap<>();

  @Override
  public void applyIsupportToken(String serverId, String tokenName, String tokenValue) {
    String sid = normalizeServer(serverId);
    String key = normalizeTokenName(tokenName);
    if (sid.isEmpty() || key.isEmpty()) return;

    ServerEntry entry = entriesByServer.computeIfAbsent(sid, ignored -> new ServerEntry());
    if (tokenValue == null) {
      entry.tokens.remove(key);
    } else {
      entry.tokens.put(key, tokenValue.trim());
    }
    entry.vocabulary = ModeVocabularyParser.parse(snapshotTokens(entry.tokens));
  }

  @Override
  public ModeVocabulary vocabularyForServer(String serverId) {
    String sid = normalizeServer(serverId);
    if (sid.isEmpty()) return ModeVocabulary.fallback();
    ServerEntry entry = entriesByServer.get(sid);
    return entry == null ? ModeVocabulary.fallback() : entry.vocabulary;
  }

  @Override
  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    if (sid.isEmpty()) return;
    entriesByServer.remove(sid);
  }

  private static Map<String, String> snapshotTokens(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      String key = normalizeTokenName(entry.getKey());
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue() == null ? null : entry.getValue().trim());
    }
    if (out.isEmpty()) return Map.of();
    return Map.copyOf(out);
  }

  private static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeTokenName(String tokenName) {
    return Objects.toString(tokenName, "").trim().toUpperCase(java.util.Locale.ROOT);
  }

  private static final class ServerEntry {
    private final ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>();
    private volatile ModeVocabulary vocabulary = ModeVocabulary.fallback();
  }
}
