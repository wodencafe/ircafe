package cafe.woden.ircclient.ui.terminal;

import io.github.andrewauclair.moderndocking.Dockable;
import jakarta.annotation.PreDestroy;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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

  private JMenuItem copyItem;

  private volatile AutoCloseable subscription;

  public TerminalDockable(ConsoleTeeService console) {
    super(new BorderLayout());
    this.console = console;

    area.setEditable(false);
    area.setLineWrap(false);
    area.setWrapStyleWord(false);
    area.setFont(defaultMonospaceFont());
    installContextMenu();

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

  private void installContextMenu() {
    JPopupMenu menu = new JPopupMenu();

    copyItem = new JMenuItem("Copy");
    copyItem.addActionListener(e -> area.copy());
    copyItem.setEnabled(false);

    JMenuItem selectAll = new JMenuItem("Select All");
    selectAll.addActionListener(e -> area.selectAll());

    JMenuItem clear = new JMenuItem("Clear");
    clear.addActionListener(e -> area.setText(""));

    JMenuItem save = new JMenuItem("Save to file...");
    save.addActionListener(e -> saveToFile());

    menu.add(copyItem);
    menu.add(selectAll);
    menu.addSeparator();
    menu.add(clear);
    menu.add(save);

    // Keep "Copy" enabled only when there is a selection.
    menu.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        boolean hasSelection = area.getSelectionStart() != area.getSelectionEnd();
        copyItem.setEnabled(hasSelection);
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });

    area.setComponentPopupMenu(menu);
  }

  private void saveToFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Save terminal output");
    chooser.setSelectedFile(new File(System.getProperty("user.home"), defaultFileName()));

    int result = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this));
    if (result != JFileChooser.APPROVE_OPTION) return;

    File file = chooser.getSelectedFile();
    if (file == null) return;

    // If the user didn't specify an extension, default to .log.
    String name = file.getName();
    if (!name.contains(".")) {
      File parent = file.getParentFile();
      file = (parent != null) ? new File(parent, name + ".log") : new File(name + ".log");
    }

    try {
      Files.writeString(
          file.toPath(),
          area.getText(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);

      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Saved terminal output to:\n" + file.getAbsolutePath(),
          "Saved",
          JOptionPane.INFORMATION_MESSAGE);
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Failed to save terminal output:\n" + ex.getMessage(),
          "Save failed",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private String defaultFileName() {
    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    return "ircafe-terminal-" + ts + ".log";
  }

  private static Font defaultMonospaceFont() {
    Font base = UIManager.getFont("TextArea.font");
    if (base == null) base = UIManager.getFont("TextPane.font");
    if (base == null) base = UIManager.getFont("Label.font");
    int size = base != null ? base.getSize() : 12;
    return new Font(Font.MONOSPACED, Font.PLAIN, Math.max(10, size));
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
