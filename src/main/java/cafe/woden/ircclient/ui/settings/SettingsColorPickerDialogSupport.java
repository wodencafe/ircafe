package cafe.woden.ircclient.ui.settings;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;

final class SettingsColorPickerDialogSupport {
  private static final int MAX_RECENT_COLORS = 12;
  private static final Deque<String> RECENT_COLOR_HEX = new ArrayDeque<>();

  private SettingsColorPickerDialogSupport() {}

  static Color showColorPickerDialog(
      Window owner, String title, Color initial, Color previewBackground) {
    Color bg =
        previewBackground != null
            ? previewBackground
            : SettingsColorSupport.preferredPreviewBackground();
    Color init = initial != null ? initial : Color.WHITE;

    final JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    final Color[] current = new Color[] {init};
    final Color[] result = new Color[1];

    JLabel preview = new JLabel(" IRCafe preview ");
    preview.setOpaque(true);
    preview.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    preview.setBackground(bg);

    JLabel contrast = new JLabel();
    contrast.setFont(UIManager.getFont("Label.smallFont"));

    JTextField hex = new JTextField(SettingsColorSupport.toHex(init), 10);
    hex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JLabel hexStatus = new JLabel(" ");
    hexStatus.setFont(UIManager.getFont("Label.smallFont"));

    JButton more = new JButton("More…");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    final boolean[] internalUpdate = new boolean[] {false};

    Runnable updatePreview =
        () -> {
          Color fg = current[0];
          preview.setForeground(fg);
          preview.setText(" IRCafe preview  " + SettingsColorSupport.toHex(fg));
          double cr = SettingsColorSupport.contrastRatio(fg, bg);
          String verdict = cr >= 4.5 ? "OK" : (cr >= 3.0 ? "Low" : "Bad");
          contrast.setText(String.format(Locale.ROOT, "Contrast: %.1f (%s)", cr, verdict));
          ok.setEnabled(fg != null);
        };

    Consumer<Color> setColor =
        c -> {
          if (c == null) return;
          current[0] = c;
          internalUpdate[0] = true;
          hex.setText(SettingsColorSupport.toHex(c));
          internalUpdate[0] = false;
          hexStatus.setText(" ");
          updatePreview.run();
        };

    hex.getDocument()
        .addDocumentListener(
            new DocChangeListener(
                () -> {
                  if (internalUpdate[0]) return;

                  Color parsed = SettingsColorSupport.parseHexColorLenient(hex.getText());
                  if (parsed == null) {
                    hexStatus.setText("Invalid hex (use #RRGGBB or #RGB)");
                    ok.setEnabled(false);
                    return;
                  }
                  current[0] = parsed;
                  hexStatus.setText(" ");
                  updatePreview.run();
                }));

    JPanel palette = new JPanel(new MigLayout("insets 0, wrap 8, gap 6", "[]", "[]"));
    Color[] colors =
        new Color[] {
          new Color(0xFFFFFF), new Color(0xD9D9D9), new Color(0xA6A6A6), new Color(0x4D4D4D),
              new Color(0x000000), new Color(0xFF6B6B), new Color(0xFFA94D), new Color(0xFFD43B),
          new Color(0x69DB7C), new Color(0x38D9A9), new Color(0x22B8CF), new Color(0x4DABF7),
              new Color(0x748FFC), new Color(0x9775FA), new Color(0xDA77F2), new Color(0xF783AC),
          new Color(0xC92A2A), new Color(0xE8590C), new Color(0xF08C00), new Color(0x2F9E44),
              new Color(0x0CA678), new Color(0x1098AD), new Color(0x1971C2), new Color(0x5F3DC4)
        };
    for (Color c : colors) {
      palette.add(colorSwatchButton(c, setColor));
    }

    JPanel recent = new JPanel(new MigLayout("insets 0, wrap 8, gap 6", "[]", "[]"));
    Runnable refreshRecent =
        () -> {
          recent.removeAll();
          List<String> rec = snapshotRecentColorHex();
          if (rec.isEmpty()) {
            recent.add(helpText("No recent colors yet."), "span 8");
          } else {
            for (String hx : rec) {
              Color c = SettingsColorSupport.parseHexColorLenient(hx);
              if (c == null) continue;
              recent.add(colorSwatchButton(c, setColor));
            }
          }
          recent.revalidate();
          recent.repaint();
        };
    refreshRecent.run();

    more.addActionListener(
        e -> {
          Color picked =
              JColorChooser.showDialog(d, "More Colors", current[0] != null ? current[0] : init);
          if (picked != null) setColor.accept(picked);
        });

    ok.addActionListener(
        e -> {
          if (current[0] == null) return;
          result[0] = current[0];
          rememberRecentColorHex(SettingsColorSupport.toHex(current[0]));
          d.dispose();
        });

    cancel.addActionListener(
        e -> {
          result[0] = null;
          d.dispose();
        });

    JPanel content =
        new JPanel(
            new MigLayout(
                "insets 12, fillx, wrap 2", "[grow,fill]12[grow,fill]", "[]10[]6[]10[]6[]10[]"));
    content.add(preview, "span 2, growx, wrap");
    content.add(contrast, "span 2, growx, wrap");

    content.add(new JLabel("Hex"));
    JPanel hexRow =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]6[nogrid]6[nogrid]", "[]2[]"));
    hexRow.setOpaque(false);
    hexRow.add(hex, "w 110!");
    hexRow.add(more);
    hexRow.add(new JLabel(), "push");
    hexRow.add(hexStatus, "span 3, growx");
    content.add(hexRow, "growx, wrap");

    content.add(new JLabel("Palette"), "aligny top");
    content.add(palette, "growx, wrap");

    content.add(new JLabel("Recent"), "aligny top");
    content.add(recent, "growx, wrap");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.add(cancel);
    buttons.add(ok);

    JPanel outer = new JPanel(new BorderLayout());
    outer.add(content, BorderLayout.CENTER);
    outer.add(buttons, BorderLayout.SOUTH);

    d.setContentPane(outer);
    d.getRootPane().setDefaultButton(ok);
    d.getRootPane()
        .registerKeyboardAction(
            ev -> cancel.doClick(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

    updatePreview.run();
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);

    return result[0];
  }

  private static void rememberRecentColorHex(String hex) {
    if (hex == null) return;
    String s = hex.trim().toUpperCase(Locale.ROOT);
    if (s.isEmpty()) return;
    if (!s.startsWith("#")) s = "#" + s;
    if (s.length() == 4) {
      char r = s.charAt(1);
      char g = s.charAt(2);
      char b = s.charAt(3);
      s = "#" + r + r + g + g + b + b;
    }
    if (s.length() != 7) return;

    final String needle = s;

    synchronized (RECENT_COLOR_HEX) {
      RECENT_COLOR_HEX.removeIf(v -> v != null && v.equalsIgnoreCase(needle));
      RECENT_COLOR_HEX.addFirst(needle);
      while (RECENT_COLOR_HEX.size() > MAX_RECENT_COLORS) {
        RECENT_COLOR_HEX.removeLast();
      }
    }
  }

  private static List<String> snapshotRecentColorHex() {
    synchronized (RECENT_COLOR_HEX) {
      return new ArrayList<>(RECENT_COLOR_HEX);
    }
  }

  private static JButton colorSwatchButton(Color c, Consumer<Color> onPick) {
    JButton b = new JButton();
    b.setFocusable(false);
    b.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    b.setContentAreaFilled(false);
    b.setIcon(new ColorSwatch(c, 18, 18));
    b.setToolTipText(SettingsColorSupport.toHex(c));
    b.addActionListener(
        e -> {
          if (onPick != null) onPick.accept(c);
        });
    return b;
  }

  private static JTextArea helpText(String text) {
    JTextArea t = new JTextArea(text);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);
    t.setFont(UIManager.getFont("Label.font"));
    t.setForeground(UIManager.getColor("Label.foreground"));
    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }

  private static final class DocChangeListener implements DocumentListener {
    private final Runnable onChange;

    private DocChangeListener(Runnable onChange) {
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
