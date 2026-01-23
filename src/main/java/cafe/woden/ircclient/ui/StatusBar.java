package cafe.woden.ircclient.ui;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;

@Component
@Lazy
public class StatusBar extends JPanel {
  // TODO: Make these their own individual Spring components.
  private final JLabel channelLabel = new JLabel("Channel: -");
  private final JLabel usersLabel   = new JLabel("Users: 0");
  private final JLabel opsLabel     = new JLabel("Ops: 0");
  private final JLabel serverLabel  = new JLabel("Server: (disconnected)");

  public StatusBar() {
    super(new BorderLayout(12, 0));
    setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    left.add(channelLabel);
    left.add(usersLabel);
    left.add(opsLabel);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
    right.add(serverLabel);

    add(left, BorderLayout.WEST);
    add(right, BorderLayout.EAST);

    setPreferredSize(new Dimension(10, 26));
  }

  public void setChannel(String channel) {
    channelLabel.setText("Channel: " + (channel == null ? "-" : channel));
  }

  public void setCounts(int users, int ops) {
    usersLabel.setText("Users: " + users);
    opsLabel.setText("Ops: " + ops);
  }

  public void setServer(String serverText) {
    serverLabel.setText("Server: " + (serverText == null ? "(disconnected)" : serverText));
  }
}
