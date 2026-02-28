package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatActiveTargetCoordinatorTest {

  @Test
  void setActiveTargetIgnoresNullTarget() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>();

    ChatActiveTargetCoordinator coordinator =
        coordinator(
            activeTarget,
            inputPanel,
            new HashMap<>(),
            target -> ChatTargetViewRouter.TargetViewType.TRANSCRIPT,
            transcripts,
            (target, offset) -> {});

    coordinator.setActiveTarget(null);

    assertNull(activeTarget.get());
    verifyNoInteractions(inputPanel, transcripts);
  }

  @Test
  void setActiveTargetIgnoresSameTarget() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>(target);

    ChatActiveTargetCoordinator coordinator =
        coordinator(
            activeTarget,
            inputPanel,
            new HashMap<>(),
            t -> ChatTargetViewRouter.TargetViewType.TRANSCRIPT,
            transcripts,
            (selected, offset) -> {});

    coordinator.setActiveTarget(target);

    assertEquals(target, activeTarget.get());
    verifyNoInteractions(inputPanel, transcripts);
  }

  @Test
  void setActiveTargetRoutesUiOnlyTargetsWithoutTranscriptRestore() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    when(inputPanel.getDraftText()).thenReturn("draft before switch");
    when(inputPanel.isVisible()).thenReturn(true);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    TargetRef previous = new TargetRef("libera", "#old");
    TargetRef uiOnly = TargetRef.channelList("libera");
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>(previous);
    Map<TargetRef, String> drafts = new HashMap<>();
    AtomicInteger scrollUpdates = new AtomicInteger();
    AtomicInteger titleUpdates = new AtomicInteger();
    AtomicInteger typingRefreshes = new AtomicInteger();
    AtomicInteger topicRefreshes = new AtomicInteger();
    AtomicInteger revalidates = new AtomicInteger();
    AtomicInteger repaints = new AtomicInteger();
    AtomicReference<TargetRef> interceptorPrevious = new AtomicReference<>();
    AtomicReference<TargetRef> interceptorNext = new AtomicReference<>();

    ChatActiveTargetCoordinator coordinator =
        new ChatActiveTargetCoordinator(
            activeTarget::get,
            activeTarget::set,
            inputPanel,
            drafts,
            scrollUpdates::incrementAndGet,
            titleUpdates::incrementAndGet,
            typingRefreshes::incrementAndGet,
            (prev, next) -> {
              interceptorPrevious.set(prev);
              interceptorNext.set(next);
            },
            target -> ChatTargetViewRouter.TargetViewType.UI_ONLY,
            transcripts,
            doc -> {},
            topicRefreshes::incrementAndGet,
            (target, unreadOffset) -> {},
            runnable -> runnable.run(),
            revalidates::incrementAndGet,
            repaints::incrementAndGet);

    coordinator.setActiveTarget(uiOnly);

    assertEquals(uiOnly, activeTarget.get());
    assertEquals("draft before switch", drafts.get(previous));
    assertEquals(previous, interceptorPrevious.get());
    assertEquals(uiOnly, interceptorNext.get());
    assertEquals(1, scrollUpdates.get());
    assertEquals(1, titleUpdates.get());
    assertEquals(1, typingRefreshes.get());
    assertEquals(1, topicRefreshes.get());
    assertEquals(1, revalidates.get());
    assertEquals(1, repaints.get());
    verify(inputPanel).clearRemoteTypingIndicator();
    verify(inputPanel).flushTypingForBufferSwitch();
    verify(inputPanel).setVisible(false);
    verify(inputPanel).setDraftText("");
    verify(inputPanel, never()).focusInput();
    verifyNoInteractions(transcripts);
  }

  @Test
  void setActiveTargetRestoresTranscriptTargetStateAndAppliesReadMarkerLater() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    when(inputPanel.getDraftText()).thenReturn("saved previous draft");
    when(inputPanel.isVisible()).thenReturn(false);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    StyledDocument doc = new DefaultStyledDocument();
    TargetRef previous = new TargetRef("libera", "#old");
    TargetRef next = new TargetRef("libera", "#new");
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>(previous);
    Map<TargetRef, String> drafts = new HashMap<>();
    drafts.put(next, "restored draft");
    AtomicReference<StyledDocument> setDocumentValue = new AtomicReference<>();
    AtomicReference<TargetRef> readMarkerTarget = new AtomicReference<>();
    AtomicInteger readMarkerOffset = new AtomicInteger(Integer.MIN_VALUE);
    AtomicReference<Runnable> deferred = new AtomicReference<>();

    when(transcripts.document(next)).thenReturn(doc);
    when(transcripts.readMarkerJumpOffset(next)).thenReturn(42);

    ChatActiveTargetCoordinator coordinator =
        new ChatActiveTargetCoordinator(
            activeTarget::get,
            activeTarget::set,
            inputPanel,
            drafts,
            () -> {},
            () -> {},
            () -> {},
            (prev, current) -> {},
            target -> ChatTargetViewRouter.TargetViewType.TRANSCRIPT,
            transcripts,
            setDocumentValue::set,
            () -> {},
            (target, offset) -> {
              readMarkerTarget.set(target);
              readMarkerOffset.set(offset);
            },
            deferred::set,
            () -> {},
            () -> {});

    coordinator.setActiveTarget(next);

    assertEquals(next, activeTarget.get());
    assertEquals(doc, setDocumentValue.get());
    verify(inputPanel).setVisible(true);
    verify(inputPanel).setDraftText("restored draft");
    verify(inputPanel).focusInput();
    verify(transcripts).ensureTargetExists(next);
    verify(transcripts).document(next);
    verify(transcripts).readMarkerJumpOffset(next);
    assertEquals(Integer.MIN_VALUE, readMarkerOffset.get());

    Runnable runnable = deferred.get();
    assertNotNull(runnable);
    runnable.run();
    assertEquals(next, readMarkerTarget.get());
    assertEquals(42, readMarkerOffset.get());
  }

  @Test
  void setActiveTargetChannelToIgnoresAndBackRestoresDraftAndInputState() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    when(inputPanel.getDraftText()).thenReturn("channel draft", "");
    when(inputPanel.isVisible()).thenReturn(true, false);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    StyledDocument doc = new DefaultStyledDocument();
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TargetRef ignores = TargetRef.ignores("libera");
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>(channel);
    Map<TargetRef, String> drafts = new HashMap<>();
    AtomicReference<TargetRef> readMarkerTarget = new AtomicReference<>();
    AtomicInteger readMarkerOffset = new AtomicInteger(Integer.MIN_VALUE);

    when(transcripts.document(channel)).thenReturn(doc);
    when(transcripts.readMarkerJumpOffset(channel)).thenReturn(7);

    ChatActiveTargetCoordinator coordinator =
        coordinator(
            activeTarget,
            inputPanel,
            drafts,
            target ->
                target.isIgnores()
                    ? ChatTargetViewRouter.TargetViewType.UI_ONLY
                    : ChatTargetViewRouter.TargetViewType.TRANSCRIPT,
            transcripts,
            (target, offset) -> {
              readMarkerTarget.set(target);
              readMarkerOffset.set(offset);
            });

    coordinator.setActiveTarget(ignores);
    coordinator.setActiveTarget(channel);

    assertEquals(channel, activeTarget.get());
    assertEquals("channel draft", drafts.get(channel));
    verify(inputPanel).setVisible(false);
    verify(inputPanel).setVisible(true);
    verify(inputPanel).setDraftText("");
    verify(inputPanel).setDraftText("channel draft");
    verify(inputPanel).focusInput();
    verify(transcripts).ensureTargetExists(channel);
    verify(transcripts).document(channel);
    verify(transcripts).readMarkerJumpOffset(channel);
    assertEquals(channel, readMarkerTarget.get());
    assertEquals(7, readMarkerOffset.get());
  }

  private static ChatActiveTargetCoordinator coordinator(
      AtomicReference<TargetRef> activeTarget,
      MessageInputPanel inputPanel,
      Map<TargetRef, String> drafts,
      java.util.function.Function<TargetRef, ChatTargetViewRouter.TargetViewType> router,
      ChatTranscriptStore transcripts,
      java.util.function.BiConsumer<TargetRef, Integer> readMarkerApplier) {
    return new ChatActiveTargetCoordinator(
        activeTarget::get,
        activeTarget::set,
        inputPanel,
        drafts,
        () -> {},
        () -> {},
        () -> {},
        (previous, next) -> {},
        router,
        transcripts,
        doc -> {},
        () -> {},
        readMarkerApplier,
        runnable -> runnable.run(),
        () -> {},
        () -> {});
  }
}
