package cafe.woden.ircclient.state.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Effective per-server IRC mode grammar derived from RPL_ISUPPORT and conservative fallbacks. */
@ApplicationLayer
public record ModeVocabulary(
    Set<Character> statusModes,
    Set<Character> explicitStatusModes,
    Map<Character, Character> prefixByStatusMode,
    Map<Character, Character> statusModeByPrefix,
    String statusPrefixOrder,
    Set<Character> listModes,
    Set<Character> explicitListModes,
    Map<Character, ModeArgPolicy> argPolicyByMode,
    String channelTypes,
    String statusMessagePrefixes,
    String caseMapping,
    Character exceptsMode,
    Character invexMode) {

  private static final int UNKNOWN_PREFIX_RANK = 99;
  private static final ModeVocabulary FALLBACK = createFallback();

  public ModeVocabulary {
    statusModes = normalizeCharSet(statusModes);
    explicitStatusModes = normalizeCharSet(explicitStatusModes);
    prefixByStatusMode = normalizeCharMap(prefixByStatusMode);
    statusModeByPrefix = normalizeCharMap(statusModeByPrefix);
    statusPrefixOrder = normalizeString(statusPrefixOrder);
    listModes = normalizeCharSet(listModes);
    explicitListModes = normalizeCharSet(explicitListModes);
    argPolicyByMode = normalizePolicyMap(argPolicyByMode);
    channelTypes = normalizeString(channelTypes);
    statusMessagePrefixes = normalizeString(statusMessagePrefixes);
    caseMapping = normalizeString(caseMapping);
    exceptsMode = normalizeModeChar(exceptsMode);
    invexMode = normalizeModeChar(invexMode);
  }

  public static ModeVocabulary fallback() {
    return FALLBACK;
  }

  public boolean isStatusMode(char mode) {
    return statusModes.contains(mode);
  }

  public boolean hasExplicitStatusMode(char mode) {
    return explicitStatusModes.contains(mode);
  }

  public boolean isListMode(char mode) {
    return listModes.contains(mode);
  }

  public boolean hasExplicitListMode(char mode) {
    return explicitListModes.contains(mode);
  }

  public boolean takesArgument(char mode, boolean adding) {
    ModeArgPolicy policy = argPolicyByMode.getOrDefault(mode, ModeArgPolicy.NEVER);
    return switch (policy) {
      case LIST, ALWAYS -> true;
      case SET_ONLY -> adding;
      case NEVER -> false;
    };
  }

  public Optional<Character> prefixForMode(char mode) {
    Character prefix = prefixByStatusMode.get(mode);
    return prefix == null ? Optional.empty() : Optional.of(prefix);
  }

  public Optional<Character> modeForPrefix(char prefix) {
    Character mode = statusModeByPrefix.get(prefix);
    return mode == null ? Optional.empty() : Optional.of(mode);
  }

  public int prefixRank(String prefixToken) {
    String token = normalizeString(prefixToken);
    if (token.isEmpty() || statusPrefixOrder.isEmpty()) return UNKNOWN_PREFIX_RANK;

    int best = UNKNOWN_PREFIX_RANK;
    for (int i = 0; i < token.length(); i++) {
      int idx = statusPrefixOrder.indexOf(token.charAt(i));
      if (idx >= 0 && idx < best) {
        best = idx;
      }
    }
    return best;
  }

  public boolean isExceptsMode(char mode) {
    return exceptsMode != null && exceptsMode.charValue() == mode;
  }

  public boolean isInvexMode(char mode) {
    return invexMode != null && invexMode.charValue() == mode;
  }

  private static ModeVocabulary createFallback() {
    LinkedHashSet<Character> statusModes = new LinkedHashSet<>();
    Collections.addAll(statusModes, 'q', 'a', 'o', 'h', 'v', 'y');

    LinkedHashMap<Character, Character> prefixByStatusMode = new LinkedHashMap<>();
    prefixByStatusMode.put('q', '~');
    prefixByStatusMode.put('a', '&');
    prefixByStatusMode.put('o', '@');
    prefixByStatusMode.put('h', '%');
    prefixByStatusMode.put('v', '+');

    LinkedHashMap<Character, Character> statusModeByPrefix = new LinkedHashMap<>();
    statusModeByPrefix.put('~', 'q');
    statusModeByPrefix.put('&', 'a');
    statusModeByPrefix.put('@', 'o');
    statusModeByPrefix.put('%', 'h');
    statusModeByPrefix.put('+', 'v');

    LinkedHashSet<Character> listModes = new LinkedHashSet<>();
    Collections.addAll(listModes, 'b', 'e', 'I');

    LinkedHashMap<Character, ModeArgPolicy> argPolicies = new LinkedHashMap<>();
    for (char mode : statusModes) {
      argPolicies.put(mode, ModeArgPolicy.ALWAYS);
    }
    for (char mode : listModes) {
      argPolicies.put(mode, ModeArgPolicy.LIST);
    }
    argPolicies.put('k', ModeArgPolicy.ALWAYS);
    argPolicies.put('l', ModeArgPolicy.SET_ONLY);
    argPolicies.put('f', ModeArgPolicy.ALWAYS);
    argPolicies.put('j', ModeArgPolicy.ALWAYS);

    return new ModeVocabulary(
        statusModes,
        Set.of(),
        prefixByStatusMode,
        statusModeByPrefix,
        "~&@%+",
        listModes,
        Set.of(),
        argPolicies,
        "#&",
        "@+",
        "rfc1459",
        'e',
        'I');
  }

  private static Set<Character> normalizeCharSet(Set<Character> raw) {
    if (raw == null || raw.isEmpty()) return Set.of();
    LinkedHashSet<Character> out = new LinkedHashSet<>();
    for (Character value : raw) {
      Character normalized = normalizeModeChar(value);
      if (normalized != null) out.add(normalized);
    }
    if (out.isEmpty()) return Set.of();
    return Collections.unmodifiableSet(out);
  }

  private static Map<Character, Character> normalizeCharMap(Map<Character, Character> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<Character, Character> out = new LinkedHashMap<>();
    for (Map.Entry<Character, Character> entry : raw.entrySet()) {
      Character key = normalizeModeChar(entry.getKey());
      Character value = normalizeModeChar(entry.getValue());
      if (key == null || value == null) continue;
      out.put(key, value);
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static Map<Character, ModeArgPolicy> normalizePolicyMap(
      Map<Character, ModeArgPolicy> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<Character, ModeArgPolicy> out = new LinkedHashMap<>();
    for (Map.Entry<Character, ModeArgPolicy> entry : raw.entrySet()) {
      Character key = normalizeModeChar(entry.getKey());
      ModeArgPolicy policy = entry.getValue();
      if (key == null || policy == null) continue;
      out.put(key, policy);
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static Character normalizeModeChar(Character value) {
    if (value == null || Character.isWhitespace(value.charValue())) return null;
    return value;
  }

  private static String normalizeString(String raw) {
    return Objects.toString(raw, "").trim();
  }
}
