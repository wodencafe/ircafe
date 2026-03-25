package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

final class RuleColorCellRenderer extends DefaultTableCellRenderer {
  @Override
  public java.awt.Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    JLabel c =
        (JLabel)
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    String raw = value != null ? value.toString().trim() : "";
    Color col = SettingsColorSupport.parseHexColor(raw);
    if (col == null && raw != null && raw.startsWith("#") && raw.length() == 4) {
      String s = raw.substring(1);
      char r = s.charAt(0), g = s.charAt(1), b = s.charAt(2);
      col = SettingsColorSupport.parseHexColor("#" + r + r + g + g + b + b);
    }

    if (col != null) {
      c.setIcon(new ColorSwatch(col, 12, 12));
      c.setText(SettingsColorSupport.toHex(col));
    } else {
      c.setIcon(null);
      c.setText(raw.isEmpty() ? "" : raw);
    }
    return c;
  }
}
