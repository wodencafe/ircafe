package cafe.woden.ircclient.state;

import cafe.woden.ircclient.state.api.ModeArgPolicy;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure parser for negotiated IRC mode grammar tokens from RPL_ISUPPORT. */
final class ModeVocabularyParser {
  private ModeVocabularyParser() {}

  static ModeVocabulary parse(Map<String, String> tokens) {
    Map<String, String> normalizedTokens = normalizeTokens(tokens);
    ModeVocabulary fallback = ModeVocabulary.fallback();

    ParsedPrefix prefix = parsePrefix(normalizedTokens.get("PREFIX"));
    ParsedChanModes chanModes = parseChanModes(normalizedTokens.get("CHANMODES"));

    Set<Character> statusModes = prefix.valid() ? prefix.statusModes() : fallback.statusModes();
    Set<Character> explicitStatusModes = prefix.valid() ? prefix.statusModes() : Set.of();
    Map<Character, Character> prefixByStatusMode =
        prefix.valid() ? prefix.prefixByStatusMode() : fallback.prefixByStatusMode();
    Map<Character, Character> statusModeByPrefix =
        prefix.valid() ? prefix.statusModeByPrefix() : fallback.statusModeByPrefix();
    String statusPrefixOrder =
        prefix.valid() ? prefix.statusPrefixOrder() : fallback.statusPrefixOrder();

    LinkedHashSet<Character> listModes =
        new LinkedHashSet<>(chanModes.valid() ? chanModes.listModes() : fallback.listModes());
    LinkedHashSet<Character> explicitListModes =
        new LinkedHashSet<>(chanModes.valid() ? chanModes.listModes() : Set.of());

    LinkedHashMap<Character, ModeArgPolicy> argPolicies =
        new LinkedHashMap<>(
            chanModes.valid() ? chanModes.argPolicyByMode() : fallback.argPolicyByMode());
    for (Character mode : statusModes) {
      if (mode == null) continue;
      argPolicies.put(mode, ModeArgPolicy.ALWAYS);
    }

    boolean explicitExceptsToken = normalizedTokens.containsKey("EXCEPTS");
    boolean explicitInvexToken = normalizedTokens.containsKey("INVEX");
    Character exceptsMode =
        resolveSemanticMode(
            normalizedTokens, "EXCEPTS", fallback.exceptsMode(), chanModes.valid(), listModes);
    Character invexMode =
        resolveSemanticMode(
            normalizedTokens, "INVEX", fallback.invexMode(), chanModes.valid(), listModes);
    if (explicitExceptsToken || !chanModes.valid()) {
      addListMode(listModes, explicitListModes, argPolicies, exceptsMode, explicitExceptsToken);
    }
    if (explicitInvexToken || !chanModes.valid()) {
      addListMode(listModes, explicitListModes, argPolicies, invexMode, explicitInvexToken);
    }

    String channelTypes =
        normalizedValue(normalizedTokens.get("CHANTYPES"), fallback.channelTypes());
    String statusMessagePrefixes =
        normalizedValue(normalizedTokens.get("STATUSMSG"), fallback.statusMessagePrefixes());
    String caseMapping =
        normalizedValue(normalizedTokens.get("CASEMAPPING"), fallback.caseMapping());

    return new ModeVocabulary(
        statusModes,
        explicitStatusModes,
        prefixByStatusMode,
        statusModeByPrefix,
        statusPrefixOrder,
        listModes,
        explicitListModes,
        argPolicies,
        channelTypes,
        statusMessagePrefixes,
        caseMapping,
        exceptsMode,
        invexMode);
  }

  private static void addListMode(
      Set<Character> listModes,
      Set<Character> explicitListModes,
      Map<Character, ModeArgPolicy> argPolicies,
      Character mode,
      boolean explicitTokenPresent) {
    if (mode == null) return;
    listModes.add(mode);
    argPolicies.put(mode, ModeArgPolicy.LIST);
    if (explicitTokenPresent) {
      explicitListModes.add(mode);
    }
  }

  private static Character resolveSemanticMode(
      Map<String, String> tokens,
      String tokenName,
      Character fallbackMode,
      boolean chanModesKnown,
      Set<Character> listModes) {
    if (!tokens.containsKey(tokenName)) {
      if (chanModesKnown) {
        return listModes.contains(fallbackMode) ? fallbackMode : null;
      }
      return fallbackMode;
    }
    String raw = Objects.toString(tokens.get(tokenName), "").trim();
    if (raw.isEmpty()) return fallbackMode;
    return raw.charAt(0);
  }

  private static ParsedPrefix parsePrefix(String rawValue) {
    String value = Objects.toString(rawValue, "").trim();
    if (value.isEmpty()) return ParsedPrefix.invalid();

    int open = value.indexOf('(');
    int close = value.indexOf(')', open + 1);
    if (open < 0 || close <= open + 1 || close >= value.length() - 1) {
      return ParsedPrefix.invalid();
    }

    String modeToken = value.substring(open + 1, close);
    String prefixToken = value.substring(close + 1);
    int len = Math.min(modeToken.length(), prefixToken.length());
    if (len <= 0) return ParsedPrefix.invalid();

    LinkedHashSet<Character> statusModes = new LinkedHashSet<>();
    LinkedHashMap<Character, Character> prefixByMode = new LinkedHashMap<>();
    LinkedHashMap<Character, Character> modeByPrefix = new LinkedHashMap<>();
    StringBuilder prefixOrder = new StringBuilder(len);

    for (int i = 0; i < len; i++) {
      char mode = modeToken.charAt(i);
      char prefix = prefixToken.charAt(i);
      if (Character.isWhitespace(mode) || Character.isWhitespace(prefix)) continue;
      statusModes.add(mode);
      prefixByMode.put(mode, prefix);
      modeByPrefix.put(prefix, mode);
      if (prefixOrder.indexOf(String.valueOf(prefix)) < 0) {
        prefixOrder.append(prefix);
      }
    }

    if (statusModes.isEmpty() || prefixOrder.length() == 0) return ParsedPrefix.invalid();
    return new ParsedPrefix(statusModes, prefixByMode, modeByPrefix, prefixOrder.toString(), true);
  }

  private static ParsedChanModes parseChanModes(String rawValue) {
    String value = Objects.toString(rawValue, "").trim();
    if (value.isEmpty()) return ParsedChanModes.invalid();

    String[] buckets = value.split(",", -1);
    if (buckets.length < 4) return ParsedChanModes.invalid();

    LinkedHashSet<Character> listModes = new LinkedHashSet<>();
    LinkedHashMap<Character, ModeArgPolicy> argPolicies = new LinkedHashMap<>();
    addBucket(buckets[0], ModeArgPolicy.LIST, listModes, argPolicies);
    addBucket(buckets[1], ModeArgPolicy.ALWAYS, null, argPolicies);
    addBucket(buckets[2], ModeArgPolicy.SET_ONLY, null, argPolicies);
    addBucket(buckets[3], ModeArgPolicy.NEVER, null, argPolicies);

    if (argPolicies.isEmpty()) return ParsedChanModes.invalid();
    return new ParsedChanModes(listModes, argPolicies, true);
  }

  private static void addBucket(
      String token,
      ModeArgPolicy policy,
      Set<Character> listModes,
      Map<Character, ModeArgPolicy> argPolicies) {
    String value = Objects.toString(token, "");
    for (int i = 0; i < value.length(); i++) {
      char mode = value.charAt(i);
      if (Character.isWhitespace(mode)) continue;
      argPolicies.put(mode, policy);
      if (policy == ModeArgPolicy.LIST && listModes != null) {
        listModes.add(mode);
      }
    }
  }

  private static String normalizedValue(String raw, String fallback) {
    String value = Objects.toString(raw, "").trim();
    return value.isEmpty() ? Objects.toString(fallback, "").trim() : value;
  }

  private static Map<String, String> normalizeTokens(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim().toUpperCase(java.util.Locale.ROOT);
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue() == null ? "" : entry.getValue().trim());
    }
    if (out.isEmpty()) return Map.of();
    return Map.copyOf(out);
  }

  private record ParsedPrefix(
      Set<Character> statusModes,
      Map<Character, Character> prefixByStatusMode,
      Map<Character, Character> statusModeByPrefix,
      String statusPrefixOrder,
      boolean valid) {
    private static ParsedPrefix invalid() {
      return new ParsedPrefix(Set.of(), Map.of(), Map.of(), "", false);
    }
  }

  private record ParsedChanModes(
      Set<Character> listModes, Map<Character, ModeArgPolicy> argPolicyByMode, boolean valid) {
    private static ParsedChanModes invalid() {
      return new ParsedChanModes(Set.of(), Map.of(), false);
    }
  }
}
