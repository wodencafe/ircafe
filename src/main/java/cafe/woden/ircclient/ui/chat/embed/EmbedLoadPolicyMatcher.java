package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.IgnoreMaskMatcher;
import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Evaluates whether inline image/link loading is allowed for a message URL. */
@Component
@Lazy
public class EmbedLoadPolicyMatcher {

  private static final long UNKNOWN_ACCOUNT_AGE_DAYS = -1L;
  private static final Set<Character> VOICE_OR_OP_PREFIXES = Set.of('+', '%', '@', '&', '~');

  private final EmbedLoadPolicyBus policyBus;
  private final UserListStore userListStore;

  public EmbedLoadPolicyMatcher(EmbedLoadPolicyBus policyBus, UserListStore userListStore) {
    this.policyBus = policyBus;
    this.userListStore = userListStore;
  }

  public boolean allow(
      TargetRef target, String fromNick, Map<String, String> ircv3Tags, String url) {
    String normalizedUrl = Objects.toString(url, "").trim();
    if (target == null || normalizedUrl.isEmpty()) return true;

    RuntimeConfigStore.EmbedLoadPolicySnapshot policy = policyBus.get();
    RuntimeConfigStore.EmbedLoadPolicyScope scope = policy.scopeForServer(target.serverId());
    if (scope == null || scope.isDefaultScope()) return true;

    SenderFacts sender = resolveSenderFacts(target, fromNick, ircv3Tags);
    String channel = target.isChannel() ? target.target() : "";

    if (!allowByUserRules(scope.userWhitelist(), scope.userBlacklist(), sender)) return false;
    if (!allowBySimpleRules(scope.channelWhitelist(), scope.channelBlacklist(), channel))
      return false;

    if (scope.requireVoiceOrOp() && !sender.voiceOrOp()) return false;
    if (scope.requireLoggedIn() && !sender.loggedIn()) return false;
    if (scope.minAccountAgeDays() > 0) {
      long ageDays = sender.accountAgeDays();
      if (ageDays < 0 || ageDays < scope.minAccountAgeDays()) return false;
    }

    if (!allowBySimpleRules(scope.linkWhitelist(), scope.linkBlacklist(), normalizedUrl))
      return false;

    String domain = extractDomain(normalizedUrl);
    if (!allowBySimpleRules(scope.domainWhitelist(), scope.domainBlacklist(), domain)) return false;

    return true;
  }

  private SenderFacts resolveSenderFacts(
      TargetRef target, String fromNick, Map<String, String> ircv3Tags) {
    String serverId = Objects.toString(target != null ? target.serverId() : "", "").trim();
    String nick = Objects.toString(fromNick, "").trim();
    String channel = target != null && target.isChannel() ? target.target() : "";

    NickInfo nickInfo = findNickInfo(serverId, channel, nick);
    String hostmask = "";
    boolean loggedIn = false;
    boolean voiceOrOp = false;

    if (nickInfo != null) {
      String hm = Objects.toString(nickInfo.hostmask(), "").trim();
      if (IgnoreMaskMatcher.isUsefulHostmask(hm)) {
        hostmask = hm;
      }
      String prefix = Objects.toString(nickInfo.prefix(), "");
      voiceOrOp = hasVoiceOrOp(prefix);
      AccountState accountState =
          nickInfo.accountState() == null ? AccountState.UNKNOWN : nickInfo.accountState();
      loggedIn = accountState == AccountState.LOGGED_IN;
    }

    if (hostmask.isBlank()) {
      String learned = userListStore.getLearnedHostmask(serverId, nick);
      if (IgnoreMaskMatcher.isUsefulHostmask(learned)) {
        hostmask = learned;
      }
    }

    String accountTag = firstTagValue(ircv3Tags, "account");
    if (!accountTag.isBlank()) {
      if ("*".equals(accountTag) || "0".equals(accountTag)) {
        loggedIn = false;
      } else {
        loggedIn = true;
      }
    }

    long accountAgeDays = parseAccountAgeDays(ircv3Tags);
    return new SenderFacts(nick, hostmask, loggedIn, voiceOrOp, accountAgeDays);
  }

  private NickInfo findNickInfo(String serverId, String channel, String nick) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return null;

    if (!ch.isEmpty()) {
      NickInfo ni = findNickInfoInRoster(userListStore.get(sid, ch), n);
      if (ni != null) return ni;
    }

    for (String serverChannel : userListStore.channelsContainingNick(sid, n)) {
      NickInfo ni = findNickInfoInRoster(userListStore.get(sid, serverChannel), n);
      if (ni != null) return ni;
    }
    return null;
  }

  private static NickInfo findNickInfoInRoster(List<NickInfo> roster, String nick) {
    if (roster == null || roster.isEmpty()) return null;
    String want = Objects.toString(nick, "").trim();
    if (want.isEmpty()) return null;
    for (NickInfo ni : roster) {
      if (ni == null) continue;
      String present = Objects.toString(ni.nick(), "").trim();
      if (!present.isEmpty() && present.equalsIgnoreCase(want)) {
        return ni;
      }
    }
    return null;
  }

  private static boolean allowByUserRules(
      List<String> whitelist, List<String> blacklist, SenderFacts sender) {
    String nick = sender.nick();
    String hostmask = sender.hostmask();

    if (matchesAnyUserPattern(blacklist, nick, hostmask)) return false;
    if (whitelist == null || whitelist.isEmpty()) return true;
    return matchesAnyUserPattern(whitelist, nick, hostmask);
  }

  private static boolean matchesAnyUserPattern(
      List<String> patterns, String nick, String hostmask) {
    if (patterns == null || patterns.isEmpty()) return false;
    for (String pattern : patterns) {
      if (matchesUserPattern(pattern, nick, hostmask)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesUserPattern(String rawPattern, String nick, String hostmask) {
    String pattern = Objects.toString(rawPattern, "").trim();
    if (pattern.isEmpty()) return false;

    UserMatchTarget target = UserMatchTarget.ANY;
    if (startsWithIgnoreCase(pattern, "nick:")) {
      target = UserMatchTarget.NICK;
      pattern = pattern.substring(5).trim();
    } else if (startsWithIgnoreCase(pattern, "host:")) {
      target = UserMatchTarget.HOST;
      pattern = pattern.substring(5).trim();
    }

    return switch (target) {
      case NICK -> matchesPattern(pattern, nick);
      case HOST -> matchesHostPattern(pattern, hostmask);
      case ANY -> matchesPattern(pattern, nick) || matchesHostPattern(pattern, hostmask);
    };
  }

  private static boolean allowBySimpleRules(
      List<String> whitelist, List<String> blacklist, String candidate) {
    if (matchesAnyPattern(blacklist, candidate)) return false;
    if (whitelist == null || whitelist.isEmpty()) return true;
    return matchesAnyPattern(whitelist, candidate);
  }

  private static boolean matchesAnyPattern(List<String> patterns, String candidate) {
    if (patterns == null || patterns.isEmpty()) return false;
    for (String pattern : patterns) {
      if (matchesPattern(pattern, candidate)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesPattern(String rawPattern, String candidate) {
    String pattern = Objects.toString(rawPattern, "").trim();
    String value = Objects.toString(candidate, "").trim();
    if (pattern.isEmpty() || value.isEmpty()) return false;

    if (startsWithIgnoreCase(pattern, "re:")) {
      return matchesRegex(pattern.substring(3).trim(), value);
    }
    if (startsWithIgnoreCase(pattern, "regex:")) {
      return matchesRegex(pattern.substring(6).trim(), value);
    }
    if (startsWithIgnoreCase(pattern, "glob:")) {
      pattern = pattern.substring(5).trim();
    }
    if (pattern.isEmpty()) return false;
    return IgnoreMaskMatcher.globMatches(pattern, value);
  }

  public static Optional<String> validatePatternSyntax(String rawPattern) {
    String pattern = Objects.toString(rawPattern, "").trim();
    if (pattern.isEmpty()) return Optional.empty();

    if (startsWithIgnoreCase(pattern, "nick:") || startsWithIgnoreCase(pattern, "host:")) {
      pattern = pattern.substring(5).trim();
    }
    if (pattern.isEmpty()) {
      return Optional.of("empty pattern");
    }

    if (startsWithIgnoreCase(pattern, "re:")) {
      return validateRegexPattern(pattern.substring(3).trim());
    }
    if (startsWithIgnoreCase(pattern, "regex:")) {
      return validateRegexPattern(pattern.substring(6).trim());
    }
    if (startsWithIgnoreCase(pattern, "glob:")) {
      pattern = pattern.substring(5).trim();
    }
    if (pattern.isEmpty()) {
      return Optional.of("empty pattern");
    }
    return Optional.empty();
  }

  private static boolean matchesHostPattern(String rawPattern, String hostmask) {
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;
    if (matchesPattern(rawPattern, hm)) return true;

    int at = hm.indexOf('@');
    if (at >= 0 && at < hm.length() - 1) {
      String hostOnly = hm.substring(at + 1).trim();
      if (matchesPattern(rawPattern, hostOnly)) return true;
    }
    return false;
  }

  private static boolean matchesRegex(String regexBody, String value) {
    String body = Objects.toString(regexBody, "").trim();
    if (body.isEmpty()) return false;
    try {
      return Pattern.compile(body, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
          .matcher(value)
          .find();
    } catch (Exception ignored) {
      return false;
    }
  }

  private static Optional<String> validateRegexPattern(String regexBody) {
    String body = Objects.toString(regexBody, "").trim();
    if (body.isEmpty()) return Optional.of("empty regex");
    try {
      Pattern.compile(body, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      return Optional.empty();
    } catch (Exception ex) {
      String message = Objects.toString(ex.getMessage(), "").trim();
      if (message.isEmpty()) {
        message = "invalid regex";
      }
      return Optional.of(message);
    }
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    String v = Objects.toString(value, "");
    String p = Objects.toString(prefix, "");
    if (v.length() < p.length()) return false;
    return v.regionMatches(true, 0, p, 0, p.length());
  }

  private static boolean hasVoiceOrOp(String prefix) {
    String p = Objects.toString(prefix, "");
    if (p.isEmpty()) return false;
    for (int i = 0; i < p.length(); i++) {
      if (VOICE_OR_OP_PREFIXES.contains(p.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static String extractDomain(String rawUrl) {
    String url = Objects.toString(rawUrl, "").trim();
    if (url.isEmpty()) return "";
    try {
      URI uri = URI.create(url);
      String host = Objects.toString(uri.getHost(), "").trim();
      if (host.isEmpty()) return "";
      return host.toLowerCase(Locale.ROOT);
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String firstTagValue(Map<String, String> tags, String key) {
    if (tags == null || tags.isEmpty()) return "";
    String wanted = normalizeTagKey(key);
    if (wanted.isEmpty()) return "";
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      String got = normalizeTagKey(entry.getKey());
      if (!wanted.equals(got)) continue;
      return Objects.toString(entry.getValue(), "").trim();
    }
    return "";
  }

  private static long parseAccountAgeDays(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return UNKNOWN_ACCOUNT_AGE_DAYS;

    long days = parsePositiveLong(firstAnyTagValue(tags, "account-age-days", "account_age_days"));
    if (days >= 0) return days;

    long seconds =
        parsePositiveLong(
            firstAnyTagValue(tags, "account-age-seconds", "account_age_seconds", "account-age"));
    if (seconds >= 0) {
      return seconds / 86_400L;
    }

    String createdRaw =
        firstAnyTagValue(
            tags,
            "account-created",
            "account_created",
            "account-ts",
            "account_ts",
            "account-registered",
            "account_registered");
    Instant createdAt = parseInstantLike(createdRaw);
    if (createdAt == null) return UNKNOWN_ACCOUNT_AGE_DAYS;
    long age = ChronoUnit.DAYS.between(createdAt, Instant.now());
    return age < 0 ? UNKNOWN_ACCOUNT_AGE_DAYS : age;
  }

  private static String firstAnyTagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      String value = firstTagValue(tags, key);
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static long parsePositiveLong(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return -1L;
    try {
      long parsed = Long.parseLong(value);
      return parsed < 0 ? -1L : parsed;
    } catch (Exception ignored) {
      return -1L;
    }
  }

  private static Instant parseInstantLike(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return null;

    Long numeric = null;
    try {
      numeric = Long.parseLong(value);
    } catch (Exception ignored) {
      numeric = null;
    }
    if (numeric != null) {
      if (numeric > 100_000_000_000L) {
        return Instant.ofEpochMilli(numeric);
      }
      return Instant.ofEpochSecond(numeric);
    }

    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String normalizeTagKey(String raw) {
    String k = Objects.toString(raw, "").trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
  }

  private enum UserMatchTarget {
    ANY,
    NICK,
    HOST
  }

  private record SenderFacts(
      String nick, String hostmask, boolean loggedIn, boolean voiceOrOp, long accountAgeDays) {}
}
