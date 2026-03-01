package cafe.woden.ircclient.ui.servertree.view;

import java.util.Objects;

public enum ServerTreeTypingIndicatorStyle {
  DOTS,
  KEYBOARD,
  GLOW_DOT;

  public static ServerTreeTypingIndicatorStyle from(String raw) {
    String style = Objects.toString(raw, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (style.isEmpty()) return DOTS;
    return switch (style) {
      case "keyboard", "kbd" -> KEYBOARD;
      case "glow-dot", "glowdot", "dot", "green-dot", "glowing-green-dot" -> GLOW_DOT;
      default -> DOTS;
    };
  }
}
