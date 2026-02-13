package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

@Component
public class CommandHistoryStore {

  private volatile int maxSize;
  private final Deque<String> history = new ArrayDeque<>();

  public CommandHistoryStore(@Lazy UiSettingsBus settingsBus) {
    this.maxSize = clampMax(settingsBus.get().commandHistoryMaxSize());
    settingsBus.addListener(this::onSettingsChanged);
  }

  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    Object v = evt.getNewValue();
    if (!(v instanceof UiSettings next)) return;
    setMaxSize(next.commandHistoryMaxSize());
  }

  private static int clampMax(int v) {
    if (v <= 0) return 500;
    return Math.min(v, 500);
  }

  public synchronized void setMaxSize(int maxSize) {
    this.maxSize = clampMax(maxSize);
    while (history.size() > this.maxSize) {
      history.removeFirst();
    }
  }

  public synchronized void add(String line) {
    if (line == null) return;
    String s = line.trim();
    if (s.isEmpty()) return;
    if ("/".equals(s)) return;
    if (looksSensitive(s)) return;

    String last = history.peekLast();
    if (last != null && last.equals(s)) return;

    history.addLast(s);
    while (history.size() > maxSize) {
      history.removeFirst();
    }
  }

  public synchronized List<String> snapshot() {
    return new ArrayList<>(history);
  }

  public synchronized int size() {
    return history.size();
  }

  public synchronized void clear() {
    history.clear();
  }

  private static boolean looksSensitive(String s) {
    ParsedCommand cmd = ParsedCommand.parse(s);
    if (cmd == null) return false;

    switch (cmd.name) {
      case "pass":
      case "password":
      case "oper":
      case "authenticate":
        return !cmd.rest.isEmpty();
      case "quote":
      case "raw":
        return startsWithAny(cmd.restLower, "pass ", "authenticate ", "oper ");
      case "msg":
        return isNickServIdentify(cmd.restLower);
      default:
        return containsAny(cmd.restLower, "token=", "apikey=", "api_key=", "access_token=", "refresh_token=");
    }
  }

  private static boolean isNickServIdentify(String restLower) {
    if (!restLower.startsWith("nickserv ")) return false;
    String after = restLower.substring("nickserv ".length()).trim();
    return after.startsWith("identify ") || after.equals("identify") || after.startsWith("id ");
  }

  private static boolean containsAny(String haystack, String... needles) {
    for (String n : needles) {
      if (haystack.contains(n)) return true;
    }
    return false;
  }

  private static boolean startsWithAny(String haystack, String... prefixes) {
    for (String p : prefixes) {
      if (haystack.startsWith(p)) return true;
    }
    return false;
  }

  private static final class ParsedCommand {
    final String name;
    final String rest;
    final String restLower;

    private ParsedCommand(String name, String rest) {
      this.name = name;
      this.rest = rest;
      this.restLower = rest.toLowerCase(Locale.ROOT);
    }

    static ParsedCommand parse(String line) {
      String s = line.trim();
      if (s.isEmpty()) return null;

      if (s.startsWith("/")) {
        String noSlash = s.substring(1).trim();
        if (noSlash.isEmpty()) return null;
        int sp = noSlash.indexOf(' ');
        String cmd = (sp < 0 ? noSlash : noSlash.substring(0, sp)).toLowerCase(Locale.ROOT);
        String rest = (sp < 0 ? "" : noSlash.substring(sp + 1).trim());
        return new ParsedCommand(cmd, rest);
      }

      int sp = s.indexOf(' ');
      String head = (sp < 0 ? s : s.substring(0, sp));
      if (!head.chars().allMatch(c -> c >= 'A' && c <= 'Z')) return null;
      String cmd = head.toLowerCase(Locale.ROOT);
      String rest = (sp < 0 ? "" : s.substring(sp + 1).trim());
      return new ParsedCommand(cmd, rest);
    }
  }
}
