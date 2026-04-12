package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatTranscriptReactionStateSupportTest {

  @Test
  void observeAndSnapshotPreserveTrimmedUniqueNickOrder() {
    Map<String, LinkedHashSet<String>> nicksByReaction = new LinkedHashMap<>();

    ChatTranscriptReactionStateSupport.observe(nicksByReaction, " :+1: ", " Alice ");
    ChatTranscriptReactionStateSupport.observe(nicksByReaction, ":+1:", "Alice");
    ChatTranscriptReactionStateSupport.observe(nicksByReaction, ":+1:", "Bob");

    Map<String, Collection<String>> snapshot =
        ChatTranscriptReactionStateSupport.reactionsSnapshot(nicksByReaction);

    assertEquals(List.of("Alice", "Bob"), snapshot.get(":+1:"));
  }

  @Test
  void forgetRemovesEmptyReactionBucket() {
    Map<String, LinkedHashSet<String>> nicksByReaction = new LinkedHashMap<>();
    ChatTranscriptReactionStateSupport.observe(nicksByReaction, ":heart:", "alice");

    ChatTranscriptReactionStateSupport.forget(nicksByReaction, ":heart:", "alice");

    assertFalse(nicksByReaction.containsKey(":heart:"));
  }

  @Test
  void hasReactionFromNickMatchesCaseInsensitively() {
    Map<String, LinkedHashSet<String>> nicksByReaction = new LinkedHashMap<>();
    ChatTranscriptReactionStateSupport.observe(nicksByReaction, ":+1:", "Alice");

    assertTrue(
        ChatTranscriptReactionStateSupport.hasReactionFromNick(nicksByReaction, ":+1:", "alice"));
    assertTrue(
        ChatTranscriptReactionStateSupport.hasReactionFromNick(nicksByReaction, ":+1:", " ALICE "));
    assertFalse(
        ChatTranscriptReactionStateSupport.hasReactionFromNick(nicksByReaction, ":+1:", "bob"));
  }

  @Test
  void normalizeReactionNickKeyTrimsAndLowercases() {
    assertEquals("alice", ChatTranscriptReactionStateSupport.normalizeReactionNickKey(" Alice "));
    assertEquals("", ChatTranscriptReactionStateSupport.normalizeReactionNickKey("   "));
  }
}
