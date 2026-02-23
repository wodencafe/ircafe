package cafe.woden.ircclient.logging.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.logging.model.LogRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbChatLogViewerServiceTest {

  @Test
  void parsesMetaAndAppliesContainsFilters() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    String meta = """
        {"messageId":"m-1","ircv3Tags":{"ircafe/hostmask":"alice!u@example.com","aaa":"bbb"}}
        """;
    when(repo.searchRows(eq("srv"), isNull(), isNull(), anyInt()))
        .thenReturn(List.of(row(11L, "Alice", meta)));

    ChatLogViewerQuery q = new ChatLogViewerQuery(
        "srv",
        "ali",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "example.com",
        ChatLogViewerMatchMode.CONTAINS,
        "#chan",
        ChatLogViewerMatchMode.CONTAINS,
        false,
        false,
        null,
        null,
        200
    );

    ChatLogViewerResult res = svc.search(q);
    assertEquals(1, res.rows().size());
    assertEquals("Alice", res.rows().getFirst().fromNick());
    assertEquals("alice!u@example.com", res.rows().getFirst().hostmask());
    assertEquals("m-1", res.rows().getFirst().messageId());
    verify(repo).searchRows("srv", null, null, 5000);
  }

  @Test
  void supportsGlobAndRegexMatching() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    String meta = """
        {"ircv3Tags":{"ircafe/hostmask":"alice!u@example.net"}}
        """;
    when(repo.searchRows(eq("srv"), isNull(), isNull(), anyInt()))
        .thenReturn(List.of(row(12L, "Alice", meta)));

    ChatLogViewerQuery q = new ChatLogViewerQuery(
        "srv",
        "A*",
        ChatLogViewerMatchMode.GLOB,
        "he*o",
        ChatLogViewerMatchMode.GLOB,
        "example\\.net$",
        ChatLogViewerMatchMode.REGEX,
        "#ch?n",
        ChatLogViewerMatchMode.GLOB,
        false,
        false,
        null,
        null,
        100
    );

    ChatLogViewerResult res = svc.search(q);
    assertEquals(1, res.rows().size());
    assertEquals("alice!u@example.net", res.rows().getFirst().hostmask());
  }

  @Test
  void filtersByMessageText() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    when(repo.searchRows(eq("srv"), isNull(), isNull(), anyInt()))
        .thenReturn(List.of(
            row(21L, LogKind.CHAT, "Alice", "hello world", "{}", LogDirection.IN),
            row(20L, LogKind.CHAT, "Bob", "goodbye", "{}", LogDirection.IN)
        ));

    ChatLogViewerQuery q = new ChatLogViewerQuery(
        "srv",
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "hello",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        false,
        false,
        null,
        null,
        100
    );

    ChatLogViewerResult res = svc.search(q);
    assertEquals(1, res.rows().size());
    assertEquals(21L, res.rows().getFirst().id());
  }

  @Test
  void throwsForInvalidRegex() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    ChatLogViewerQuery q = new ChatLogViewerQuery(
        "srv",
        "[",
        ChatLogViewerMatchMode.REGEX,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        false,
        false,
        null,
        null,
        100
    );

    assertThrows(IllegalArgumentException.class, () -> svc.search(q));
  }

  @Test
  void hidesServerAndProtocolNoiseByDefault() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    when(repo.searchRows(eq("srv"), isNull(), isNull(), anyInt()))
        .thenReturn(List.of(
            row(101L, LogKind.STATUS, "(server)", "[315] End of WHO list. (#chan)", "{}", LogDirection.SYSTEM),
            row(100L, LogKind.STATUS, "(mode)", "Channel modes: +nt", "{}", LogDirection.SYSTEM),
            row(99L, LogKind.ERROR, "(conn)", "Disconnected: timeout", "{}", LogDirection.SYSTEM),
            row(98L, LogKind.CHAT, "Alice", "hello", "{}", LogDirection.IN)
        ));

    ChatLogViewerQuery q = new ChatLogViewerQuery(
        "srv",
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        false,
        false,
        null,
        null,
        200
    );

    ChatLogViewerResult res = svc.search(q);
    assertEquals(2, res.rows().size());
    assertEquals(99L, res.rows().get(0).id());
    assertEquals(98L, res.rows().get(1).id());
  }

  @Test
  void canIncludeServerEventsButKeepProtocolDetailsHidden() {
    ChatLogRepository repo = mock(ChatLogRepository.class);
    DbChatLogViewerService svc = new DbChatLogViewerService(repo);

    when(repo.searchRows(eq("srv"), isNull(), isNull(), anyInt()))
        .thenReturn(List.of(
            row(201L, LogKind.STATUS, "(server)", "[315] End of WHO list. (#chan)", "{}", LogDirection.SYSTEM),
            row(200L, LogKind.STATUS, "(server)", "Connected to irc.libera.chat", "{}", LogDirection.SYSTEM)
        ));

    ChatLogViewerQuery hideProtocol = new ChatLogViewerQuery(
        "srv",
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        true,
        false,
        null,
        null,
        200
    );

    ChatLogViewerResult withoutProtocol = svc.search(hideProtocol);
    assertEquals(1, withoutProtocol.rows().size());
    assertEquals(200L, withoutProtocol.rows().getFirst().id());

    ChatLogViewerQuery showProtocol = new ChatLogViewerQuery(
        "srv",
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        "",
        ChatLogViewerMatchMode.CONTAINS,
        true,
        true,
        null,
        null,
        200
    );

    ChatLogViewerResult withProtocol = svc.search(showProtocol);
    assertEquals(2, withProtocol.rows().size());
  }

  private static LogRow row(long id, String fromNick, String metaJson) {
    return row(id, LogKind.CHAT, fromNick, "hello", metaJson, LogDirection.IN);
  }

  private static LogRow row(long id, LogKind kind, String fromNick, String text, String metaJson, LogDirection direction) {
    return new LogRow(id, new LogLine(
        "srv",
        "#chan",
        1_700_000_000_000L,
        direction,
        kind,
        fromNick,
        text,
        false,
        false,
        metaJson
    ));
  }
}
