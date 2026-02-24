package cafe.woden.ircclient.ui.chat;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Tracks per-server nick mention patterns. */
@Component
@Lazy
public class MentionPatternRegistry {

  /** IRC-ish nick characters, used to avoid highlighting inside words. */
  private static final String NICK_CHARS = "[A-Za-z0-9\\[\\]\\\\`_\\^\\{\\|\\}-]";

  private final ConcurrentHashMap<String, Pattern> byServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> nickLowerByServer = new ConcurrentHashMap<>();

  public void setCurrentNick(String serverId, String nick) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return;

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      byServer.remove(id);
      nickLowerByServer.remove(id);
      return;
    }

    nickLowerByServer.put(id, n.toLowerCase(Locale.ROOT));

    // Avoid highlighting inside words: (?<!nickChars)NICK(?!nickChars)
    String quoted = Pattern.quote(n);
    String regex = "(?i)(?<!" + NICK_CHARS + ")" + quoted + "(?!" + NICK_CHARS + ")";
    byServer.put(id, Pattern.compile(regex));
  }

  public String currentNickLower(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return null;
    return nickLowerByServer.get(id);
  }

  public Pattern get(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return null;
    return byServer.get(id);
  }
}
