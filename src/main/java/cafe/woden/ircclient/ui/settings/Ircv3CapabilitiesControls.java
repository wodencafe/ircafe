package cafe.woden.ircclient.ui.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

record Ircv3CapabilitiesControls(Map<String, JCheckBox> checkboxes, JPanel panel) {
  Map<String, Boolean> snapshot() {
    Map<String, Boolean> out = new LinkedHashMap<>();
    for (Map.Entry<String, JCheckBox> entry : checkboxes.entrySet()) {
      JCheckBox checkbox = entry.getValue();
      out.put(entry.getKey(), checkbox != null && checkbox.isSelected());
    }
    return out;
  }
}
