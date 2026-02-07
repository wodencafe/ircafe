package cafe.woden.ircclient.ui.terminal;

import io.github.andrewauclair.moderndocking.Dockable;
import jakarta.annotation.PreDestroy;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * A simple in-app "terminal" dock that mirrors whatever is printed to the real console.
 */
@Component
@Lazy
public class TerminalDockable extends JPanel implements Dockable {

  public static final String ID = "terminal";

  private static final int MAX_DOC_CHARS = 1_000_000; // Keep last ~1MB in the UI too.

  private final ConsoleTeeService console;
  private final JTextArea area = new JTextArea();
  private final JCheckBox followTail = new JCheckBox("Follow", true);

  private volatile AutoCloseable subscription;

  public TerminalDockable(ConsoleTeeService console) {
    super(new BorderLayout());
    this.console = console;

    area.setEditable(false);
    area.setLineWrap(false);
    area.setWrapStyleWord(false);
    area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    JScrollPane scroll = new JScrollPane(area);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);

    JToolBar tb = new JToolBar();
    tb.setFloatable(false);
    tb.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    JButton clear = new JButton(new AbstractAction("Clear") {
      @Override
      public void actionPerformed(ActionEvent e) {
        area.setText("");
      }
    });
    clear.setFocusable(false);
    followTail.setFocusable(false);

    tb.add(clear);
    tb.addSeparator();
    tb.add(followTail);
    add(tb, BorderLayout.NORTH);

    // Seed with whatever has already been printed.
    String initial = (console == null) ? "" : console.snapshot();
    if (initial != null && !initial.isBlank()) {
      area.setText(initial);
      area.setCaretPosition(area.getDocument().getLength());
    }
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Terminal";
  }

  @Override
  public void addNotify() {
    super.addNotify();
    attach();
  }

  @Override
  public void removeNotify() {
    detach();
    super.removeNotify();
  }

  @PreDestroy
  public void shutdown() {
    detach();
  }

  private void attach() {
    if (subscription != null) return;
    if (console == null) return;

    subscription = console.addListener(this::appendOnEdt);
  }

  private void detach() {
    AutoCloseable sub = subscription;
    subscription = null;
    if (sub == null) return;
    try {
      sub.close();
    } catch (Exception ignored) {
    }
  }

  private void appendOnEdt(String text) {
    if (text == null || text.isEmpty()) return;
    if (SwingUtilities.isEventDispatchThread()) {
      append(text);
    } else {
      SwingUtilities.invokeLater(() -> append(text));
    }
  }

  private void append(String text) {
    Document doc = area.getDocument();
    try {
      doc.insertString(doc.getLength(), text, null);
    } catch (BadLocationException ignored) {
      area.append(text);
    }

    trimDocIfNeeded(doc);
    if (followTail.isSelected()) {
      area.setCaretPosition(doc.getLength());
    }
  }

  private void trimDocIfNeeded(Document doc) {
    int len = doc.getLength();
    int over = len - MAX_DOC_CHARS;
    if (over <= 0) return;

    // Drop a little extra to reduce churn.
    int drop = over + Math.min(16_384, MAX_DOC_CHARS / 10);
    drop = Math.min(drop, len);
    try {
      doc.remove(0, drop);
    } catch (BadLocationException ignored) {
      // Best effort.
    }
  }
}
