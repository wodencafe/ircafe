package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoggingUiPortDecoratorTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LogProperties LOGGING_ON = new LogProperties(true, true, true, 0, null);

  @Test
  void appendChatAtWithMetadataPersistsMessageIdentity() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "#chan");
    Instant at = Instant.ofEpochMilli(1_732_000_000_123L);
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("draft/reply", "root-9");
    tags.put("msgid", "m-1");

    d.appendChatAt(target, at, "alice", "hello", false, "m-1", tags);

    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogKind.CHAT, line.kind());
    assertEquals(LogDirection.IN, line.direction());
    assertEquals("m-1", meta(line).get("messageId"));
    assertEquals("root-9", metaTags(line).get("draft/reply"));
    verify(delegate).appendChatAt(target, at, "alice", "hello", false, "m-1", tags);
  }

  @Test
  void appendActionAtWithMetadataPersistsMessageIdentity() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "#chan");
    Instant at = Instant.ofEpochMilli(1_732_000_000_456L);
    Map<String, String> tags = Map.of("msgid", "act-42");

    d.appendActionAt(target, at, "bob", "waves", false, "act-42", tags);

    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogKind.ACTION, line.kind());
    assertEquals("act-42", meta(line).get("messageId"));
    assertEquals("act-42", metaTags(line).get("msgid"));
    verify(delegate).appendActionAt(target, at, "bob", "waves", false, "act-42", tags);
  }

  @Test
  void appendNoticeAtWithMetadataPersistsMessageIdentity() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "status");
    Instant at = Instant.ofEpochMilli(1_732_000_000_789L);
    Map<String, String> tags = Map.of("label", "req-7");

    d.appendNoticeAt(target, at, "(notice) server", "maintenance", "n-7", tags);

    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogKind.NOTICE, line.kind());
    assertEquals("n-7", meta(line).get("messageId"));
    assertEquals("req-7", metaTags(line).get("label"));
    verify(delegate).appendNoticeAt(target, at, "(notice) server", "maintenance", "n-7", tags);
  }

  @Test
  void appendStatusAtWithMetadataPersistsMessageIdentity() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "status");
    Instant at = Instant.ofEpochMilli(1_732_000_001_111L);
    Map<String, String> tags = Map.of("msgid", "srv-1", "label", "raw-88");

    d.appendStatusAt(target, at, "(server)", "421 NO_SUCH_COMMAND", "srv-1", tags);

    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogKind.STATUS, line.kind());
    assertEquals("srv-1", meta(line).get("messageId"));
    assertEquals("raw-88", metaTags(line).get("label"));
    verify(delegate).appendStatusAt(target, at, "(server)", "421 NO_SUCH_COMMAND", "srv-1", tags);
  }

  @Test
  void resolvePendingOutgoingChatPersistsPendingAndIdentityMetadata() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "#chan");
    Instant at = Instant.ofEpochMilli(1_732_000_001_222L);
    Map<String, String> tags = Map.of("msgid", "srv-echo-1");
    when(delegate.resolvePendingOutgoingChat(target, "p-123", at, "me", "hello", "srv-echo-1", tags))
        .thenReturn(true);

    boolean resolved = d.resolvePendingOutgoingChat(target, "p-123", at, "me", "hello", "srv-echo-1", tags);

    assertTrue(resolved);
    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogDirection.OUT, line.direction());
    assertEquals("p-123", meta(line).get("pendingId"));
    assertEquals("srv-echo-1", meta(line).get("messageId"));
    assertEquals("srv-echo-1", metaTags(line).get("msgid"));
  }

  @Test
  void resolvePendingOutgoingChatWithoutMatchDoesNotPersist() {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "#chan");
    Instant at = Instant.ofEpochMilli(1_732_000_001_333L);
    Map<String, String> tags = Map.of("msgid", "srv-echo-2");
    when(delegate.resolvePendingOutgoingChat(target, "p-404", at, "me", "hello", "srv-echo-2", tags))
        .thenReturn(false);

    boolean resolved = d.resolvePendingOutgoingChat(target, "p-404", at, "me", "hello", "srv-echo-2", tags);

    assertFalse(resolved);
    assertNull(captured.get());
  }

  @Test
  void appendSpoilerChatAtWithMetadataPersistsMessageIdentity() throws Exception {
    UiPort delegate = mock(UiPort.class);
    AtomicReference<LogLine> captured = new AtomicReference<>();
    LoggingUiPortDecorator d = newDecorator(delegate, captured);

    TargetRef target = new TargetRef("srv", "#chan");
    Instant at = Instant.ofEpochMilli(1_732_000_001_444L);
    Map<String, String> tags = Map.of("msgid", "spoiler-1", "typing", "active");

    d.appendSpoilerChatAt(target, at, "eve", "hidden", "spoiler-1", tags);

    LogLine line = captured.get();
    assertNotNull(line);
    assertEquals(LogKind.SPOILER, line.kind());
    assertTrue(line.softIgnored());
    assertEquals("spoiler-1", meta(line).get("messageId"));
    assertEquals("active", metaTags(line).get("typing"));
    verify(delegate).appendSpoilerChatAt(target, at, "eve", "hidden", "spoiler-1", tags);
  }

  private static LoggingUiPortDecorator newDecorator(UiPort delegate, AtomicReference<LogLine> captured) {
    ChatLogWriter writer = captured::set;
    return new LoggingUiPortDecorator(delegate, writer, new LogLineFactory(), LOGGING_ON);
  }

  private static Map<String, Object> meta(LogLine line) throws Exception {
    String json = line.metaJson();
    assertNotNull(json);
    return JSON.readValue(json, new TypeReference<>() {});
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> metaTags(LogLine line) throws Exception {
    Object tags = meta(line).get("ircv3Tags");
    assertNotNull(tags);
    return (Map<String, Object>) tags;
  }
}
