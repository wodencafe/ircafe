package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates server/status event rendering side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorServerStatusEventHandler {
  private static final Duration AWAY_ORIGIN_MAX_AGE = Duration.ofSeconds(15);

  interface Callbacks {
    TargetRef safeStatusTarget();

    void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

    void handleStandardReply(String sid, TargetRef status, IrcEvent.StandardReply event);

    void handleServerResponseLine(String sid, TargetRef status, IrcEvent.ServerResponseLine event);

    TargetRef resolveActiveOrStatus(String sid, TargetRef status);

    void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType);

    String learnedHostmaskForNick(String sid, String nick);

    boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body);
  }

  private final IrcMediatorInteractionPort irc;
  private final UiPort ui;
  private final AwayRoutingPort awayRoutingState;
  private final WhoisRoutingPort whoisRoutingState;

  public void handleNickChanged(String sid, TargetRef status, IrcEvent.NickChanged event) {
    irc.currentNick(sid)
        .ifPresent(
            currentNick -> {
              if (!Objects.equals(currentNick, event.oldNick())
                  && !Objects.equals(currentNick, event.newNick())) {
                ui.appendNotice(
                    status, "(nick)", event.oldNick() + " is now known as " + event.newNick());
              } else {
                ui.appendStatus(status, "(nick)", "Now known as " + event.newNick());
                ui.setChatCurrentNick(sid, event.newNick());
              }
            });
  }

  public void handleWallopsReceived(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.WallopsReceived event) {
    TargetRef dest = statusOrSafe(callbacks, status);
    String from = Objects.toString(event.from(), "").trim();
    if (from.isEmpty()) {
      from = "server";
    }
    String body = Objects.toString(event.text(), "").trim();
    if (body.isEmpty()) {
      body = "(empty WALLOPS)";
    }

    String rendered = from + ": " + body;
    callbacks.postTo(dest, true, d -> ui.appendStatusAt(d, event.at(), "(wallops)", rendered));
    callbacks.recordInterceptorEvent(
        sid,
        "status",
        from,
        callbacks.learnedHostmaskForNick(sid, from),
        body,
        InterceptorEventType.SERVER);

    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.WALLOPS_RECEIVED,
        sid,
        null,
        from,
        from.equalsIgnoreCase("server") ? "WALLOPS" : ("WALLOPS from " + from),
        body);
  }

  public void handleServerTimeNotNegotiated(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ServerTimeNotNegotiated event) {
    TargetRef dest = statusOrSafe(callbacks, status);
    ui.appendStatus(dest, "(ircv3)", event.message());
    callbacks.recordInterceptorEvent(
        sid, "status", "server", "", event.message(), InterceptorEventType.SERVER);
  }

  public void handleStandardReplyEvent(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.StandardReply event) {
    callbacks.handleStandardReply(sid, status, event);
    callbacks.recordInterceptorEvent(
        sid,
        "status",
        "server",
        "",
        Objects.toString(event.description(), "").trim(),
        InterceptorEventType.SERVER);
  }

  public void handleChannelListStarted(String sid, IrcEvent.ChannelListStarted event) {
    ui.beginChannelList(sid, event.banner());
  }

  public void handleChannelListEntry(String sid, IrcEvent.ChannelListEntry event) {
    ui.appendChannelListEntry(sid, event.channel(), event.visibleUsers(), event.topic());
  }

  public void handleChannelListEnded(String sid, IrcEvent.ChannelListEnded event) {
    ui.endChannelList(sid, event.summary());
  }

  public void handleChannelBanListStarted(String sid, IrcEvent.ChannelBanListStarted event) {
    ui.beginChannelBanList(sid, event.channel());
  }

  public void handleChannelBanListEntry(String sid, IrcEvent.ChannelBanListEntry event) {
    ui.appendChannelBanListEntry(
        sid, event.channel(), event.mask(), event.setBy(), event.setAtEpochSeconds());
  }

  public void handleChannelBanListEnded(String sid, IrcEvent.ChannelBanListEnded event) {
    ui.endChannelBanList(sid, event.channel(), event.summary());
  }

  public void handleServerResponseLineEvent(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ServerResponseLine event) {
    callbacks.handleServerResponseLine(sid, status, event);
    String rawLine = Objects.toString(event.rawLine(), "").trim();
    if (rawLine.isEmpty()) {
      rawLine = Objects.toString(event.message(), "").trim();
    }
    callbacks.recordInterceptorEvent(
        sid, "status", "server", "", rawLine, InterceptorEventType.SERVER);
  }

  public void handleAwayStatusChanged(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.AwayStatusChanged event) {
    awayRoutingState.setAway(sid, event.away());
    if (!event.away()) {
      awayRoutingState.setLastReason(sid, null);
    }
    TargetRef dest = null;
    TargetRef origin = awayRoutingState.recentOriginIfFresh(sid, AWAY_ORIGIN_MAX_AGE);
    if (origin != null && Objects.equals(origin.serverId(), sid)) {
      dest = origin;
    }
    if (dest == null) {
      dest = callbacks.resolveActiveOrStatus(sid, status);
    }

    final String rendered;
    if (event.away()) {
      String reason = awayRoutingState.getLastReason(sid);
      if (reason != null && !reason.isBlank()) {
        rendered = "You are now marked as being away (Reason: " + reason + ")";
      } else {
        rendered = event.message();
      }
    } else {
      rendered = "You are no longer marked as being away";
    }

    TargetRef finalDest = dest;
    callbacks.postTo(finalDest, true, d -> ui.appendStatus(d, "(away)", rendered));
  }

  public void handleWhoisResult(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.WhoisResult event) {
    TargetRef dest = whoisRoutingState.remove(sid, event.nick());
    if (dest == null) {
      dest = status;
    }
    callbacks.postTo(
        dest,
        true,
        d -> {
          ui.appendStatus(d, "(whois)", "WHOIS for " + event.nick());
          for (String line : event.lines()) {
            ui.appendStatus(d, "(whois)", line);
          }
        });
  }

  private static TargetRef statusOrSafe(Callbacks callbacks, TargetRef status) {
    return status != null ? status : callbacks.safeStatusTarget();
  }
}
