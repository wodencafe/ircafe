package cafe.woden.ircclient.ui.chat.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatRichTextRendererFunctionalTest {
  @ParameterizedTest
  @ValueSource(strings = {"nimbus-dark-orange", "darcula"})
  void startupTextBurstUsesOneUpdatePerPlainLineAndPreservesBidi(String theme) throws Exception {
    String originalLaf = UIManager.getLookAndFeel().getClass().getName();
    ThemeManager themes =
        new ThemeManager(mock(ChatStyles.class), mock(ChatTranscriptStore.class), null, null, null);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            themes.installLookAndFeel(theme);
            ChatStyles styles = new ChatStyles(null);
            ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
            DefaultStyledDocument doc = new DefaultStyledDocument();
            doc.putProperty("i18n", Boolean.TRUE);
            JTextPane pane = new JTextPane(doc);
            pane.setSize(640, 480);
            AtomicInteger insertions = new AtomicInteger();
            doc.addDocumentListener(
                new DocumentListener() {
                  @Override
                  public void insertUpdate(DocumentEvent event) {
                    insertions.incrementAndGet();
                  }

                  @Override
                  public void removeUpdate(DocumentEvent event) {}

                  @Override
                  public void changedUpdate(DocumentEvent event) {}
                });
            String line = "ordinary startup text ".repeat(12) + "שלום עולם مرحبا بالعالم\n";
            try {
              for (int i = 0; i < 200; i++)
                renderer.insertRichText(doc, null, line, styles.message());
              assertEquals(line.repeat(200), pane.getText());
              assertEquals(
                  200, insertions.get(), "a plain line should update the Swing document once");
              assertEquals(Boolean.TRUE, doc.getProperty("i18n"));
              boolean hasRightToLeftRun = false;
              var bidiRoot = doc.getBidiRootElement();
              for (int i = 0; i < bidiRoot.getElementCount(); i++) {
                int level = StyleConstants.getBidiLevel(bidiRoot.getElement(i).getAttributes());
                hasRightToLeftRun |= (level & 1) != 0;
              }
              assertTrue(hasRightToLeftRun, "bidirectional text layout must remain enabled");
            } catch (javax.swing.text.BadLocationException e) {
              throw new AssertionError(e);
            }
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            themes.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
    }
  }
}
