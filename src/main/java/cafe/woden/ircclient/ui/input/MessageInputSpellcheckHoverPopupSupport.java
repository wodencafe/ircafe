package cafe.woden.ircclient.ui.input;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows a tiny hover popup with top spell-correction suggestions for misspelled words.
 *
 * <p>The popup is strictly best-effort: it only uses non-blocking suggestion lookups so mouse hover
 * never blocks the EDT.
 */
final class MessageInputSpellcheckHoverPopupSupport {

  private static final Logger log =
      LoggerFactory.getLogger(MessageInputSpellcheckHoverPopupSupport.class);

  private static final int MAX_HOVER_SUGGESTIONS = 3;
  private static final int POPUP_GAP_PX = 8;
  private static final int POPUP_BELOW_GAP_PX = 6;
  private static final int POPUP_BUTTON_HGAP_PX = 6;
  private static final int POPUP_BUTTON_VGAP_PX = 4;
  private static final int AUTO_HIDE_MS = 2500;
  private static final int FADE_INTERVAL_MS = 20;
  private static final float FADE_ALPHA_STEP = 0.2f;

  @FunctionalInterface
  interface PopupDisplay {
    Popup create(JComponent owner, JComponent content, int x, int y);
  }

  private final JComponent owner;
  private final JTextComponent input;
  private final MessageInputSpellcheckSupport spellcheckSupport;
  private final PopupDisplay popupDisplay;

  private final AlphaPanel popupPanel = new AlphaPanel();
  private final Timer fadeInTimer;
  private final Timer autoHideTimer;
  private final HierarchyListener hierarchyListener;

  private Popup popup;
  private int popupX = Integer.MIN_VALUE;
  private int popupY = Integer.MIN_VALUE;
  private boolean listenersInstalled;
  private MessageInputSpellcheckSupport.MisspelledWord shownWord;
  private List<String> shownSuggestions = List.of();
  private Font popupFont;
  private volatile SpellcheckSettings settings;

  private final MouseAdapter inputMouseListener =
      new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          onInputMouseMoved(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          onInputMouseMoved(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
          hidePopup();
        }
      };

  private final FocusAdapter inputFocusListener =
      new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          hidePopup();
        }
      };

  private final ComponentAdapter anchorListener =
      new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
          hidePopup();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          hidePopup();
        }

        @Override
        public void componentHidden(ComponentEvent e) {
          hidePopup();
        }
      };

  MessageInputSpellcheckHoverPopupSupport(
      JComponent owner,
      JTextComponent input,
      MessageInputSpellcheckSupport spellcheckSupport,
      SpellcheckSettings initialSettings) {
    this(
        owner,
        input,
        spellcheckSupport,
        initialSettings,
        (popupOwner, popupContent, x, y) ->
            PopupFactory.getSharedInstance().getPopup(popupOwner, popupContent, x, y));
  }

  MessageInputSpellcheckHoverPopupSupport(
      JComponent owner,
      JTextComponent input,
      MessageInputSpellcheckSupport spellcheckSupport,
      SpellcheckSettings initialSettings,
      PopupDisplay popupDisplay) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.input = Objects.requireNonNull(input, "input");
    this.spellcheckSupport = Objects.requireNonNull(spellcheckSupport, "spellcheckSupport");
    this.popupDisplay = Objects.requireNonNull(popupDisplay, "popupDisplay");
    this.settings = initialSettings != null ? initialSettings : SpellcheckSettings.defaults();
    this.hierarchyListener =
        evt -> {
          long flags = evt.getChangeFlags();
          if ((flags & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED))
              == 0) {
            return;
          }
          if (!this.owner.isShowing() || !this.input.isShowing()) {
            hidePopup();
          }
        };

    popupPanel.setLayout(
        new FlowLayout(FlowLayout.LEFT, POPUP_BUTTON_HGAP_PX, POPUP_BUTTON_VGAP_PX));
    popupPanel.setOpaque(true);
    popupPanel.setAlpha(1f);
    applyTheme();

    this.fadeInTimer =
        new Timer(
            FADE_INTERVAL_MS,
            e -> {
              float next = Math.min(1f, popupPanel.getAlpha() + FADE_ALPHA_STEP);
              popupPanel.setAlpha(next);
              if (next >= 1f && e.getSource() instanceof Timer timer) {
                timer.stop();
              }
            });
    this.fadeInTimer.setRepeats(true);

    this.autoHideTimer =
        new Timer(
            AUTO_HIDE_MS,
            e -> {
              hidePopup();
            });
    this.autoHideTimer.setRepeats(false);
  }

  void installListeners() {
    if (listenersInstalled) return;
    listenersInstalled = true;

    input.addMouseMotionListener(inputMouseListener);
    input.addMouseListener(inputMouseListener);
    input.addFocusListener(inputFocusListener);
    input.addComponentListener(anchorListener);
    owner.addComponentListener(anchorListener);
    owner.addHierarchyListener(hierarchyListener);
  }

  void onAppearanceChanged(Font chatFont) {
    popupFont =
        chatFont != null ? chatFont.deriveFont(Math.max(10f, chatFont.getSize2D() - 2f)) : null;
    applyTheme();
    hidePopup();
  }

  void onSettingsApplied(SpellcheckSettings next) {
    settings = next != null ? next : SpellcheckSettings.defaults();
    hidePopup();
  }

  void onDraftChanged() {
    hidePopup();
  }

  void onInputEnabledChanged(boolean enabled) {
    if (!enabled) {
      hidePopup();
    }
  }

  void shutdown() {
    if (listenersInstalled) {
      listenersInstalled = false;
      try {
        input.removeMouseMotionListener(inputMouseListener);
      } catch (Exception ignored) {
      }
      try {
        input.removeMouseListener(inputMouseListener);
      } catch (Exception ignored) {
      }
      try {
        input.removeFocusListener(inputFocusListener);
      } catch (Exception ignored) {
      }
      try {
        input.removeComponentListener(anchorListener);
      } catch (Exception ignored) {
      }
      try {
        owner.removeComponentListener(anchorListener);
      } catch (Exception ignored) {
      }
      try {
        owner.removeHierarchyListener(hierarchyListener);
      } catch (Exception ignored) {
      }
    }
    hidePopup();
  }

  private void onInputMouseMoved(MouseEvent e) {
    if (e == null || !input.isEnabled() || !input.isEditable() || !input.hasFocus()) {
      hidePopup();
      return;
    }
    if (!isHoverSuggestionsEnabled()) {
      hidePopup();
      return;
    }
    if (!input.isShowing() || !owner.isShowing()) {
      hidePopup();
      return;
    }

    int modelPos = -1;
    try {
      modelPos = input.viewToModel2D(e.getPoint());
    } catch (Exception ignored) {
      modelPos = -1;
    }
    if (modelPos < 0) {
      hidePopup();
      return;
    }

    Optional<MessageInputSpellcheckSupport.MisspelledWord> maybeWord =
        spellcheckSupport.misspelledWordAt(modelPos);
    if (maybeWord.isEmpty()) {
      hidePopup();
      return;
    }
    MessageInputSpellcheckSupport.MisspelledWord word = maybeWord.get();

    List<String> suggestions =
        spellcheckSupport.suggestionsForMisspelledWordNonBlocking(word, MAX_HOVER_SUGGESTIONS);
    if (suggestions.isEmpty()) {
      hidePopup();
      return;
    }

    showPopup(word, suggestions, e.getPoint());
  }

  private void showPopup(
      MessageInputSpellcheckSupport.MisspelledWord word,
      List<String> suggestions,
      Point inputPoint) {
    if (!isHoverSuggestionsEnabled()) {
      hidePopup();
      return;
    }
    if (word == null || suggestions == null || suggestions.isEmpty() || inputPoint == null) {
      hidePopup();
      return;
    }
    try {
      applyTheme();
      boolean contentChanged =
          popup == null
              || !sameWord(shownWord, word)
              || !Objects.equals(shownSuggestions, suggestions);
      if (contentChanged) {
        rebuildPopupContent(word, suggestions);
      }

      // Keep the popup position stable while hovering the same misspelled word so the user can
      // click a suggestion without the popup "chasing" cursor movement.
      boolean sameWordAsShown = popup != null && sameWord(shownWord, word);
      Point screenPos;
      if (sameWordAsShown) {
        screenPos = new Point(popupX, popupY);
      } else {
        Dimension pref = popupPanel.getPreferredSize();
        screenPos = resolvePopupScreenLocation(inputPoint, pref);
      }
      boolean unchanged =
          popup != null
              && !contentChanged
              && screenPos.x == popupX
              && screenPos.y == popupY
              && sameWord(shownWord, word)
              && Objects.equals(shownSuggestions, suggestions);
      if (unchanged) {
        restartAutoHideTimer();
        return;
      }

      hidePopupInternal(false);
      popup = popupDisplay.create(owner, popupPanel, screenPos.x, screenPos.y);
      popup.show();
      popupX = screenPos.x;
      popupY = screenPos.y;
      shownWord = word;
      shownSuggestions = List.copyOf(suggestions);

      if (contentChanged) {
        startFadeIn();
      } else {
        popupPanel.setAlpha(1f);
      }
      restartAutoHideTimer();
    } catch (IllegalComponentStateException ignored) {
      hidePopup();
    } catch (Exception ex) {
      log.debug("[MessageInputSpellcheckHoverPopupSupport] show popup failed", ex);
      hidePopup();
    }
  }

  void showSuggestionsAtScreen(
      MessageInputSpellcheckSupport.MisspelledWord word,
      List<String> suggestions,
      Point screenPos) {
    if (!isHoverSuggestionsEnabled()) {
      hidePopup();
      return;
    }
    if (word == null || suggestions == null || suggestions.isEmpty() || screenPos == null) {
      hidePopup();
      return;
    }
    try {
      applyTheme();
      boolean contentChanged =
          popup == null
              || !sameWord(shownWord, word)
              || !Objects.equals(shownSuggestions, suggestions);
      if (contentChanged) {
        rebuildPopupContent(word, suggestions);
      }

      boolean unchanged =
          popup != null
              && !contentChanged
              && screenPos.x == popupX
              && screenPos.y == popupY
              && sameWord(shownWord, word)
              && Objects.equals(shownSuggestions, suggestions);
      if (unchanged) {
        restartAutoHideTimer();
        return;
      }

      hidePopupInternal(false);
      popup = popupDisplay.create(owner, popupPanel, screenPos.x, screenPos.y);
      popup.show();
      popupX = screenPos.x;
      popupY = screenPos.y;
      shownWord = word;
      shownSuggestions = List.copyOf(suggestions);

      if (contentChanged) {
        startFadeIn();
      } else {
        popupPanel.setAlpha(1f);
      }
      restartAutoHideTimer();
    } catch (IllegalComponentStateException ignored) {
      hidePopup();
    } catch (Exception ex) {
      log.debug("[MessageInputSpellcheckHoverPopupSupport] show popup failed", ex);
      hidePopup();
    }
  }

  boolean isPopupVisible() {
    return popup != null;
  }

  private void rebuildPopupContent(
      MessageInputSpellcheckSupport.MisspelledWord word, List<String> suggestions) {
    popupPanel.removeAll();
    for (String suggestion : suggestions) {
      if (suggestion == null || suggestion.isBlank()) continue;
      JButton button = new JButton(suggestion);
      button.setFocusable(false);
      button.setFocusPainted(false);
      button.setMargin(new Insets(2, 8, 2, 8));
      button.addActionListener(
          e -> {
            boolean replaced = spellcheckSupport.replaceMisspelledWord(word, suggestion);
            hidePopup();
            if (replaced) {
              input.requestFocusInWindow();
            }
          });
      popupPanel.add(button);
    }
    applyTheme();
    popupPanel.revalidate();
    popupPanel.repaint();
  }

  private Point resolvePopupScreenLocation(Point inputPoint, Dimension pref) {
    Point anchor = input.getLocationOnScreen();
    GraphicsConfiguration gc = input.getGraphicsConfiguration();
    Rectangle screenBounds =
        gc != null ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

    int x = anchor.x + Math.max(0, inputPoint.x);
    int y = anchor.y + Math.max(0, inputPoint.y) - pref.height - POPUP_GAP_PX;
    if (y < screenBounds.y) {
      y = anchor.y + input.getHeight() + POPUP_BELOW_GAP_PX;
    }
    int minX = screenBounds.x;
    int minY = screenBounds.y;
    int maxX = screenBounds.x + screenBounds.width - pref.width;
    int maxY = screenBounds.y + screenBounds.height - pref.height;
    x = clamp(x, minX, maxX);
    y = clamp(y, minY, maxY);
    return new Point(x, y);
  }

  private void startFadeIn() {
    fadeInTimer.stop();
    popupPanel.setAlpha(0f);
    fadeInTimer.start();
  }

  private void restartAutoHideTimer() {
    autoHideTimer.restart();
  }

  private void hidePopup() {
    hidePopupInternal(true);
  }

  private void hidePopupInternal(boolean clearShownState) {
    fadeInTimer.stop();
    autoHideTimer.stop();
    if (popup != null) {
      try {
        popup.hide();
      } catch (Exception ignored) {
      }
      popup = null;
    }
    popupX = Integer.MIN_VALUE;
    popupY = Integer.MIN_VALUE;
    popupPanel.setAlpha(1f);
    if (clearShownState) {
      shownWord = null;
      shownSuggestions = List.of();
    }
  }

  private void applyTheme() {
    Color bg = input.getBackground();
    if (bg == null) bg = UIManager.getColor("TextField.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    if (bg == null) bg = Color.WHITE;

    Color fg = input.getForeground();
    if (fg == null) fg = UIManager.getColor("TextField.foreground");
    if (fg == null) fg = UIManager.getColor("Label.foreground");
    if (fg == null) fg = Color.BLACK;

    Color panelBg = blend(bg, fg, isDark(bg) ? 0.08f : 0.03f);
    Color border = blend(bg, fg, 0.25f);
    Color buttonBg = blend(panelBg, fg, isDark(bg) ? 0.16f : 0.1f);

    popupPanel.setBackground(panelBg);
    popupPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, 1, true),
            BorderFactory.createEmptyBorder(1, 3, 1, 3)));

    for (Component child : popupPanel.getComponents()) {
      if (!(child instanceof JButton button)) continue;
      button.setBackground(buttonBg);
      button.setForeground(fg);
      button.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(border, 1, true),
              BorderFactory.createEmptyBorder(2, 8, 2, 8)));
      if (popupFont != null) {
        button.setFont(popupFont);
      }
      if (button.getAccessibleContext() != null) {
        String text = button.getText();
        button.getAccessibleContext().setAccessibleName("Replace misspelled word with " + text);
        button
            .getAccessibleContext()
            .setAccessibleDescription("Replace misspelled word with " + text);
      }
    }
  }

  private boolean isHoverSuggestionsEnabled() {
    SpellcheckSettings current = settings;
    return current != null
        && current.enabled()
        && current.underlineEnabled()
        && current.hoverSuggestionsEnabled();
  }

  private static boolean sameWord(
      MessageInputSpellcheckSupport.MisspelledWord a,
      MessageInputSpellcheckSupport.MisspelledWord b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.start() == b.start() && a.end() == b.end() && Objects.equals(a.token(), b.token());
  }

  private static int clamp(int value, int min, int max) {
    if (max < min) return min;
    return Math.max(min, Math.min(max, value));
  }

  private static Color blend(Color a, Color b, float ratio) {
    Color c1 = a != null ? a : Color.WHITE;
    Color c2 = b != null ? b : Color.BLACK;
    float r = Math.max(0f, Math.min(1f, ratio));
    int red = Math.round(c1.getRed() * (1f - r) + c2.getRed() * r);
    int green = Math.round(c1.getGreen() * (1f - r) + c2.getGreen() * r);
    int blue = Math.round(c1.getBlue() * (1f - r) + c2.getBlue() * r);
    return new Color(red, green, blue);
  }

  private static boolean isDark(Color color) {
    if (color == null) return false;
    int luma = color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114;
    return luma < 128_000;
  }

  private static final class AlphaPanel extends JPanel {
    private float alpha = 1f;

    void setAlpha(float alpha) {
      this.alpha = Math.max(0f, Math.min(1f, alpha));
      repaint();
    }

    float getAlpha() {
      return alpha;
    }

    @Override
    public void paint(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        super.paint(g2);
      } finally {
        g2.dispose();
      }
    }
  }
}
