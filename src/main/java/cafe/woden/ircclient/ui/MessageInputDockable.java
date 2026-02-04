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
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    setPreferredSize(new Dimension(10, 34));
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
    installInputContextMenu();
    input.setFocusTraversalKeysEnabled(false);
    autoCompletion.setAutoActivationEnabled(false);
    autoCompletion.setParameterAssistanceEnabled(false);
    autoCompletion.setShowDescWindow(false);
    autoCompletion.setAutoCompleteSingleChoices(true);
    autoCompletion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    autoCompletion.install(input);
    input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateHint(); }
    });
    input.addCaretListener(e -> updateHint());

    applySettings(settingsBus.get());
    input.addActionListener(e -> emit());
    send.addActionListener(e -> emit());
  }

  private void installInputContextMenu() {
    final JPopupMenu menu = new JPopupMenu();

    final JMenuItem cutItem = new JMenuItem("Cut");
    cutItem.addActionListener(e -> input.cut());

    final JMenuItem copyItem = new JMenuItem("Copy");
    copyItem.addActionListener(e -> input.copy());

    final JMenuItem pasteItem = new JMenuItem("Paste");
    pasteItem.addActionListener(e -> input.paste());

    final JMenuItem deleteItem = new JMenuItem("Delete");
    deleteItem.addActionListener(e -> {
      if (!input.isEditable() || !input.isEnabled()) return;

      int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
      int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
      try {
        if (start != end) {
          input.getDocument().remove(start, end - start);
          input.setCaretPosition(start);
        } else {
          int caret = input.getCaretPosition();
          int len = input.getDocument().getLength();
          if (caret < len) {
            input.getDocument().remove(caret, 1);
          } else {
            Toolkit.getDefaultToolkit().beep();
          }
        }
      } catch (BadLocationException ex) {
        Toolkit.getDefaultToolkit().beep();
      }
    });

    final JMenuItem clearItem = new JMenuItem("Clear");
    clearItem.addActionListener(e -> {
      if (!input.isEditable() || !input.isEnabled()) return;
      if (input.getDocument().getLength() == 0) return;
      input.setText("");
    });

    final JMenuItem selectAllItem = new JMenuItem("Select All");
    selectAllItem.addActionListener(e -> {
      if (!input.isEnabled()) return;
      input.selectAll();
    });

    menu.add(cutItem);
    menu.add(copyItem);
    menu.add(pasteItem);
    menu.addSeparator();
    menu.add(deleteItem);
    menu.add(clearItem);
    menu.addSeparator();
    menu.add(selectAllItem);

    final Runnable refreshEnabledStates = () -> {
      boolean enabled = input.isEnabled();
      boolean editable = enabled && input.isEditable();

      int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
      int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
      boolean hasSelection = start != end;

      int len = input.getDocument().getLength();
      int caret = input.getCaretPosition();

      cutItem.setEnabled(editable && hasSelection);
      copyItem.setEnabled(enabled && hasSelection);
      pasteItem.setEnabled(editable && clipboardHasText());
      deleteItem.setEnabled(editable && (hasSelection || caret < len));
      clearItem.setEnabled(editable && len > 0);
      selectAllItem.setEnabled(enabled && len > 0 && !(start == 0 && end == len));
    };

    menu.addPopupMenuListener(new PopupMenuListener() {
      @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { refreshEnabledStates.run(); }
      @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
      @Override public void popupMenuCanceled(PopupMenuEvent e) { }
    });

    // Use an explicit listener so enablement refreshes before showing.
    input.addMouseListener(new MouseAdapter() {
      private void maybeShow(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        if (!input.isShowing()) return;

        if (!input.hasFocus()) input.requestFocusInWindow();
        try {
          int pos = input.viewToModel2D(e.getPoint());
          if (pos >= 0) input.setCaretPosition(pos);
        } catch (Exception ignored) {
        }

        refreshEnabledStates.run();
        SwingUtilities.updateComponentTreeUI(menu);
        menu.show(e.getComponent(), e.getX(), e.getY());
      }

      @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
      @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
    });
  }

  private boolean clipboardHasText() {
    try {
      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (cb == null) return false;
      Transferable t = cb.getContents(null);
      return t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor);
    } catch (Exception ignored) {
      return false;
    }
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

  public void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;
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