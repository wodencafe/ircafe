package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates channel membership and join-flow side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorChannelMembershipEventHandler {
  private static final Duration JOIN_ROUTING_WINDOW = Duration.ofSeconds(15);

  interface Callbacks {
    void observeChannelActivity(String serverId, String channel);

    void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

    boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body);

    void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType);

    String learnedHostmaskForNick(String sid, String nick);

    TargetRef resolveActiveOrStatus(String sid, TargetRef status);

    TargetRef safeStatusTarget();

    void markPrivateMessagePeerOffline(String serverId, String nick);

    void maybeNotifyUserKlineFromQuit(String serverId, IrcEvent.UserQuitChannel event);

    void maybeNotifyNetsplitDetected(String serverId, IrcEvent.UserQuitChannel event);
  }

  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final InboundModeEventHandler inboundModeEventHandler;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final JoinRoutingPort joinRoutingState;
  private final IrcSessionRuntimeConfigPort runtimeConfig;
  private final ServerRegistry serverRegistry;

  public void handleUserJoinedChannel(
      Callbacks callbacks, String sid, IrcEvent.UserJoinedChannel event) {
    callbacks.observeChannelActivity(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    ui.ensureTargetExists(channel);
    ui.appendPresence(channel, PresenceEvent.join(event.nick()));
    String joinedNick = Objects.toString(event.nick(), "").trim();
    String body = (joinedNick.isEmpty() ? "Someone" : joinedNick) + " joined " + event.channel();
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.USER_JOINED,
        sid,
        event.channel(),
        joinedNick,
        "Join in " + event.channel(),
        body);
    callbacks.recordInterceptorEvent(
        sid,
        event.channel(),
        joinedNick,
        callbacks.learnedHostmaskForNick(sid, joinedNick),
        body,
        InterceptorEventType.JOIN);
  }

  public void handleUserPartedChannel(
      Callbacks callbacks, String sid, IrcEvent.UserPartedChannel event) {
    callbacks.observeChannelActivity(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    ui.ensureTargetExists(channel);
    ui.appendPresence(channel, PresenceEvent.part(event.nick(), event.reason()));
    String targetChannel = Objects.toString(event.channel(), "").trim();
    String nick = Objects.toString(event.nick(), "").trim();
    String reason = Objects.toString(event.reason(), "").trim();
    String body = (nick.isEmpty() ? "Someone" : nick) + " parted " + targetChannel;
    if (!reason.isEmpty()) {
      body = body + " (" + reason + ")";
    }
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.USER_PARTED,
        sid,
        targetChannel,
        nick,
        "Part in " + targetChannel,
        body);
    callbacks.recordInterceptorEvent(
        sid,
        targetChannel,
        nick,
        callbacks.learnedHostmaskForNick(sid, nick),
        body,
        InterceptorEventType.PART);
  }

  public void handleLeftChannel(String sid, IrcEvent.LeftChannel event) {
    TargetRef status = new TargetRef(sid, "status");
    String rendered = "You left " + event.channel();
    String reason = Objects.toString(event.reason(), "").trim();
    if (!reason.isEmpty()) {
      rendered = rendered + " (" + reason + ")";
    }
    String detachedWarning = reason.isEmpty() ? "Removed from channel by server." : reason;

    ui.ensureTargetExists(status);
    ui.appendStatusAt(status, event.at(), "(part)", rendered);
    inboundModeEventHandler.onLeftChannel(sid, event.channel());
    targetCoordinator.onChannelMembershipLost(sid, event.channel(), true, detachedWarning);
  }

  public void handleUserKickedFromChannel(
      Callbacks callbacks, String sid, IrcEvent.UserKickedFromChannel event) {
    TargetRef channel = new TargetRef(sid, event.channel());
    String rendered = renderOtherKick(event.nick(), event.by(), event.reason());
    callbacks.postTo(channel, true, d -> ui.appendStatusAt(d, event.at(), "(kick)", rendered));
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.KICKED,
        sid,
        event.channel(),
        event.by(),
        "Kick in " + event.channel(),
        rendered);
    callbacks.recordInterceptorEvent(
        sid,
        event.channel(),
        event.by(),
        callbacks.learnedHostmaskForNick(sid, event.by()),
        rendered,
        InterceptorEventType.KICK);
  }

  public void handleKickedFromChannel(
      Callbacks callbacks, String sid, IrcEvent.KickedFromChannel event) {
    TargetRef channel = new TargetRef(sid, event.channel());
    TargetRef status = new TargetRef(sid, "status");
    String rendered = renderSelfKick(event.channel(), event.by(), event.reason());

    ui.ensureTargetExists(channel);
    ui.appendErrorAt(channel, event.at(), "(kick)", rendered);
    ui.ensureTargetExists(status);
    ui.appendErrorAt(status, event.at(), "(kick)", rendered);

    inboundModeEventHandler.onLeftChannel(sid, event.channel());
    String by = Objects.toString(event.by(), "").trim();
    String reason = Objects.toString(event.reason(), "").trim();
    String detachedWarning = "Kicked" + (by.isEmpty() ? "" : (" by " + by));
    if (!reason.isEmpty()) {
      detachedWarning = detachedWarning + " (" + reason + ")";
    }
    targetCoordinator.onChannelMembershipLost(sid, event.channel(), true, detachedWarning);
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.YOU_KICKED,
        sid,
        event.channel(),
        event.by(),
        "You were kicked from " + event.channel(),
        rendered);
    callbacks.recordInterceptorEvent(
        sid,
        event.channel(),
        event.by(),
        callbacks.learnedHostmaskForNick(sid, event.by()),
        rendered,
        InterceptorEventType.KICK);
  }

  public void handleUserQuitChannel(
      Callbacks callbacks, String sid, IrcEvent.UserQuitChannel event) {
    callbacks.observeChannelActivity(sid, event.channel());
    callbacks.markPrivateMessagePeerOffline(sid, event.nick());
    TargetRef channel = new TargetRef(sid, event.channel());
    ui.ensureTargetExists(channel);
    ui.appendPresence(channel, PresenceEvent.quit(event.nick(), event.reason()));
    String targetChannel = Objects.toString(event.channel(), "").trim();
    String nick = Objects.toString(event.nick(), "").trim();
    String reason = Objects.toString(event.reason(), "").trim();
    String body = (nick.isEmpty() ? "Someone" : nick) + " quit";
    if (!reason.isEmpty()) {
      body = body + " (" + reason + ")";
    }
    if (!targetChannel.isEmpty()) {
      body = body + " while in " + targetChannel;
    }
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.USER_QUIT,
        sid,
        targetChannel,
        nick,
        "Quit" + (targetChannel.isEmpty() ? "" : " in " + targetChannel),
        body);
    callbacks.recordInterceptorEvent(
        sid,
        targetChannel,
        nick,
        callbacks.learnedHostmaskForNick(sid, nick),
        body,
        InterceptorEventType.QUIT);
    callbacks.maybeNotifyUserKlineFromQuit(sid, event);
    callbacks.maybeNotifyNetsplitDetected(sid, event);
  }

  public void handleUserNickChangedChannel(
      Callbacks callbacks, String sid, IrcEvent.UserNickChangedChannel event) {
    callbacks.observeChannelActivity(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    ui.ensureTargetExists(channel);
    ui.appendPresence(channel, PresenceEvent.nick(event.oldNick(), event.newNick()));
    String targetChannel = Objects.toString(event.channel(), "").trim();
    String oldNick = Objects.toString(event.oldNick(), "").trim();
    String newNick = Objects.toString(event.newNick(), "").trim();
    String body =
        (oldNick.isEmpty() ? "(unknown)" : oldNick)
            + " is now known as "
            + (newNick.isEmpty() ? "(unknown)" : newNick)
            + (targetChannel.isEmpty() ? "" : " in " + targetChannel);
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.USER_NICK_CHANGED,
        sid,
        targetChannel,
        oldNick,
        "Nick changed" + (targetChannel.isEmpty() ? "" : " in " + targetChannel),
        body);
    callbacks.recordInterceptorEvent(
        sid,
        targetChannel,
        oldNick,
        callbacks.learnedHostmaskForNick(sid, oldNick),
        body,
        InterceptorEventType.NICK);
  }

  public void handleChannelRedirected(String sid, IrcEvent.ChannelRedirected event) {
    String fromChannel = Objects.toString(event.fromChannel(), "").trim();
    String toChannel = Objects.toString(event.toChannel(), "").trim();
    if (fromChannel.isEmpty() || toChannel.isEmpty()) {
      return;
    }

    TargetRef origin = joinRoutingState.recentOriginIfFresh(sid, fromChannel, JOIN_ROUTING_WINDOW);
    if (origin != null) {
      joinRoutingState.rememberOrigin(sid, toChannel, origin);
    }
    joinRoutingState.clear(sid, fromChannel);

    if (!isQuasselCoreServer(sid)) {
      runtimeConfig.rememberJoinedChannel(sid, toChannel);
    }
    targetCoordinator.joinChannel(new TargetRef(sid, toChannel));
  }

  public void handleJoinedChannel(String sid, IrcEvent.JoinedChannel event) {
    TargetRef channel = new TargetRef(sid, event.channel());
    TargetRef joinOrigin =
        joinRoutingState.recentOriginIfFresh(sid, event.channel(), JOIN_ROUTING_WINDOW);
    joinRoutingState.clear(sid, event.channel());
    connectionCoordinator.noteJoinedChannel(sid, event.channel());

    if (!targetCoordinator.onJoinedChannel(sid, event.channel())) {
      connectionCoordinator.clearJoinedChannelObservation(sid, event.channel());
      TargetRef status = new TargetRef(sid, "status");
      ui.ensureTargetExists(status);
      ui.appendStatusAt(
          status,
          event.at(),
          "(join)",
          "Stayed disconnected from "
              + event.channel()
              + " (right-click channel and choose Reconnect).");
      return;
    }

    if (!isQuasselCoreServer(sid)) {
      runtimeConfig.rememberJoinedChannel(sid, event.channel());
      targetCoordinator.syncRuntimeAutoJoinForReconnect(sid);
    }
    inboundModeEventHandler.onJoinedChannel(sid, event.channel());
    userInfoEnrichmentService.enqueueWhoChannelPrioritized(sid, event.channel());

    ui.ensureTargetExists(channel);
    ui.appendStatus(channel, "(join)", "Joined " + event.channel());
    if (joinOrigin != null) {
      ui.selectTarget(channel);
    }
  }

  public void handleJoinFailed(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.JoinFailed event) {
    TargetRef origin =
        joinRoutingState.recentOriginIfFresh(sid, event.channel(), JOIN_ROUTING_WINDOW);
    joinRoutingState.clear(sid, event.channel());

    TargetRef dest = origin;
    if (dest == null) {
      dest = callbacks.resolveActiveOrStatus(sid, status);
    }
    if (dest == null) {
      dest = callbacks.safeStatusTarget();
    }

    String message = Objects.toString(event.message(), "").trim();
    if (message.isEmpty()) {
      message = "Join failed";
    }

    String rendered;
    String messageLower = message.toLowerCase(java.util.Locale.ROOT);
    if (messageLower.startsWith("cannot join")) {
      rendered = message + " [" + event.code() + "]";
    } else {
      rendered = "Cannot join " + event.channel() + " [" + event.code() + "]: " + message;
    }

    ui.ensureTargetExists(dest);
    ui.appendError(dest, "(join)", rendered);
    if (status != null && !dest.equals(status)) {
      ui.appendError(status, "(join)", rendered);
    }
  }

  private boolean isQuasselCoreServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return false;
    }
    if (serverRegistry == null) {
      return false;
    }
    try {
      Optional<IrcProperties.Server> configured = serverRegistry.find(sid);
      if (configured == null || configured.isEmpty()) {
        return false;
      }
      return configured.orElseThrow().backend() == IrcProperties.Server.Backend.QUASSEL_CORE;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String renderOtherKick(String nick, String by, String reason) {
    String targetNick = Objects.toString(nick, "").trim();
    String actor = Objects.toString(by, "").trim();
    String detail = Objects.toString(reason, "").trim();
    if (targetNick.isEmpty()) {
      targetNick = "(unknown)";
    }
    if (actor.isEmpty()) {
      actor = "server";
    }
    String base = targetNick + " was kicked by " + actor;
    return detail.isEmpty() ? base : base + " (" + detail + ")";
  }

  private static String renderSelfKick(String channel, String by, String reason) {
    String targetChannel = Objects.toString(channel, "").trim();
    String actor = Objects.toString(by, "").trim();
    String detail = Objects.toString(reason, "").trim();
    if (targetChannel.isEmpty()) {
      targetChannel = "(unknown channel)";
    }
    if (actor.isEmpty()) {
      actor = "server";
    }
    String base = "You were kicked from " + targetChannel + " by " + actor;
    return detail.isEmpty() ? base : base + " (" + detail + ")";
  }
}
