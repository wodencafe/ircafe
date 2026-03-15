package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.IrcEventNotificationRule;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for dispatching configured IRC event notifications. */
@SecondaryPort
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

  default boolean notifyConfigured(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body,
      String activeServerId,
      String activeTarget,
      String ctcpCommand,
      String ctcpValue) {
    return notifyConfigured(
        eventType,
        serverId,
        channel,
        sourceNick,
        sourceIsSelf,
        title,
        body,
        activeServerId,
        activeTarget);
  }

  boolean hasEnabledRuleFor(IrcEventNotificationRule.EventType eventType);
}
