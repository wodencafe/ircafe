package cafe.woden.ircclient.ui.ignore;

import cafe.woden.ircclient.ui.icons.SvgIcons;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Lightweight center-card launcher for per-server ignore management. */
public final class IgnoresPanel extends JPanel {

  private final JLabel serverLabel = new JLabel("Server: (none)");
  private final JButton openButton = new JButton("Open Ignore Lists...");
  private String serverId = "";
  private Consumer<String> openIgnoreDialogHandler = sid -> {};

  public IgnoresPanel() {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

    JLabel heading = new JLabel("Ignores");
    JLabel copy =
        new JLabel(
            "<html>Manage hard and soft ignore rules for the selected server.<br>"
                + "Hard ignores drop matching events. Soft ignores collapse matching lines.</html>");

    openButton.setIcon(SvgIcons.action("ban", 16));
    openButton.setDisabledIcon(SvgIcons.actionDisabled("ban", 16));
    openButton.setEnabled(false);
    openButton.addActionListener(
        e -> {
          String sid = Objects.toString(serverId, "").trim();
          if (!isValidServerId(sid)) return;
          openIgnoreDialogHandler.accept(sid);
        });

    JPanel stack = new JPanel();
    stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
    stack.add(heading);
    stack.add(Box.createVerticalStrut(8));
    stack.add(copy);
    stack.add(Box.createVerticalStrut(12));
    stack.add(serverLabel);
    stack.add(Box.createVerticalStrut(12));
    stack.add(openButton);

    add(stack, BorderLayout.NORTH);
  }

  public void setServerId(String serverId) {
    this.serverId = Objects.toString(serverId, "").trim();
    if (this.serverId.isEmpty()) {
      serverLabel.setText("Server: (none)");
      openButton.setEnabled(false);
      return;
    }
    serverLabel.setText("Server: " + this.serverId);
    openButton.setEnabled(isValidServerId(this.serverId));
  }

  public void setOnOpenIgnoreDialog(Consumer<String> handler) {
    this.openIgnoreDialogHandler = handler == null ? sid -> {} : handler;
  }

  private static boolean isValidServerId(String rawServerId) {
    String sid = Objects.toString(rawServerId, "").trim();
    if (sid.isEmpty()) return false;
    for (int i = 0; i < sid.length(); i++) {
      if (Character.isWhitespace(sid.charAt(i))) return false;
    }
    return true;
  }
}
