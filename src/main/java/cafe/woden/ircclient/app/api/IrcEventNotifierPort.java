package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.IrcEventNotificationRule;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for dispatching configured IRC event notifications. */
@ApplicationLayer
public interface IrcEventNotifierPort {

  boolean notifyConfigured(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body,
      String activeServerId,
      String activeTarget);

  boolean hasEnabledRuleFor(IrcEventNotificationRule.EventType eventType);
}
