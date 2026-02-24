package cafe.woden.ircclient.ui.nickcolors;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class NickColorOverrideEntryDialog {

  record Entry(String nickLower, String hex) {}

  static Optional<Entry> open(Window owner, String title, Entry seed) {
    if (!SwingUtilities.isEventDispatchThread()) {
      final Optional<Entry>[] box = new Optional[] {Optional.empty()};
      try {
        SwingUtilities.invokeAndWait(() -> box[0] = open(owner, title, seed));
      } catch (Exception ignored) {
      }
      return box[0];
    }

    JDialog dlg = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dlg.setLayout(new BorderLayout(10, 10));
    ((JPanel) dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JTextField nick = new JTextField(22);
    JTextField hex = new JTextField(10);

    nick.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "nick (case-insensitive)");
    hex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    final Color seedColor;
    if (seed != null) {
      nick.setText(Objects.toString(seed.nickLower(), ""));
      hex.setText(normalizeHex(Objects.toString(seed.hex(), "")));
      seedColor = parseHexColor(seed.hex());
    } else {
      seedColor = null;
    }

    JLabel preview = new JLabel();
    preview.setOpaque(true);
    preview.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    preview.setPreferredSize(new Dimension(120, 28));

    JButton pick = new JButton("Pick...");
    pick.addActionListener(
        e -> {
          Color current = parseHexColor(hex.getText());
          if (current == null) current = seedColor != null ? seedColor : Color.WHITE;
          Color chosen = JColorChooser.showDialog(dlg, "Choose Color", current);
          if (chosen != null) {
            hex.setText(toHex(chosen));
          }
        });

    // Layout
    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(8, 10, 8, 10);
    g.anchor = GridBagConstraints.WEST;
    g.fill = GridBagConstraints.HORIZONTAL;
    g.weightx = 1.0;

    g.gridx = 0;
    g.gridy = 0;
    form.add(new JLabel("Nick"), g);
    g.gridx = 1;
    form.add(nick, g);

    g.gridx = 0;
    g.gridy++;
    form.add(new JLabel("Color"), g);

    JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    colorRow.add(hex);
    colorRow.add(pick);
    colorRow.add(preview);

    g.gridx = 1;
    form.add(colorRow, g);

    dlg.add(form, BorderLayout.CENTER);

    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");
    ok.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(cancel);
    buttons.add(ok);
    dlg.add(buttons, BorderLayout.SOUTH);

    final Optional<Entry>[] result = new Optional[] {Optional.empty()};

    Runnable validate =
        () -> {
          String n = normalizeNickKey(nick.getText());
          Color c = parseHexColor(hex.getText());

          boolean valid = !n.isEmpty() && c != null;
          ok.setEnabled(valid);

          if (c != null) {
            preview.setBackground(c);
            preview.setText(normalizeHex(hex.getText()));
          } else {
            preview.setBackground(new Color(0, 0, 0, 0));
            preview.setText("Invalid color");
          }
        };

    nick.getDocument().addDocumentListener(new SimpleDocListener(validate));
    hex.getDocument().addDocumentListener(new SimpleDocListener(validate));

    cancel.addActionListener(
        e -> {
          result[0] = Optional.empty();
          dlg.dispose();
        });

    ok.addActionListener(
        e -> {
          String n = normalizeNickKey(nick.getText());
          Color c = parseHexColor(hex.getText());
          if (n.isEmpty() || c == null) {
            validate.run();
            return;
          }
          result[0] = Optional.of(new Entry(n, toHex(c)));
          dlg.dispose();
        });

    validate.run();

    dlg.setMinimumSize(new Dimension(520, 180));
    dlg.pack();
    dlg.setLocationRelativeTo(owner);
    dlg.setVisible(true);

    return result[0];
  }

  static String normalizeNickKey(String raw) {
    String n = Objects.toString(raw, "").trim();
    if (n.isEmpty()) return "";
    return n.toLowerCase(Locale.ROOT);
  }

  static String normalizeHex(String raw) {
    Color c = parseHexColor(raw);
    return c == null ? Objects.toString(raw, "").trim() : toHex(c);
  }

  static String toHex(Color c) {
    if (c == null) return "";
    return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
  }

  static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static final class SimpleDocListener implements DocumentListener {
    private final Runnable onChange;

    private SimpleDocListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      onChange.run();
    }
  }
}
