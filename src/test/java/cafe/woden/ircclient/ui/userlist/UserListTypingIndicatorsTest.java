package cafe.woden.ircclient.ui.userlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserListTypingIndicatorsTest {

  @Test
  void activeTypingCreatesVisibleEntry() {
    UserListTypingIndicators indicators = new UserListTypingIndicators();
    String key = UserListTypingIndicators.foldNick("Alice");

    indicators.onTyping(key, "active", 1_000L);

    assertFalse(indicators.isEmpty());
    float alpha = indicators.alphaForKey(key, 1_050L);
    assertTrue(alpha > 0f);
    assertTrue(alpha <= 1f);
  }

  @Test
  void doneTypingFadesOutAndIsRemovedOnTick() {
    UserListTypingIndicators indicators = new UserListTypingIndicators();
    String key = UserListTypingIndicators.foldNick("alice");

    indicators.onTyping(key, "active", 1_000L);
    indicators.onTyping(key, "done", 1_100L);

    UserListTypingIndicators.TickOutcome outcome = indicators.tick(2_100L);

    assertTrue(outcome.changed());
    assertFalse(outcome.hasIndicators());
    assertTrue(indicators.isEmpty());
  }

  @Test
  void pruneRemovesUnknownNickKeys() {
    UserListTypingIndicators indicators = new UserListTypingIndicators();
    String alice = UserListTypingIndicators.foldNick("alice");
    String bob = UserListTypingIndicators.foldNick("bob");
    indicators.onTyping(alice, "active", 500L);
    indicators.onTyping(bob, "active", 500L);

    boolean changed = indicators.pruneToKnownNicks(Set.of(alice));

    assertTrue(changed);
    assertEquals(Set.of(alice), indicators.activeKeysSnapshot());
  }

  @Test
  void pausedTypingMarksPausedState() {
    UserListTypingIndicators indicators = new UserListTypingIndicators();
    String key = UserListTypingIndicators.foldNick("alice");

    indicators.onTyping(key, "paused", 1_000L);

    assertTrue(indicators.isPausedKey(key));
    assertTrue(indicators.alphaForKey(key, 1_100L) > 0f);
  }
}
