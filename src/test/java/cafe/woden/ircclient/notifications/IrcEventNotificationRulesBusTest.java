package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrcEventNotificationRulesBusTest {

  @Test
  void nullConfigFallsBackToDefaultRules() {
    IrcEventNotificationRulesBus bus = new IrcEventNotificationRulesBus(null);

    List<IrcEventNotificationRule> defaults = bus.get();
    assertNotNull(defaults);
    assertFalse(defaults.isEmpty());
    assertEquals(IrcEventNotificationRule.defaults(), defaults);
  }

  @Test
  void setSanitizesNullRulesAndNotifiesListeners() {
    IrcEventNotificationRulesBus bus = new IrcEventNotificationRulesBus(null);
    List<PropertyChangeEvent> events = new ArrayList<>();
    var listener = (java.beans.PropertyChangeListener) events::add;
    bus.addListener(listener);
    try {
      IrcEventNotificationRule keep =
          new IrcEventNotificationRule(
              true,
              IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
              IrcEventNotificationRule.SourceMode.ANY,
              null,
              IrcEventNotificationRule.ChannelScope.ALL,
              null,
              true,
              IrcEventNotificationRule.FocusScope.ANY,
              true,
              true,
              false,
              "NOTIF_1",
              false,
              null,
              false,
              null,
              null,
              null);

      List<IrcEventNotificationRule> mixed = new ArrayList<>();
      mixed.add(null);
      mixed.add(keep);
      mixed.add(null);
      bus.set(mixed);
      assertEquals(List.of(keep), bus.get());
      assertEquals(1, events.size());
      assertEquals(
          IrcEventNotificationRulesBus.PROP_IRC_EVENT_NOTIFICATION_RULES,
          events.getFirst().getPropertyName());
      assertTrue(events.getFirst().getOldValue() instanceof List<?>);
      assertEquals(List.of(keep), events.getFirst().getNewValue());
    } finally {
      bus.removeListener(listener);
    }
  }

  @Test
  void refreshDoesNotPublishWhenRulesUnchanged() {
    IrcEventNotificationRulesBus bus = new IrcEventNotificationRulesBus(null);
    List<PropertyChangeEvent> events = new ArrayList<>();
    var listener = (java.beans.PropertyChangeListener) events::add;
    bus.addListener(listener);
    try {
      bus.refresh();
      assertTrue(events.isEmpty());
    } finally {
      bus.removeListener(listener);
    }
  }
}
