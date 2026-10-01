package cafe.woden.ircclient.ui.chat.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.roster.UserListPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import java.awt.Color;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatRichTextRendererTest {

  @ParameterizedTest
  @ValueSource(strings = {"ordinary words with spaces, ", "שלום עולם مرحبا بالعالم "})
  void plainTextUsesOneDocumentInsertionEvenWithBidiEnabled(String sample) throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.putProperty("i18n", Boolean.TRUE);
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
    String text = sample.repeat(100);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, null, null);

    int end = renderer.insertRichTextAt(doc, null, text, new SimpleAttributeSet(), 0);

    assertEquals(text, doc.getText(0, doc.getLength()));
    assertEquals(text.length(), end);
    assertEquals(
        1, insertions.get(), "unchanged text styling should not trigger per-word document updates");
  }

  @Test
  void coalescedInsertionPreservesRichTextMetadataAtAnInteriorOffset() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    MentionPatternRegistry mentions = new MentionPatternRegistry();
    mentions.setCurrentNick("server", "Alice");
    UserListPort users = mock(UserListPort.class);
    when(users.getLowerNickSet("server", "#chat")).thenReturn(Set.of("alice", "bob"));
    ChatRichTextRenderer renderer =
        new ChatRichTextRenderer(mentions, users, styles, new NickColorService(null, null));
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, "head | tail", new SimpleAttributeSet());
    String input = "before Alice Bob #room, https://example.test/path). 😀 \u0002bold\u0002 after ";
    String rendered = input.replace("\u0002", "");

    int end =
        renderer.insertRichTextAt(
            doc, new TargetRef("server", "#chat"), input, styles.message(), 5);

    String text = doc.getText(0, doc.getLength());
    assertEquals("head " + rendered + "| tail", text);
    assertEquals(5 + rendered.length(), end);
    var self = doc.getCharacterElement(text.indexOf("Alice")).getAttributes();
    assertEquals(ChatStyles.STYLE_MENTION, self.getAttribute(ChatStyles.ATTR_STYLE));
    assertEquals("alice", self.getAttribute(NickColorService.ATTR_NICK));
    assertEquals(
        "bob",
        doc.getCharacterElement(text.indexOf("Bob"))
            .getAttributes()
            .getAttribute(NickColorService.ATTR_NICK));
    assertEquals(
        "#room",
        doc.getCharacterElement(text.indexOf("#room"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_CHANNEL));
    assertNull(
        doc.getCharacterElement(text.indexOf(","))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_CHANNEL));
    assertEquals(
        "https://example.test/path",
        doc.getCharacterElement(text.indexOf("https"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_URL));
    assertNull(
        doc.getCharacterElement(text.indexOf(")."))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_URL));
    int emoji = text.indexOf("😀");
    assertTrue(EmojiFontSupport.isEmojiRun(doc.getCharacterElement(emoji).getAttributes()));
    assertFalse(EmojiFontSupport.isEmojiRun(doc.getCharacterElement(emoji - 1).getAttributes()));
    assertFalse(EmojiFontSupport.isEmojiRun(doc.getCharacterElement(emoji + 2).getAttributes()));
    assertTrue(
        StyleConstants.isBold(doc.getCharacterElement(text.indexOf("bold")).getAttributes()));
    assertFalse(
        StyleConstants.isBold(doc.getCharacterElement(text.indexOf("after")).getAttributes()));
  }

  @Test
  void linksAndMentionsRetainExplicitColorsAndNotificationBackgrounds() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    MentionPatternRegistry mentions = new MentionPatternRegistry();
    mentions.setCurrentNick("server", "Alice");
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(mentions, null, styles, null);
    SimpleAttributeSet base = new SimpleAttributeSet(styles.message());
    base.addAttribute(ChatStyles.ATTR_OVERRIDE_FG, Color.MAGENTA);
    base.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, Color.YELLOW);
    StyleConstants.setForeground(base, Color.MAGENTA);
    StyleConstants.setBackground(base, Color.YELLOW);
    String input =
        "plain Alice #room https://example.test \u000304red Alice #other https://red.test\u000f end";
    DefaultStyledDocument doc = new DefaultStyledDocument();

    renderer.insertRichTextAt(doc, new TargetRef("server", "#chat"), input, base, 0);

    String text = doc.getText(0, doc.getLength());
    assertEquals(
        "plain Alice #room https://example.test red Alice #other https://red.test end", text);
    for (String token : new String[] {"plain", "Alice", "#room", "https://example.test", "end"}) {
      var attrs = doc.getCharacterElement(text.indexOf(token)).getAttributes();
      assertEquals(Color.MAGENTA, StyleConstants.getForeground(attrs));
      assertEquals(Color.YELLOW, StyleConstants.getBackground(attrs));
      assertEquals(Color.YELLOW, attrs.getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG));
    }
    for (String token : new String[] {"red", "#other", "https://red.test"}) {
      var attrs = doc.getCharacterElement(text.indexOf(token, text.indexOf("red"))).getAttributes();
      assertEquals(IrcFormatting.colorForCode(4), StyleConstants.getForeground(attrs));
      assertEquals(Color.YELLOW, StyleConstants.getBackground(attrs));
    }
    int secondSelf = text.indexOf("Alice", text.indexOf("red"));
    var secondSelfStyle = doc.getCharacterElement(secondSelf).getAttributes();
    assertEquals(4, secondSelfStyle.getAttribute(ChatStyles.ATTR_IRC_FG));
    assertEquals(Color.YELLOW, StyleConstants.getBackground(secondSelfStyle));
    assertEquals(ChatStyles.STYLE_MENTION, secondSelfStyle.getAttribute(ChatStyles.ATTR_STYLE));
  }
}
