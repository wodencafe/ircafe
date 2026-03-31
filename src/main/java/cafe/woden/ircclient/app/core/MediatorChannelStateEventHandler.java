package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates channel and target state updates extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorChannelStateEventHandler {

  interface Callbacks extends MediatorAlertNotificationHandler.Callbacks {
    void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType);

    String learnedHostmaskForNick(String serverId, String nick);
  }

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final InboundModeEventHandler inboundModeEventHandler;
  private final MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator;
  private final MediatorAlertNotificationHandler mediatorAlertNotificationHandler;

  public void handleChannelModeObserved(
      Callbacks callbacks, String sid, IrcEvent.ChannelModeObserved event) {
    observeChannelActivity(sid, event.channel());
    inboundModeEventHandler.handleChannelModeObserved(sid, event);
    if (event.kind() == IrcEvent.ChannelModeKind.DELTA) {
      mediatorAlertNotificationHandler.maybeNotifyModeEvents(callbacks, sid, event);
      callbacks.recordInterceptorEvent(
          sid,
          event.channel(),
          event.by(),
          callbacks.learnedHostmaskForNick(sid, event.by()),
          event.details(),
          InterceptorEventType.MODE);
    }
  }

  public void handleChannelTopicUpdated(
      Callbacks callbacks, String sid, IrcEvent.ChannelTopicUpdated event) {
    observeChannelActivity(sid, event.channel());
    inboundModeEventHandler.onChannelTopicUpdated(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    ui.ensureTargetExists(channel);
    ui.setChannelTopic(channel, event.topic());
    String name = Objects.toString(event.channel(), "").trim();
    String topic = Objects.toString(event.topic(), "").trim();
    String body =
        topic.isEmpty() ? "Topic cleared in " + name : "Topic changed in " + name + ": " + topic;
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.TOPIC_CHANGED,
        sid,
        name,
        null,
        "Topic changed" + (name.isEmpty() ? "" : " in " + name),
        body);
    callbacks.recordInterceptorEvent(
        sid,
        name,
        "server",
        "",
        topic.isEmpty() ? "(topic cleared)" : topic,
        InterceptorEventType.TOPIC);
  }

  public void handleChatHistoryBatchReceived(String sid, IrcEvent.ChatHistoryBatchReceived event) {
    observeChannelActivity(sid, event.target());
    mediatorHistoryIngestOrchestrator.onChatHistoryBatchReceived(sid, event);
  }

  public void handleZncPlaybackBatchReceived(String sid, IrcEvent.ZncPlaybackBatchReceived event) {
    mediatorHistoryIngestOrchestrator.onZncPlaybackBatchReceived(sid, event);
  }

  public void handleNickListUpdated(String sid, IrcEvent.NickListUpdated event) {
    observeChannelActivity(sid, event.channel());
    inboundModeEventHandler.onNickListUpdated(sid, event.channel());
    targetCoordinator.onNickListUpdated(sid, event);
  }

  public void handleUserHostmaskObserved(String sid, IrcEvent.UserHostmaskObserved event) {
    targetCoordinator.onUserHostmaskObserved(sid, event);
  }

  public void observeChannelActivity(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) {
      return;
    }
    try {
      TargetRef target = new TargetRef(sid, ch);
      if (!target.isChannel()) {
        return;
      }
    } catch (IllegalArgumentException ignored) {
      return;
    }
    targetCoordinator.onChannelActivityObserved(sid, ch);
  }
}
