package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.NickColorService;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

final class NickColorPreviewPanel extends JPanel {
  private static final String[] SAMPLE_NICKS =
      new String[] {"Alice", "Bob", "Carol", "Dave", "Eve", "Mallory"};

  private final NickColorService nickColorService;
  private final List<JLabel> labels = new ArrayList<>();

  NickColorPreviewPanel(NickColorService nickColorService) {
    super(new FlowLayout(FlowLayout.LEFT, 10, 6));
    this.nickColorService = nickColorService;

    setOpaque(true);
    Color border = UIManager.getColor("Component.borderColor");
    if (border == null) border = UIManager.getColor("Separator.foreground");
    if (border == null) border = UIManager.getColor("Label.foreground");
    if (border == null) border = Color.GRAY;

    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));

    for (String nick : SAMPLE_NICKS) {
      JLabel label = new JLabel(nick);
      label.setFont(label.getFont().deriveFont(Font.BOLD));
      labels.add(label);
      add(label);
    }
  }

  void updatePreview(boolean enabled, double minContrast) {
    Color background = UIManager.getColor("TextPane.background");
    if (background == null) background = UIManager.getColor("Panel.background");
    if (background == null) background = Color.WHITE;

    Color foreground = UIManager.getColor("TextPane.foreground");
    if (foreground == null) foreground = UIManager.getColor("Label.foreground");
    if (foreground == null) foreground = Color.BLACK;

    setBackground(background);

    for (int i = 0; i < labels.size(); i++) {
      JLabel label = labels.get(i);
      String nick = SAMPLE_NICKS[i];

      Color color =
          nickColorService != null
              ? nickColorService.previewColorForNick(
                  nick, background, foreground, enabled, minContrast)
              : foreground;

      label.setForeground(color);
    }

    repaint();
  }
}
