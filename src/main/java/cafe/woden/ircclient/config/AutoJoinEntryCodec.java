package cafe.woden.ircclient.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Encodes/decodes extra non-channel entries stored in {@code irc.servers[].autoJoin}.
 *
 * <p>We keep PM auto-open entries in the same list using a stable prefix so existing config wiring
 * remains unchanged.
 */
public final class AutoJoinEntryCodec {

  private static final String PM_PREFIX = "query:";

  private AutoJoinEntryCodec() {}

  public static boolean isPrivateMessageEntry(String raw) {
    return !decodePrivateMessageNick(raw).isEmpty();
  }

  public static String decodePrivateMessageNick(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return "";
    if (!s.regionMatches(true, 0, PM_PREFIX, 0, PM_PREFIX.length())) return "";
    String nick = s.substring(PM_PREFIX.length()).trim();
    return nick;
  }

  public static String encodePrivateMessageNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return "";
    return PM_PREFIX + n;
  }

  /**
   * Return channel (non-PM) auto-join entries, preserving order and case-insensitive uniqueness.
   */
  public static List<String> channelEntries(List<String> entries) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    if (entries != null) {
      for (String entry : entries) {
        String s = Objects.toString(entry, "").trim();
        if (s.isEmpty() || isPrivateMessageEntry(s)) continue;
        out.putIfAbsent(s.toLowerCase(Locale.ROOT), s);
      }
    }
    return new ArrayList<>(out.values());
  }

  /** Return PM nick entries, preserving order and case-insensitive uniqueness. */
  public static List<String> privateMessageNicks(List<String> entries) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    if (entries != null) {
      for (String entry : entries) {
        String nick = decodePrivateMessageNick(entry);
        if (nick.isEmpty()) continue;
        out.putIfAbsent(nick.toLowerCase(Locale.ROOT), nick);
      }
    }
    return new ArrayList<>(out.values());
  }
}
