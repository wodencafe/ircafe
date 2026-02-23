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
        "example.com",
        ChatLogViewerMatchMode.CONTAINS,
        "#chan",
        ChatLogViewerMatchMode.CONTAINS,
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
        "example\\.net$",
        ChatLogViewerMatchMode.REGEX,
        "#ch?n",
        ChatLogViewerMatchMode.GLOB,
        null,
        null,
        100
    );

    ChatLogViewerResult res = svc.search(q);
    assertEquals(1, res.rows().size());
    assertEquals("alice!u@example.net", res.rows().getFirst().hostmask());
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
        null,
        null,
        100
    );

    assertThrows(IllegalArgumentException.class, () -> svc.search(q));
  }

  private static LogRow row(long id, String fromNick, String metaJson) {
    return new LogRow(id, new LogLine(
        "srv",
        "#chan",
        1_700_000_000_000L,
        LogDirection.IN,
        LogKind.CHAT,
        fromNick,
        "hello",
        false,
        false,
        metaJson
    ));
  }
}
