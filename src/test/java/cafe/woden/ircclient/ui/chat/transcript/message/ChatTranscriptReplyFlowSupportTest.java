package cafe.woden.ircclient.ui.chat.transcript.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptDocumentLineSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptLineMetaSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.LineMeta;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptState;
import java.util.HashMap;
import java.util.Map;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatTranscriptReplyFlowSupportTest {

  @Test
  void appendReplyContextLineEnsuresTargetAndUsesMessagePreviewCache() throws Exception {
    TargetRef ref = new TargetRef("srv", "#chan");
    Map<TargetRef, StyledDocument> docs = new HashMap<>();
    Map<TargetRef, ChatTranscriptState> stateByTarget = new HashMap<>();
    ChatTranscriptMessageCatalogSupport messageCatalogSupport = messageCatalogSupport();
    ChatTranscriptReplyFlowSupport support = new ChatTranscriptReplyFlowSupport();
    ChatTranscriptReplyFlowSupport.Context context =
        new ChatTranscriptReplyFlowSupport.Context(
            docs,
            stateByTarget,
            target -> {
              docs.computeIfAbsent(target, ignored -> new DefaultStyledDocument());
              stateByTarget.computeIfAbsent(
                  target, ignored -> newTranscriptState(messageCatalogSupport));
            },
            new ChatTranscriptDocumentLineSupport(new ChatStyles(null)),
            new ChatTranscriptReplyContextSupport.Context(new ChatStyles(null)),
            messageCatalogSupport);
    LineMeta meta =
        ChatTranscriptLineMetaSupport.create(
            ref, LogKind.CHAT, LogDirection.IN, "alice", 1_000L, null, "m-1", Map.of());
    messageCatalogSupport.rememberMessagePreview(
        stateByTarget
            .computeIfAbsent(ref, ignored -> newTranscriptState(messageCatalogSupport))
            .messageCatalog(),
        meta,
        "Alice",
        "hello");

    support.appendReplyContextLine(context, ref, "alice", "m-1", 2_000L);

    assertEquals("↪ Reply to Alice: hello\n", docs.get(ref).getText(0, docs.get(ref).getLength()));
  }

  private static ChatTranscriptMessageCatalogSupport messageCatalogSupport() {
    return new ChatTranscriptMessageCatalogSupport(
        new ChatTranscriptMessageStateSupport.Context(120, "[message redacted]", () -> 0L));
  }

  private static ChatTranscriptState newTranscriptState(
      ChatTranscriptMessageCatalogSupport messageCatalogSupport) {
    return new ChatTranscriptState(
        messageCatalogSupport.createState(8, 8),
        new cafe.woden.ircclient.ui.chat.transcript.filter.ChatTranscriptFilteredLinesSupport
            .State(),
        new cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptPresenceFoldSupport.State());
  }
}
