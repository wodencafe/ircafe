package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptContextMenuDecoratorTest {

  @Test
  void lineIdentityFromAttributesReadsMessageIdentityMetadata() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "abc123");
    attrs.addAttribute(ChatStyles.ATTR_META_IRCV3_TAGS, "msgid=abc123;typing=active");

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertEquals("abc123", id.messageId());
    assertEquals("msgid=abc123;typing=active", id.ircv3Tags());
    assertFalse(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesMarksOutgoingFromDirectionMetadata() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "abc123");
    attrs.addAttribute(ChatStyles.ATTR_META_DIRECTION, "OUT");

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertTrue(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesMarksOutgoingFromLegacyOutgoingAttribute() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertTrue(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesReturnsEmptyForMissingMetadata() {
    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(new SimpleAttributeSet());

    assertSame(ChatTranscriptContextMenuDecorator.LineIdentity.EMPTY, id);
  }

  @Test
  void contextMenuKeepsIrcv3ActionsVisibleWhenUnavailable() throws Exception {
    JTextPane transcript = new JTextPane();
    ChatTranscriptContextMenuDecorator decorator = buildDecorator(transcript, false, false, false);

    setPopupLineIdentity(decorator, "", false);
    invokePrivate(decorator, "rebuildMenu", new Class<?>[] {String.class}, "");
    invokePrivate(decorator, "updateEnabledState", new Class<?>[] {String.class}, "");

    JPopupMenu menu = (JPopupMenu) readField(decorator, "menu");
    JMenuItem loadNewerHistoryItem = (JMenuItem) readField(decorator, "loadNewerHistoryItem");
    JMenuItem loadAroundMessageItem = (JMenuItem) readField(decorator, "loadAroundMessageItem");
    JMenuItem replyToMessageItem = (JMenuItem) readField(decorator, "replyToMessageItem");
    JMenuItem reactToMessageItem = (JMenuItem) readField(decorator, "reactToMessageItem");
    JMenuItem unreactToMessageItem = (JMenuItem) readField(decorator, "unreactToMessageItem");
    JMenuItem editMessageItem = (JMenuItem) readField(decorator, "editMessageItem");
    JMenuItem redactMessageItem = (JMenuItem) readField(decorator, "redactMessageItem");

    assertSame(menu, loadNewerHistoryItem.getParent());
    assertSame(menu, loadAroundMessageItem.getParent());
    assertSame(menu, replyToMessageItem.getParent());
    assertSame(menu, reactToMessageItem.getParent());
    assertSame(menu, unreactToMessageItem.getParent());
    assertSame(menu, editMessageItem.getParent());
    assertSame(menu, redactMessageItem.getParent());

    assertFalse(loadNewerHistoryItem.isEnabled());
    assertEquals(
        "Unavailable: server does not support IRCv3 CHATHISTORY or playback for this target.",
        loadNewerHistoryItem.getToolTipText());

    assertFalse(loadAroundMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", loadAroundMessageItem.getToolTipText());

    assertFalse(replyToMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", replyToMessageItem.getToolTipText());

    assertFalse(reactToMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", reactToMessageItem.getToolTipText());

    assertFalse(unreactToMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", unreactToMessageItem.getToolTipText());

    assertFalse(editMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", editMessageItem.getToolTipText());

    assertFalse(redactMessageItem.isEnabled());
    assertEquals(
        "Unavailable: this line has no IRCv3 message ID.", redactMessageItem.getToolTipText());
  }

  @Test
  void contextMenuEnablesIrcv3ActionsWhenMessageAndCapabilitiesAvailable() throws Exception {
    JTextPane transcript = new JTextPane();
    ChatTranscriptContextMenuDecorator decorator = buildDecorator(transcript, true, true, true);

    setPopupLineIdentity(decorator, "abc123", true);
    invokePrivate(decorator, "rebuildMenu", new Class<?>[] {String.class}, "");
    invokePrivate(decorator, "updateEnabledState", new Class<?>[] {String.class}, "");

    JMenuItem loadNewerHistoryItem = (JMenuItem) readField(decorator, "loadNewerHistoryItem");
    JMenuItem loadAroundMessageItem = (JMenuItem) readField(decorator, "loadAroundMessageItem");
    JMenuItem replyToMessageItem = (JMenuItem) readField(decorator, "replyToMessageItem");
    JMenuItem reactToMessageItem = (JMenuItem) readField(decorator, "reactToMessageItem");
    JMenuItem unreactToMessageItem = (JMenuItem) readField(decorator, "unreactToMessageItem");
    JMenuItem editMessageItem = (JMenuItem) readField(decorator, "editMessageItem");
    JMenuItem redactMessageItem = (JMenuItem) readField(decorator, "redactMessageItem");

    assertTrue(loadNewerHistoryItem.isEnabled());
    assertTrue(loadAroundMessageItem.isEnabled());
    assertTrue(replyToMessageItem.isEnabled());
    assertTrue(reactToMessageItem.isEnabled());
    assertTrue(unreactToMessageItem.isEnabled());
    assertTrue(editMessageItem.isEnabled());
    assertTrue(redactMessageItem.isEnabled());
  }

  @Test
  void contextMenuDisablesEditAndRedactForNonOwnedMessages() throws Exception {
    JTextPane transcript = new JTextPane();
    ChatTranscriptContextMenuDecorator decorator = buildDecorator(transcript, true, true, true);

    setPopupLineIdentity(decorator, "abc123", false);
    invokePrivate(decorator, "rebuildMenu", new Class<?>[] {String.class}, "");
    invokePrivate(decorator, "updateEnabledState", new Class<?>[] {String.class}, "");

    JMenuItem replyToMessageItem = (JMenuItem) readField(decorator, "replyToMessageItem");
    JMenuItem reactToMessageItem = (JMenuItem) readField(decorator, "reactToMessageItem");
    JMenuItem unreactToMessageItem = (JMenuItem) readField(decorator, "unreactToMessageItem");
    JMenuItem editMessageItem = (JMenuItem) readField(decorator, "editMessageItem");
    JMenuItem redactMessageItem = (JMenuItem) readField(decorator, "redactMessageItem");

    assertTrue(replyToMessageItem.isEnabled());
    assertTrue(reactToMessageItem.isEnabled());
    assertTrue(unreactToMessageItem.isEnabled());
    assertFalse(editMessageItem.isEnabled());
    assertFalse(redactMessageItem.isEnabled());
    assertEquals(
        "Unavailable: only your own messages can be edited.", editMessageItem.getToolTipText());
    assertEquals(
        "Unavailable: only your own messages can be redacted.", redactMessageItem.getToolTipText());
  }

  private static ChatTranscriptContextMenuDecorator buildDecorator(
      JTextPane transcript, boolean historyVisible, boolean replyReactVisible, boolean editRedactVisible) {
    return ChatTranscriptContextMenuDecorator.decorate(
        transcript,
        p -> null,
        p -> null,
        nick -> null,
        url -> {},
        () -> {},
        () -> null,
        () -> historyVisible,
        () -> historyVisible,
        () -> {},
        msgId -> {},
        () -> replyReactVisible,
        () -> replyReactVisible,
        () -> replyReactVisible,
        msgId -> {},
        msgId -> {},
        msgId -> {},
        () -> editRedactVisible,
        () -> editRedactVisible,
        msgId -> {},
        msgId -> {});
  }

  private static void setPopupLineIdentity(
      ChatTranscriptContextMenuDecorator decorator, String messageId, boolean ownMessage)
      throws Exception {
    writeField(decorator, "currentPopupMessageId", messageId);
    writeField(decorator, "currentPopupIrcv3Tags", "");
    writeField(decorator, "currentPopupOwnMessage", ownMessage);
  }

  private static Object readField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    Object value = field.get(target);
    assertNotNull(value);
    return value;
  }

  private static void writeField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invokePrivate(
      Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    method.invoke(target, args);
  }
}
