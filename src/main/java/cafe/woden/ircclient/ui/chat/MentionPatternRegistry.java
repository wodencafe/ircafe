package cafe.woden.ircclient.ui.chat;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tracks per-server nick mention patterns.
 *
 * <p>We store a compiled regex for the current nick so renderers can highlight
 * mentions without depending on whichever chat view is currently active.
 */
@Component
@Lazy
public class MentionPatternRegistry {

  /** IRC-ish nick characters, used to avoid highlighting inside words. */
  private static final String NICK_CHARS = "[A-Za-z0-9\\[\\]\\\\`_\\^\\{\\|\\}-]";

  private final ConcurrentHashMap<String, Pattern> byServer = new ConcurrentHashMap<>();

  public void setCurrentNick(String serverId, String nick) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return;

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      byServer.remove(id);
      return;
    }

    // Avoid highlighting inside words: (?<!nickChars)NICK(?!nickChars)
    String quoted = Pattern.quote(n);
    String regex = "(?i)(?<!" + NICK_CHARS + ")" + quoted + "(?!" + NICK_CHARS + ")";
    byServer.put(id, Pattern.compile(regex));
  }

  public Pattern get(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return null;
    return byServer.get(id);
  }
}
