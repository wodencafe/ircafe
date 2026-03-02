package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Owns header button wiring and connection-control tooltip state for the server tree. */
public final class ServerTreeHeaderControls {

  private static final String CONNECT_TOOLTIP_BASE = "Connect all disconnected servers";
  private static final String DISCONNECT_TOOLTIP_BASE = "Disconnect connected/connecting servers";
  private static final int HEADER_BUTTON_SIZE = 26;

  private final JComponent owner;
  private final ServerDialogs serverDialogs;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;
  private final JButton addServerBtn = new JButton();
  private final JPanel panel;

  public ServerTreeHeaderControls(
      JComponent owner,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      ServerDialogs serverDialogs) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.connectBtn = Objects.requireNonNull(connectBtn, "connectBtn");
    this.disconnectBtn = Objects.requireNonNull(disconnectBtn, "disconnectBtn");
    this.serverDialogs = serverDialogs;
    configureButtons();
    this.panel = buildPanel();
  }

  public JPanel panel() {
    return panel;
  }

  public void setStatusText(String text) {
    String normalized = Objects.toString(text, "").trim();
    String suffix = normalized.isEmpty() ? "" : (" Current: " + normalized);
    connectBtn.setToolTipText(CONNECT_TOOLTIP_BASE + "." + suffix);
    disconnectBtn.setToolTipText(DISCONNECT_TOOLTIP_BASE + "." + suffix);
  }

  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    connectBtn.setEnabled(connectEnabled);
    disconnectBtn.setEnabled(disconnectEnabled);
  }

  private void configureButtons() {
    addServerBtn.setText("");
    addServerBtn.setIcon(SvgIcons.action("plus", 16));
    addServerBtn.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addServerBtn.setToolTipText("Add server");
    addServerBtn.setFocusable(false);
    addServerBtn.setPreferredSize(new Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE));
    addServerBtn.setEnabled(serverDialogs != null);
    addServerBtn.addActionListener(
        ev -> {
          if (serverDialogs == null) return;
          Window window = SwingUtilities.getWindowAncestor(owner);
          serverDialogs.openAddServer(window);
        });

    connectBtn.setText("");
    connectBtn.setIcon(SvgIcons.action("check", 16));
    connectBtn.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    connectBtn.setToolTipText(CONNECT_TOOLTIP_BASE);
    connectBtn.setFocusable(false);
    connectBtn.setPreferredSize(new Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE));

    disconnectBtn.setText("");
    disconnectBtn.setIcon(SvgIcons.action("exit", 16));
    disconnectBtn.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    disconnectBtn.setToolTipText(DISCONNECT_TOOLTIP_BASE);
    disconnectBtn.setFocusable(false);
    disconnectBtn.setPreferredSize(new Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE));
  }

  private JPanel buildPanel() {
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    header.add(addServerBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(connectBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(disconnectBtn);
    header.add(Box.createHorizontalGlue());
    return header;
  }
}
