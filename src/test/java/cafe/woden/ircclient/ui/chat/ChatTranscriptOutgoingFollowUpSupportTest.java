package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatTranscriptOutgoingFollowUpSupportTest {

  @Test
  void planNormalizesMessageIdAndExtractsReplyReactionTags() {
    ChatTranscriptOutgoingFollowUpSupport.Plan plan =
        ChatTranscriptOutgoingFollowUpSupport.plan(
            " m-2 ", Map.of("+draft/reply", "m-1", "+draft/react", ":+1:"));

    assertEquals("m-2", plan.normalizedMessageId());
    assertEquals("m-1", plan.replyToMessageId());
    assertEquals(":+1:", plan.reactionToken());
    assertTrue(plan.hasReplyContext());
    assertTrue(plan.hasMaterializedMessageId());
    assertTrue(plan.hasReplyReaction());
  }

  @Test
  void callbacksRunOnlyWhenRequiredFieldsArePresent() {
    ChatTranscriptOutgoingFollowUpSupport.Plan plan =
        ChatTranscriptOutgoingFollowUpSupport.plan("", Map.of("draft/reply", "m-1"));
    AtomicReference<String> replyTarget = new AtomicReference<>();
    AtomicInteger materialized = new AtomicInteger();
    AtomicInteger reactions = new AtomicInteger();

    plan.runReplyContext(replyTarget::set);
    plan.runPendingMaterialization(materialized::incrementAndGet);
    plan.runReplyReaction(reactions::incrementAndGet);

    assertEquals("m-1", replyTarget.get());
    assertEquals(0, materialized.get());
    assertEquals(0, reactions.get());
    assertFalse(plan.hasMaterializedMessageId());
    assertFalse(plan.hasReplyReaction());
  }
}
