package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.model.NotificationRule;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class NotificationRuleMatcherTest {

  @Test
  void matchAllSupportsWordWholeWordAndRegexRules() {
    MutableUiSettingsPort settings =
        new MutableUiSettingsPort(
            snapshot(
                new NotificationRule(
                    "Ping", NotificationRule.Type.WORD, "ping", true, false, false, "#f90"),
                new NotificationRule(
                    "ExactBob", NotificationRule.Type.WORD, "Bob", true, true, true, null),
                new NotificationRule(
                    "HashTag", NotificationRule.Type.REGEX, "#\\w+", true, false, false, null)));

    NotificationRuleMatcher matcher = new NotificationRuleMatcher(settings);

    List<NotificationRuleMatch> matches = matcher.matchAll("bob pinging Bob #chan");
    assertEquals(3, matches.size());

    NotificationRuleMatch ping = matches.get(0);
    assertEquals("Ping", ping.ruleLabel());
    assertEquals("ping", ping.matchedText());
    assertEquals(4, ping.start());
    assertEquals(8, ping.end());
    assertEquals("#FF9900", ping.highlightColor());

    NotificationRuleMatch exactBob = matches.get(1);
    assertEquals("ExactBob", exactBob.ruleLabel());
    assertEquals("Bob", exactBob.matchedText());
    assertEquals(12, exactBob.start());
    assertEquals(15, exactBob.end());

    NotificationRuleMatch hashTag = matches.get(2);
    assertEquals("HashTag", hashTag.ruleLabel());
    assertEquals("#chan", hashTag.matchedText());
    assertEquals(16, hashTag.start());
    assertEquals(21, hashTag.end());
  }

  @Test
  void refreshesCompiledRulesWhenSettingsChange() {
    MutableUiSettingsPort settings =
        new MutableUiSettingsPort(
            snapshot(
                new NotificationRule(
                    "Alpha", NotificationRule.Type.WORD, "alpha", true, false, false, null)));

    NotificationRuleMatcher matcher = new NotificationRuleMatcher(settings);
    matcher.start();
    try {
      assertEquals(1, matcher.matchAll("alpha message").size());

      settings.set(
          snapshot(
              new NotificationRule(
                  "Beta", NotificationRule.Type.WORD, "beta", true, false, false, null)));

      assertTrue(matcher.matchAll("alpha message").isEmpty());
      List<NotificationRuleMatch> betaMatches = matcher.matchAll("hello beta");
      assertEquals(1, betaMatches.size());
      assertEquals("Beta", betaMatches.getFirst().ruleLabel());
    } finally {
      matcher.stop();
    }
  }

  @Test
  void invalidRegexRuleIsSkippedWithoutBreakingOtherRules() {
    MutableUiSettingsPort settings =
        new MutableUiSettingsPort(
            snapshot(
                new NotificationRule(
                    "BrokenRegex", NotificationRule.Type.REGEX, "[", true, false, false, null),
                new NotificationRule(
                    "WordOk", NotificationRule.Type.WORD, "ok", true, false, false, null)));

    NotificationRuleMatcher matcher = new NotificationRuleMatcher(settings);
    List<NotificationRuleMatch> matches = matcher.matchAll("ok [");

    assertEquals(1, matches.size());
    assertEquals("WordOk", matches.getFirst().ruleLabel());
    assertTrue(matcher.matchAll("   ").isEmpty());
  }

  private static UiSettingsSnapshot snapshot(NotificationRule... rules) {
    return new UiSettingsSnapshot(List.of(rules), 15, 30, true, true, true, true, true);
  }

  private static final class MutableUiSettingsPort implements UiSettingsPort {
    private final List<PropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile UiSettingsSnapshot snapshot;

    private MutableUiSettingsPort(UiSettingsSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public UiSettingsSnapshot get() {
      return snapshot;
    }

    @Override
    public void addListener(PropertyChangeListener listener) {
      if (listener == null) return;
      listeners.add(listener);
    }

    @Override
    public void removeListener(PropertyChangeListener listener) {
      listeners.remove(listener);
    }

    private void set(UiSettingsSnapshot next) {
      this.snapshot = next;
      PropertyChangeEvent event = new PropertyChangeEvent(this, "settings", null, next);
      for (PropertyChangeListener listener : listeners) {
        listener.propertyChange(event);
      }
    }
  }
}
