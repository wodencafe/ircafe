package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@Lazy
public class MessageInputDockable extends JPanel implements Dockable {
  public static final String ID = "input";

  private final JTextField input = new JTextField();
  private final JButton send = new JButton("Send");

  private final JLabel completionHint = new JLabel();

  private final DefaultCompletionProvider completionProvider = new DefaultCompletionProvider();
  private final AutoCompletion autoCompletion = new AutoCompletion(completionProvider);

  private volatile List<String> nickSnapshot = List.of();

  private final FlowableProcessor<String> outbound = PublishProcessor.<String>create().toSerialized();

  private final UiSettingsBus settingsBus;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  public MessageInputDockable(UiSettingsBus settingsBus) {
    super(new BorderLayout(8, 0));
    this.settingsBus = settingsBus;

    setPreferredSize(new Dimension(10, 34)); // width ignored by layout; height matters

    // Tab completion hint (right side, next to Send)
    completionHint.setText(" ");
    completionHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
    completionHint.setHorizontalAlignment(SwingConstants.RIGHT);
    completionHint.setToolTipText("Press Tab for nick completion");

    JPanel right = new JPanel(new BorderLayout(6, 0));
    right.setOpaque(false);
    right.add(completionHint, BorderLayout.CENTER);
    right.add(send, BorderLayout.EAST);

    add(input, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);

    // Allow Tab in the input field (we use it as the auto-complete trigger)
    input.setFocusTraversalKeysEnabled(false);

    // Auto-complete setup (bobbylight/AutoComplete)
    autoCompletion.setAutoActivationEnabled(false);
    autoCompletion.setParameterAssistanceEnabled(false);
    autoCompletion.setShowDescWindow(false);
    autoCompletion.setAutoCompleteSingleChoices(true);
    autoCompletion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    autoCompletion.install(input);

    // Keep the hint label updated as the user types.
    input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
    });
    input.addCaretListener(e -> updateHint());

    applySettings(settingsBus.get());

    // Press Enter
    input.addActionListener(e -> emit());
    // Click Send
    send.addActionListener(e -> emit());
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) settingsBus.addListener(settingsListener);
  }

  @Override
  public void removeNotify() {
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
    super.removeNotify();
  }

  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof UiSettings s) {
      applySettings(s);
    }
  }

  private void applySettings(UiSettings s) {
    if (s == null) return;
    try {
      Font f = new Font(s.chatFontFamily(), Font.PLAIN, s.chatFontSize());
      input.setFont(f);
      send.setFont(f);
      completionHint.setFont(f.deriveFont(Math.max(10f, f.getSize2D() - 2f)));
    } catch (Exception ignored) {
    }
  }

  /**
   * Updates the set of nick completions offered by the Tab completion popup.
   *
   */
  public void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;

    // Rebuild provider contents.
    completionProvider.clear();
    for (String nick : cleaned) {
      completionProvider.addCompletion(new BasicCompletion(completionProvider, nick, "IRC nick"));
    }

    updateHint();
  }

  private static List<String> cleanNickList(List<String> nicks) {
    if (nicks == null || nicks.isEmpty()) return List.of();

    ArrayList<String> out = new ArrayList<>(nicks.size());
    for (String n : nicks) {
      if (n == null) continue;
      String s = n.trim();
      if (s.isEmpty()) continue;
      out.add(s);
    }

    // Sort and de-dupe with case-insensitive order.
    out.sort(String.CASE_INSENSITIVE_ORDER);
    ArrayList<String> deduped = new ArrayList<>(out.size());
    String last = null;
    for (String s : out) {
      if (last == null || !s.equalsIgnoreCase(last)) {
        deduped.add(s);
        last = s;
      }
    }

    return Collections.unmodifiableList(deduped);
  }

  private void updateHint() {
    if (!input.isEnabled() || !input.isEditable()) {
      completionHint.setText(" ");
      return;
    }

    String text = input.getText();
    int caret = input.getCaretPosition();
    String token = currentToken(text, caret);
    if (token.isBlank()) {
      completionHint.setText(" ");
      return;
    }

    String match = firstNickStartingWith(token, nickSnapshot);
    if (match == null) {
      completionHint.setText(" ");
    } else {
      completionHint.setText("Tab → " + match);
    }
  }

  private static String currentToken(String text, int caretPos) {
    if (text == null || text.isEmpty()) return "";
    int caret = Math.max(0, Math.min(caretPos, text.length()));

    int start = caret;
    while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
      start--;
    }

    String token = text.substring(start, caret);
    // Allow completing when user types "@nick" or "nick," etc.
    token = stripLeadingPunct(token);
    token = stripTrailingPunct(token);
    return token;
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
    return i == 0 ? s : s.substring(i);
  }

  private static String stripTrailingPunct(String s) {
    if (s == null || s.isEmpty()) return "";
    int end = s.length();
    while (end > 0) {
      char c = s.charAt(end - 1);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '[' || c == ']' || c == '\\') {
        break;
      }
      end--;
    }
    return end == s.length() ? s : s.substring(0, end);
  }

  private static String firstNickStartingWith(String token, List<String> nicks) {
    if (token == null || token.isBlank()) return null;
    if (nicks == null || nicks.isEmpty()) return null;

    String t = token.toLowerCase(Locale.ROOT);
    for (String n : nicks) {
      if (n == null) continue;
      if (n.toLowerCase(Locale.ROOT).startsWith(t)) return n;
    }
    return null;
  }

  public Flowable<String> outboundMessages() {
    return outbound.onBackpressureBuffer();
  }

  private void emit() {
    String msg = input.getText().trim();
    if (msg.isEmpty()) return;
    input.setText("");
    outbound.onNext(msg);
  }

  public void setInputEnabled(boolean enabled) {
    input.setEditable(enabled);
    input.setEnabled(enabled);
    send.setEnabled(enabled);

    if (!enabled) {
      input.setText("");
    }

    updateHint();
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Input"; }
}
