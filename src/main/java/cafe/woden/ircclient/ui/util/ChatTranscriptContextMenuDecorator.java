package cafe.woden.ircclient.ui.util;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Decorates a chat transcript {@link JTextComponent} with a simple right-click context menu:
 * <ul>
 *   <li>Copy</li>
 *   <li>Select All</li>
 *   <li>Find Text (opens the find bar)</li>
 * </ul>
 */
public final class ChatTranscriptContextMenuDecorator implements AutoCloseable {

  private final JTextComponent transcript;
  private final Runnable openFind;
  private final MouseAdapter mouse;

  private final JPopupMenu menu = new JPopupMenu();
  private final JMenuItem copyItem = new JMenuItem("Copy");
  private final JMenuItem selectAllItem = new JMenuItem("Select All");
  private final JMenuItem findItem = new JMenuItem("Find Text");

  private boolean closed = false;

  private ChatTranscriptContextMenuDecorator(JTextComponent transcript, Runnable openFind) {
    this.transcript = Objects.requireNonNull(transcript, "transcript");
    this.openFind = (openFind != null) ? openFind : () -> {};

    copyItem.addActionListener(this::onCopy);
    selectAllItem.addActionListener(this::onSelectAll);
    findItem.addActionListener(this::onFind);

    menu.add(copyItem);
    menu.add(selectAllItem);
    menu.addSeparator();
    menu.add(findItem);

    this.mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShow(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShow(e);
      }

      private void maybeShow(MouseEvent e) {
        if (closed) return;
        if (e == null) return;

        // Cross-platform: popup trigger may fire on pressed or released.
        if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
        if (!transcript.isShowing() || !transcript.isEnabled()) return;

        updateEnabledState();
        menu.show(transcript, e.getX(), e.getY());
      }
    };

    transcript.addMouseListener(this.mouse);
  }

  public static ChatTranscriptContextMenuDecorator decorate(JTextComponent transcript, Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(transcript, openFind);
  }

  private void updateEnabledState() {
    try {
      int start = transcript.getSelectionStart();
      int end = transcript.getSelectionEnd();
      copyItem.setEnabled(start != end);
    } catch (Exception ignored) {
      copyItem.setEnabled(true);
    }
  }

  private void onCopy(ActionEvent e) {
    try {
      transcript.copy();
    } catch (Exception ignored) {
    }
  }

  private void onSelectAll(ActionEvent e) {
    try {
      transcript.requestFocusInWindow();
      transcript.selectAll();
    } catch (Exception ignored) {
    }
  }

  private void onFind(ActionEvent e) {
    try {
      openFind.run();
    } catch (Exception ignored) {
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      transcript.removeMouseListener(mouse);
    } catch (Exception ignored) {
    }
  }
}
