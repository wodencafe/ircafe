package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared notification and interceptor behavior extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorNotificationSupport {

  private final IrcEventNotifierPort ircEventNotifierPort;
  private final InterceptorIngestPort interceptorIngestPort;
  private final UserListStore userListStore;
  private final TargetCoordinator targetCoordinator;
  private final MediatorTargetUiSupport mediatorTargetUiSupport;

  public boolean notifyIrcEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body) {
    return notifyIrcEvent(eventType, serverId, channel, sourceNick, title, body, null, null);
  }

  public boolean notifyIrcEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body,
      String ctcpCommand,
      String ctcpValue) {
    if (eventType == null || ircEventNotifierPort == null) {
      return false;
    }
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || mediatorTargetUiSupport.isMutedChannel(sid, channel)) {
      return false;
    }

    String source = Objects.toString(sourceNick, "").trim();
    Boolean sourceIsSelf =
        source.isEmpty() ? null : mediatorTargetUiSupport.isFromSelf(sid, source);
    TargetRef active = targetCoordinator != null ? targetCoordinator.getActiveTarget() : null;
    String activeSid = active != null ? active.serverId() : null;
    String activeTarget = active != null ? active.target() : null;
    try {
      return ircEventNotifierPort.notifyConfigured(
          eventType,
          sid,
          channel,
          source,
          sourceIsSelf,
          title,
          body,
          activeSid,
          activeTarget,
          ctcpCommand,
          ctcpValue);
    } catch (Exception ignored) {
      return false;
    }
  }

  public void recordInterceptorEvent(
      String serverId,
      String channel,
      String fromNick,
      String fromHostmask,
      String text,
      InterceptorEventType eventType) {
    recordInterceptorEvent(serverId, channel, fromNick, fromHostmask, text, eventType, "");
  }

  public void recordInterceptorEvent(
      String serverId,
      String channel,
      String fromNick,
      String fromHostmask,
      String text,
      InterceptorEventType eventType,
      String messageId) {
    if (interceptorIngestPort == null) {
      return;
    }
    String sid = Objects.toString(serverId, "").trim();
    String from = Objects.toString(fromNick, "").trim();
    if (sid.isEmpty() || from.isEmpty() || mediatorTargetUiSupport.isFromSelf(sid, from)) {
      return;
    }
    interceptorIngestPort.ingestEvent(
        sid,
        channel,
        from,
        Objects.toString(fromHostmask, "").trim(),
        Objects.toString(text, "").trim(),
        eventType,
        Objects.toString(messageId, "").trim());
  }

  public String learnedHostmaskForNick(String serverId, String nick) {
    if (userListStore == null) {
      return "";
    }
    String sid = Objects.toString(serverId, "").trim();
    String normalizedNick = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || normalizedNick.isEmpty()) {
      return "";
    }
    String hostmask = userListStore.getLearnedHostmask(sid, normalizedNick);
    return Objects.toString(hostmask, "").trim();
  }
}
