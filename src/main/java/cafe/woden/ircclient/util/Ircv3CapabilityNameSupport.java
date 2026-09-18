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
          Ircv3CapabilityNames.READ_MARKER, Ircv3CapabilityNames.DRAFT_READ_MARKER,
          Ircv3CapabilityNames.DRAFT_READ_MARKER, Ircv3CapabilityNames.DRAFT_READ_MARKER,
          Ircv3CapabilityNames.MULTILINE, Ircv3CapabilityNames.DRAFT_MULTILINE,
          Ircv3CapabilityNames.DRAFT_MULTILINE, Ircv3CapabilityNames.DRAFT_MULTILINE,
          Ircv3CapabilityNames.CHATHISTORY, Ircv3CapabilityNames.DRAFT_CHATHISTORY,
          Ircv3CapabilityNames.DRAFT_CHATHISTORY, Ircv3CapabilityNames.DRAFT_CHATHISTORY,
          Ircv3CapabilityNames.EXTENDED_MONITOR, Ircv3CapabilityNames.EXTENDED_MONITOR,
          Ircv3CapabilityNames.DRAFT_EXTENDED_MONITOR, Ircv3CapabilityNames.EXTENDED_MONITOR,
          Ircv3CapabilityNames.MESSAGE_REDACTION, Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION,
          Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION,
              Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION);

  private static final Map<String, String> PREFERENCE_KEY_ALIASES =
      Map.ofEntries(
          entry(Ircv3CapabilityNames.READ_MARKER, Ircv3CapabilityNames.READ_MARKER),
          entry(Ircv3CapabilityNames.DRAFT_READ_MARKER, Ircv3CapabilityNames.READ_MARKER),
          entry(Ircv3CapabilityNames.MULTILINE, Ircv3CapabilityNames.MULTILINE),
          entry(Ircv3CapabilityNames.DRAFT_MULTILINE, Ircv3CapabilityNames.MULTILINE),
          entry(Ircv3CapabilityNames.CHATHISTORY, Ircv3CapabilityNames.CHATHISTORY),
          entry(Ircv3CapabilityNames.DRAFT_CHATHISTORY, Ircv3CapabilityNames.CHATHISTORY),
          entry(Ircv3CapabilityNames.EXTENDED_MONITOR, Ircv3CapabilityNames.EXTENDED_MONITOR),
          entry(Ircv3CapabilityNames.DRAFT_EXTENDED_MONITOR, Ircv3CapabilityNames.EXTENDED_MONITOR),
          entry(Ircv3CapabilityNames.MESSAGE_REDACTION, Ircv3CapabilityNames.MESSAGE_REDACTION),
          entry(
              Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION, Ircv3CapabilityNames.MESSAGE_REDACTION),
          entry(Ircv3CapabilityNames.REPLY, Ircv3CapabilityNames.REPLY),
          entry(Ircv3CapabilityNames.DRAFT_REPLY, Ircv3CapabilityNames.REPLY),
          entry(Ircv3CapabilityNames.REACT, Ircv3CapabilityNames.REACT),
          entry(Ircv3CapabilityNames.DRAFT_REACT, Ircv3CapabilityNames.REACT),
          entry(Ircv3CapabilityNames.UNREACT, Ircv3CapabilityNames.UNREACT),
          entry(Ircv3CapabilityNames.DRAFT_UNREACT, Ircv3CapabilityNames.UNREACT),
          entry(Ircv3CapabilityNames.TYPING, Ircv3CapabilityNames.TYPING),
          entry(Ircv3CapabilityNames.DRAFT_TYPING, Ircv3CapabilityNames.TYPING),
          entry(Ircv3CapabilityNames.CHANNEL_CONTEXT, Ircv3CapabilityNames.CHANNEL_CONTEXT),
          entry(Ircv3CapabilityNames.DRAFT_CHANNEL_CONTEXT, Ircv3CapabilityNames.CHANNEL_CONTEXT),
          entry(Ircv3CapabilityNames.MESSAGE_EDIT, Ircv3CapabilityNames.MESSAGE_EDIT),
          entry(Ircv3CapabilityNames.DRAFT_MESSAGE_EDIT, Ircv3CapabilityNames.MESSAGE_EDIT));

  private static final Set<String> NON_REQUESTABLE_TOKENS =
      Set.of(
          Ircv3CapabilityNames.STS,
          Ircv3CapabilityNames.MONITOR,
          Ircv3CapabilityNames.REPLY,
          Ircv3CapabilityNames.DRAFT_REPLY,
          Ircv3CapabilityNames.REACT,
          Ircv3CapabilityNames.DRAFT_REACT,
          Ircv3CapabilityNames.UNREACT,
          Ircv3CapabilityNames.DRAFT_UNREACT,
          Ircv3CapabilityNames.TYPING,
          Ircv3CapabilityNames.DRAFT_TYPING,
          Ircv3CapabilityNames.CHANNEL_CONTEXT,
          Ircv3CapabilityNames.DRAFT_CHANNEL_CONTEXT,
          Ircv3CapabilityNames.MESSAGE_EDIT,
          Ircv3CapabilityNames.DRAFT_MESSAGE_EDIT);

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
