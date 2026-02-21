package cafe.woden.ircclient.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Objects;
import java.util.function.Function;

/**
 * Handles the small hint popup shown near the message input (e.g. "Tab -> nick").
 *
 * Responsibilities:
 *  - Determine the current token under the caret
 *  - Resolve a completion hint (via an injected matcher)
 *  - Own Popup + popup UI (panel/label)
 *  - Theme + positioning + refresh/hide lifecycle
 */
final class MessageInputHintPopupSupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputHintPopupSupport.class);

  private static final boolean DEBUG_HINT_POPUP = Boolean.getBoolean("ircafe.debug.hintPopup");
  private static final int HINT_POPUP_GAP_PX = 6;

  private final JComponent owner;
  private final JTextField input;
  private final Function<String, String> completionMatcher;

  private final JLabel hintPopupLabel = new JLabel();
  private final JPanel hintPopupPanel = new JPanel(new BorderLayout());
  private Popup hintPopup;

  private String hintPopupText = "";
  private String hintPopupShownText = "";
  private int hintPopupX = Integer.MIN_VALUE;
  private int hintPopupY = Integer.MIN_VALUE;

  private final ComponentAdapter hintAnchorListener = new ComponentAdapter() {
    @Override public void componentResized(ComponentEvent e) { refreshHintPopup(); }
    @Override public void componentMoved(ComponentEvent e) { refreshHintPopup(); }
    @Override public void componentShown(ComponentEvent e) { refreshHintPopup(); }
    @Override public void componentHidden(ComponentEvent e) { hideHintPopup(); }
  };

  private final HierarchyListener hierarchyListener;

  MessageInputHintPopupSupport(JComponent owner,
                              JTextField input,
                              Function<String, String> completionMatcher) {
    this.owner = owner;
    this.input = input;
    this.completionMatcher = completionMatcher;

    // Must be initialized after the blank-final fields above are assigned.
    this.hierarchyListener = evt -> {
      long flags = evt.getChangeFlags();
      if ((flags & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0) {
        if (this.owner.isShowing()) {
          refreshHintPopup();
        } else {
          hideHintPopup();
        }
      }
    };

    hintPopupPanel.setOpaque(true);
    // Border is (re)applied in applyHintPopupTheme() so it follows the active theme.
    hintPopupPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    hintPopupLabel.setOpaque(false);
    hintPopupLabel.setHorizontalAlignment(SwingConstants.LEFT);
    hintPopupPanel.add(hintPopupLabel, BorderLayout.CENTER);
    applyHintPopupTheme();
  }

  void installListeners() {
    // Match the previous behavior: listeners are installed once and live for the component lifetime.
    input.addComponentListener(hintAnchorListener);
    owner.addComponentListener(hintAnchorListener);
    owner.addHierarchyListener(hierarchyListener);
  }

  void hide() {
    hideHintPopup();
  }

  void onAppearanceChanged(Font chatFont) {
    if (chatFont != null) {
      hintPopupLabel.setFont(chatFont.deriveFont(Math.max(10f, chatFont.getSize2D() - 2f)));
    }
    applyHintPopupTheme();
    refreshHintPopup();
  }

  void updateHint() {
    if (!input.isEnabled() || !input.isEditable()) {
      clearHintPopup();
      return;
    }
    if (!input.hasFocus()) {
      clearHintPopup();
      return;
    }
    String text = input.getText();
    if (startsWithSlashCommand(text)) {
      clearHintPopup();
      return;
    }
    int caret = input.getCaretPosition();
    String token = currentToken(text, caret);
    if (token.isBlank()) {
      clearHintPopup();
      return;
    }
    String match = completionMatcher != null ? completionMatcher.apply(token) : null;
    if (match == null) {
      clearHintPopup();
    } else {
      showHintText("Tab -> " + match, true);
    }
  }

  private void showHintText(String rawText, boolean isCompletionHint) {
    String text = rawText == null ? "" : rawText.trim();
    if (text.isEmpty()) {
      clearHintPopup();
      return;
    }
    hintPopupText = text;
    hintPopupLabel.setText(text);
    if (DEBUG_HINT_POPUP && !Objects.equals(hintPopupShownText, text)) {
      log.info("[HintPopupDebug] hint=" + text);
    }
    hintPopupLabel.setToolTipText(isCompletionHint ? "Press Tab for nick completion" : null);
    refreshHintPopup();
  }

  private void clearHintPopup() {
    hintPopupText = "";
    hideHintPopup();
  }

  private void refreshHintPopup() {
    if (hintPopupText == null || hintPopupText.isBlank()) {
      hideHintPopup();
      return;
    }
    if (!owner.isShowing() || !input.isShowing()) {
      hideHintPopup();
      return;
    }
    try {
      Dimension pref = hintPopupPanel.getPreferredSize();
      Point anchor = input.getLocationOnScreen();

      GraphicsConfiguration gc = input.getGraphicsConfiguration();
      Rectangle screen = gc != null ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

      int x = anchor.x;
      int y = anchor.y - pref.height - HINT_POPUP_GAP_PX;
      if (y < screen.y) {
        y = anchor.y + input.getHeight() + HINT_POPUP_GAP_PX;
      }
      int maxX = screen.x + screen.width - pref.width;
      if (x > maxX) x = maxX;
      if (x < screen.x) x = screen.x;

      boolean unchanged = hintPopup != null
          && x == hintPopupX
          && y == hintPopupY
          && Objects.equals(hintPopupShownText, hintPopupText);
      if (unchanged) return;

      applyHintPopupTheme();
      hideHintPopup();
      hintPopup = PopupFactory.getSharedInstance().getPopup(owner, hintPopupPanel, x, y);
      hintPopup.show();
      if (DEBUG_HINT_POPUP) {
        log.info("[HintPopupDebug] show popup class={} at ({},{}) pref={} panel.bg={} label.fg={} panel.opaque={}",
            hintPopup != null ? hintPopup.getClass().getName() : "null",
            x, y,
            pref,
            colorToString(hintPopupPanel.getBackground()),
            colorToString(hintPopupLabel.getForeground()),
            hintPopupPanel.isOpaque());
      }
      hintPopupX = x;
      hintPopupY = y;
      hintPopupShownText = hintPopupText;
    } catch (IllegalComponentStateException ignored) {
      hideHintPopup();
    } catch (Exception ignored) {
      hideHintPopup();
    }
  }

  private void hideHintPopup() {
    if (hintPopup != null) {
      try {
        hintPopup.hide();
      } catch (Exception ignored) {
      }
      hintPopup = null;
    }
    hintPopupX = Integer.MIN_VALUE;
    hintPopupY = Integer.MIN_VALUE;
    hintPopupShownText = "";
  }

  private void applyHintPopupTheme() {
    // Base the hint styling on the *actual* input component, not generic TextPane defaults.
    Color textBg = input.getBackground();
    if (textBg == null) textBg = UIManager.getColor("TextField.background");
    if (textBg == null) textBg = UIManager.getColor("TextComponent.background");
    if (textBg == null) textBg = UIManager.getColor("Panel.background");
    if (textBg == null) textBg = new Color(245, 245, 245);

    Color textFg = input.getForeground();
    if (textFg == null) textFg = UIManager.getColor("TextField.foreground");
    if (textFg == null) textFg = UIManager.getColor("TextComponent.foreground");
    if (textFg == null) textFg = UIManager.getColor("Label.foreground");
    if (textFg == null) textFg = Color.DARK_GRAY;

    Color selBg = UIManager.getColor("TextField.selectionBackground");
    if (selBg == null) selBg = UIManager.getColor("TextComponent.selectionBackground");
    if (selBg == null) selBg = UIManager.getColor("List.selectionBackground");
    if (selBg == null) {
      try {
        selBg = input.getSelectionColor();
      } catch (Exception ignored) {
      }
    }

    // Subtle tint so the hint is distinct but still theme-native.
    Color hintBg = (selBg == null) ? textBg : mix(textBg, selBg, 0.18);

    Color border = UIManager.getColor("TextField.borderColor");

    if (DEBUG_HINT_POPUP) {
      try {
        log.info("[HintPopupDebug] LAF={} ({})",
            UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getName() : "null",
            UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getClass().getName() : "null");
        log.info("[HintPopupDebug] input.bg={} input.fg={} input.sel={} ",
            colorToString(input.getBackground()),
            colorToString(input.getForeground()),
            colorToString(input.getSelectionColor()));
        for (String k : new String[]{
            "TextField.background", "TextField.foreground", "TextField.selectionBackground",
            "TextPane.background", "TextPane.foreground",
            "TextComponent.background", "TextComponent.foreground", "TextComponent.selectionBackground",
            "Panel.background", "Label.foreground",
            "ToolTip.background", "ToolTip.foreground",
            "TextField.borderColor", "Component.borderColor", "Separator.foreground"
        }) {
          Object v = UIManager.get(k);
          if (v instanceof Color c) {
            log.info("[HintPopupDebug] UIManager[{}] = {}", k, colorToString(c));
          } else if (v != null) {
            log.info("[HintPopupDebug] UIManager[{}] = {} ({})", k, v, v.getClass().getName());
          } else {
            log.info("[HintPopupDebug] UIManager[{}] = null", k);
          }
        }
        log.info("[HintPopupDebug] computed hintBg={} border={}", colorToString(hintBg), colorToString(border));
      } catch (Exception e) {
        log.warn("[HintPopupDebug] failed to log UI values", e);
      }
    }
    if (border == null) border = UIManager.getColor("Component.borderColor");
    if (border == null) border = UIManager.getColor("Separator.foreground");
    if (border == null) border = new Color(textFg.getRed(), textFg.getGreen(), textFg.getBlue(), 120);

    hintPopupPanel.setBackground(hintBg);
    hintPopupLabel.setForeground(textFg);
    hintPopupPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(border),
        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
  }

  private static String currentToken(String text, int caretPos) {
    if (text == null || text.isEmpty()) return "";
    int caret = Math.max(0, Math.min(caretPos, text.length()));
    int start = caret;
    while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
      start--;
    }
    String token = text.substring(start, caret);
    token = stripLeadingPunct(token);
    token = stripTrailingPunct(token);
    return token;
  }

  private static boolean startsWithSlashCommand(String text) {
    if (text == null || text.isEmpty()) return false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) continue;
      return c == '/';
    }
    return false;
  }

  private static String stripLeadingPunct(String s) {
    if (s == null || s.isEmpty()) return "";
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '[' || c == ']' || c == '\\') {
        break;
      }
      i++;
    }
    return s.substring(i);
  }

  private static String stripTrailingPunct(String s) {
    if (s == null || s.isEmpty()) return "";
    int i = s.length() - 1;
    while (i >= 0) {
      char c = s.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '[' || c == ']' || c == '\\') {
        break;
      }
      i--;
    }
    return s.substring(0, i + 1);
  }

  private static String colorToString(Color c) {
    if (c == null) return "null";
    return String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
  }

  private static Color mix(Color a, Color b, double wb) {
    if (a == null) return b;
    if (b == null) return a;
    double w = Math.max(0.0, Math.min(1.0, wb));
    double wa = 1.0 - w;
    int r = (int) Math.round(a.getRed() * wa + b.getRed() * w);
    int g = (int) Math.round(a.getGreen() * wa + b.getGreen() * w);
    int bl = (int) Math.round(a.getBlue() * wa + b.getBlue() * w);
    return new Color(clampColor(r), clampColor(g), clampColor(bl));
  }

  private static int clampColor(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
