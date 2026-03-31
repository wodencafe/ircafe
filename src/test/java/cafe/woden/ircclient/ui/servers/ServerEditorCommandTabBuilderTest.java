package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class ServerEditorCommandTabBuilderTest {

  @Test
  void buildAutoJoinPanelWrapsAndStylesBothAreas() {
    JTextArea autoJoinArea = new JTextArea();
    JTextArea autoJoinPmArea = new JTextArea();

    JPanel panel =
        ServerEditorCommandTabBuilder.buildAutoJoinPanel(
            new ServerEditorCommandTabBuilder.AutoJoinWidgets(autoJoinArea, autoJoinPmArea));

    assertTrue(autoJoinArea.getLineWrap());
    assertTrue(autoJoinArea.getWrapStyleWord());
    assertTrue(autoJoinPmArea.getLineWrap());
    assertTrue(autoJoinPmArea.getWrapStyleWord());
    assertEquals(2, countComponentsOfType(panel, JScrollPane.class));
    assertEquals(
        "foreground:$Label.disabledForeground",
        ((JLabel) panel.getComponent(0)).getClientProperty(FlatClientProperties.STYLE));
    assertTrue(panel.isAncestorOf(autoJoinArea));
    assertTrue(panel.isAncestorOf(autoJoinPmArea));
  }

  @Test
  void buildPerformPanelWrapsAreaAndAddsHints() {
    JTextArea performArea = new JTextArea();

    JPanel panel =
        ServerEditorCommandTabBuilder.buildPerformPanel(
            new ServerEditorCommandTabBuilder.PerformWidgets(performArea));

    assertTrue(performArea.getLineWrap());
    assertTrue(performArea.getWrapStyleWord());
    assertEquals(1, countComponentsOfType(panel, JScrollPane.class));
    assertEquals(
        "foreground:$Label.disabledForeground",
        ((JLabel) panel.getComponent(0)).getClientProperty(FlatClientProperties.STYLE));
    assertEquals(
        "foreground:$Label.disabledForeground",
        ((JLabel) panel.getComponent(2)).getClientProperty(FlatClientProperties.STYLE));
    assertTrue(panel.isAncestorOf(performArea));
  }

  private static int countComponentsOfType(Container container, Class<?> type) {
    int count = 0;
    for (Component component : container.getComponents()) {
      if (type.isInstance(component)) {
        count++;
      }
      if (component instanceof Container child) {
        count += countComponentsOfType(child, type);
      }
    }
    return count;
  }
}
