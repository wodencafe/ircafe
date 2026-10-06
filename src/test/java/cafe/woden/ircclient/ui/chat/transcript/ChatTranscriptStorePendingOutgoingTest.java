package cafe.woden.ircclient.ui.chat.transcript;

import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreDocumentTestSupport.inlineComponentCount;
import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreDocumentTestSupport.transcriptTextUnchecked;
import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreTargetRefTestSupport.channelRef;
import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreTestFactory.newStoreWithTranscriptCapAndDeliveryIndicators;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.line.OutgoingSendIndicator;
import java.util.Map;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatTranscriptStorePendingOutgoingTest {

  @Test
  void appendPendingOutgoingChatSkipsSpinnerWhenDeliveryIndicatorsAreDisabled() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, false);
    TargetRef ref = channelRef();

    store.appendPendingOutgoingChat(ref, "pending-1", "me", "hello", 10_000L);

    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
  }

  @Test
  void resolvePendingOutgoingChatSkipsConfirmedDotWhenDeliveryIndicatorsAreDisabled() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, false);
    TargetRef ref = channelRef();

    store.appendPendingOutgoingChat(ref, "pending-2", "me", "hello", 10_000L);
    boolean resolved =
        store.resolvePendingOutgoingChat(
            ref, "pending-2", "me", "hello", 10_100L, "msg-1", Map.of("msgid", "msg-1"));

    assertTrue(resolved);
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.ConfirmedDot.class));
  }

  @Test
  void resolvePendingOutgoingChatAddsConfirmedDotWhenDeliveryIndicatorsAreEnabled() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, true);
    TargetRef ref = channelRef();

    store.appendPendingOutgoingChat(ref, "pending-2", "me", "hello", 10_000L);
    boolean resolved =
        store.resolvePendingOutgoingChat(
            ref, "pending-2", "me", "hello", 10_100L, "msg-1", Map.of("msgid", "msg-1"));

    assertTrue(resolved);
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello"));
    assertEquals(1, inlineComponentCount(doc, OutgoingSendIndicator.ConfirmedDot.class));
  }

  @Test
  void resolvePendingOutgoingChatAppliesReplyReactionToReferencedMessage() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, false);
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "hello", false, 9_000L, "m-1", Map.of("msgid", "m-1"));
    store.appendPendingOutgoingChat(ref, "pending-4", "bob", "react", 9_100L);

    boolean resolved =
        store.resolvePendingOutgoingChat(
            ref,
            "pending-4",
            "bob",
            "react",
            9_200L,
            "m-2",
            Map.of("msgid", "m-2", "draft/reply", "m-1", "draft/react", ":+1:"));

    assertTrue(resolved);
    assertTrue(store.hasReactionFromNick(ref, "m-1", ":+1:", "bob"));
  }

  @Test
  void failPendingOutgoingChatReplacesSpinnerLineWithFailedSuffix() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, true);
    TargetRef ref = channelRef();

    store.appendPendingOutgoingChat(ref, "pending-3", "me", "hello", 10_000L);

    boolean failed =
        store.failPendingOutgoingChat(ref, "pending-3", "me", "hello", 10_100L, "network");

    assertTrue(failed);
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello [failed: network]"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pendingActionResolvesInPlaceWithActionMetadataAndConfiguredIndicator(boolean indicators) {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, indicators);
    TargetRef ref = channelRef();
    store.appendPendingOutgoingAction(ref, "action-1", "me", "waves", 10_000L);
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("* me waves"));
    assertEquals(
        indicators ? 1 : 0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
    store.appendChat(ref, "alice", "next message");

    assertTrue(
        store.resolvePendingOutgoingAction(
            ref, "action-1", "me", "waves", 10_100L, "msg-a", Map.of("msgid", "msg-a")));

    String text = transcriptTextUnchecked(doc);
    assertEquals(text.indexOf("* me waves"), text.lastIndexOf("* me waves"));
    assertTrue(text.indexOf("* me waves") < text.indexOf("next message"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
    assertEquals(
        indicators ? 1 : 0, inlineComponentCount(doc, OutgoingSendIndicator.ConfirmedDot.class));
    var attrs = doc.getCharacterElement(text.indexOf("waves")).getAttributes();
    assertEquals("ACTION", attrs.getAttribute(ChatStyles.ATTR_META_KIND));
    assertEquals("OUT", attrs.getAttribute(ChatStyles.ATTR_META_DIRECTION));
    assertEquals("msg-a", attrs.getAttribute(ChatStyles.ATTR_META_MSGID));
    assertTrue(store.isOwnMessage(ref, "msg-a"));
  }

  @Test
  void failedPendingActionKeepsEmoteFormattingAndRemovesSpinner() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, true);
    TargetRef ref = channelRef();
    store.appendPendingOutgoingAction(ref, "action-1", "me", "waves", 10_000L);
    assertTrue(store.failPendingOutgoingAction(ref, "action-1", "me", "waves", 10_100L, "network"));
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("* me waves [failed: network]"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.ConfirmedDot.class));
  }

  @Test
  void pendingActionsRespectTranscriptCapAndPrunedLinesCannotBeResolved() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(2, true);
    TargetRef ref = channelRef();
    for (int i = 0; i < 5; i++) {
      store.appendPendingOutgoingAction(ref, "action-" + i, "me", "waves" + i, 10_000L + i);
    }
    StyledDocument doc = store.document(ref);
    assertEquals(2, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
    assertFalse(
        store.resolvePendingOutgoingAction(
            ref, "action-0", "me", "waves0", 10_100L, "old", Map.of()));
    assertTrue(
        store.resolvePendingOutgoingAction(
            ref, "action-4", "me", "waves4", 10_100L, "new", Map.of()));
  }
}
