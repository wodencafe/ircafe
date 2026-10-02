package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.SettingsColorSupport;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatTopicNotificationsFunctionalTest {
  @ParameterizedTest
  @ValueSource(strings = {"nimbus-dark-orange", "nimbus-orange", "darcula"})
  void notificationBellStaysReadableAndResetsAfterClearing(String theme) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var originalLaf = UIManager.getLookAndFeel();
          ThemeManager themes =
              new ThemeManager(
                  mock(ChatStyles.class), mock(ChatTranscriptStore.class), null, null, null);
          try {
            themes.installLookAndFeel(theme);
            NotificationStore store = new NotificationStore();
            ChatTopicCoordinator coordinator =
                new ChatTopicCoordinator(
                    new JScrollPane(), mock(ChannelListPanel.class), store, target -> {}, () -> {});
            TargetRef channel = new TargetRef("libera", "#ircafe");
            coordinator.setTopic(channel, "visible topic", channel);
            coordinator.updateTopicPanelForActiveTarget(channel);
            JButton button =
                findButton((Container) ((JSplitPane) coordinator.topicSplit()).getTopComponent());
            assertNotNull(button);
            assertBell(button);
            assertFalse(button.isContentAreaFilled());

            store.recordHighlight(channel, "alice", "hello", "message-1");
            coordinator.updateTopicPanelForActiveTarget(channel);
            assertBell(button);
            assertTrue(button.isContentAreaFilled());
            assertTrue(button.getToolTipText().contains("hello"));
            assertReadableBell(button, theme);

            store.clearChannel(channel);
            coordinator.updateTopicPanelForActiveTarget(channel);
            assertBell(button);
            assertFalse(button.isContentAreaFilled());
            assertFalse(button.isBorderPainted());
            assertFalse(button.isOpaque());

            coordinator.updateTopicPanelForActiveTarget(new TargetRef("libera", "status"));
            assertFalse(button.isVisible());
          } finally {
            themes.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (javax.swing.UnsupportedLookAndFeelException e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }

  private static void assertBell(JButton button) {
    FlatSVGIcon icon = assertInstanceOf(FlatSVGIcon.class, button.getIcon());
    assertTrue(icon.hasFound(), "notification SVG must load successfully");
    assertEquals("icons/svg/bell.svg", icon.getName());
  }

  private static void assertReadableBell(JButton button, String theme) {
    button.setSize(button.getPreferredSize());
    BufferedImage image =
        new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(button.getBackground());
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      button.printAll(graphics);
    } finally {
      graphics.dispose();
    }
    Color paintedBackground = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
    int insetX = (button.getWidth() - button.getIcon().getIconWidth()) / 2;
    int insetY = (button.getHeight() - button.getIcon().getIconHeight()) / 2;
    int readablePixels = 0;
    for (int y = insetY; y < image.getHeight() - insetY; y++) {
      for (int x = insetX; x < image.getWidth() - insetX; x++) {
        if (SettingsColorSupport.contrastRatio(new Color(image.getRGB(x, y)), paintedBackground)
            >= 3.0) {
          readablePixels++;
        }
      }
    }
    assertTrue(readablePixels >= 20, theme + ": bell must contrast with its highlight");
  }

  private static JButton findButton(Container container) {
    for (Component component : container.getComponents()) {
      if (component instanceof JButton button) return button;
      if (component instanceof Container child) {
        JButton found = findButton(child);
        if (found != null) return found;
      }
    }
    return null;
  }
}
