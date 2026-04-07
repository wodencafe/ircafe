package cafe.woden.ircclient.util;

import static java.util.Map.entry;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared normalization helpers for built-in IRCv3 capability names and aliases. */
public final class Ircv3CapabilityNameSupport {

  private static final Map<String, String> REQUEST_TOKEN_ALIASES =
      Map.of(
          "read-marker", "draft/read-marker",
          "draft/read-marker", "draft/read-marker",
          "multiline", "draft/multiline",
          "draft/multiline", "draft/multiline",
          "chathistory", "draft/chathistory",
          "draft/chathistory", "draft/chathistory",
          "message-redaction", "draft/message-redaction",
          "draft/message-redaction", "draft/message-redaction");

  private static final Map<String, String> PREFERENCE_KEY_ALIASES =
      Map.ofEntries(
          entry("read-marker", "read-marker"),
          entry("draft/read-marker", "read-marker"),
          entry("multiline", "multiline"),
          entry("draft/multiline", "multiline"),
          entry("chathistory", "chathistory"),
          entry("draft/chathistory", "chathistory"),
          entry("message-redaction", "message-redaction"),
          entry("draft/message-redaction", "message-redaction"),
          entry("reply", "reply"),
          entry("draft/reply", "reply"),
          entry("react", "react"),
          entry("draft/react", "react"),
          entry("unreact", "unreact"),
          entry("draft/unreact", "unreact"),
          entry("typing", "typing"),
          entry("draft/typing", "typing"),
          entry("channel-context", "channel-context"),
          entry("draft/channel-context", "channel-context"),
          entry("message-edit", "message-edit"),
          entry("draft/message-edit", "message-edit"));

  private static final Set<String> NON_REQUESTABLE_TOKENS =
      Set.of(
          "sts",
          "reply",
          "draft/reply",
          "react",
          "draft/react",
          "unreact",
          "draft/unreact",
          "typing",
          "draft/typing",
          "channel-context",
          "draft/channel-context",
          "message-edit",
          "draft/message-edit");

  private Ircv3CapabilityNameSupport() {}

  /**
   * Returns the built-in canonical preference key for an IRCv3 capability name.
   *
   * <p>Unknown names pass through in lowercase so callers can still persist additive extensions.
   */
  public static String normalizePreferenceKey(String capability) {
    String key = normalize(capability);
    if (key.isEmpty()) {
      return null;
    }
    return PREFERENCE_KEY_ALIASES.getOrDefault(key, key);
  }

  /**
   * Returns the built-in canonical CAP REQ token for an IRCv3 capability name.
   *
   * <p>Known non-requestable names return an empty string. Unknown names pass through in lowercase
   * so additive extensions can still be requested verbatim.
   */
  public static String normalizeRequestToken(String capability) {
    String key = normalize(capability);
    if (key.isEmpty()) {
      return "";
    }
    if (NON_REQUESTABLE_TOKENS.contains(key)) {
      return "";
    }
    return REQUEST_TOKEN_ALIASES.getOrDefault(key, key);
  }

  private static String normalize(String capability) {
    return Objects.toString(capability, "").trim().toLowerCase(Locale.ROOT);
  }
}
