package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates roster and monitor status side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorRosterStatusEventHandler {

  interface Callbacks {
    void markPrivateMessagePeerOnline(String serverId, String nick);

    void markPrivateMessagePeerOffline(String serverId, String nick);
  }

  private final cafe.woden.ircclient.app.api.UiPort ui;
  private final TargetCoordinator targetCoordinator;

  public void handleUserHostChanged(String sid, TargetRef status, IrcEvent.UserHostChanged event) {
    targetCoordinator.onUserHostChanged(sid, event);

    String nick = Objects.toString(event.nick(), "").trim();
    List<TargetRef> sharedChannels = targetCoordinator.sharedChannelTargetsForNick(sid, nick);
    String user = Objects.toString(event.user(), "").trim();
    String host = Objects.toString(event.host(), "").trim();
    if (nick.isEmpty()) {
      nick = "(unknown)";
    }
    String rendered = nick + " changed host";
    if (!user.isEmpty() || !host.isEmpty()) {
      String userHost = user + (host.isEmpty() ? "" : ("@" + host));
      rendered = rendered + " to " + userHost;
    }

    postRosterStatusToSharedChannelsOrStatus(
        sharedChannels, status, event.at(), "(chghost)", rendered);
  }

  public void handleUserAwayStateObserved(
      Callbacks callbacks, String sid, IrcEvent.UserAwayStateObserved event) {
    if (event.awayState() == IrcEvent.AwayState.AWAY
        || event.awayState() == IrcEvent.AwayState.HERE) {
      callbacks.markPrivateMessagePeerOnline(sid, event.nick());
    }
    targetCoordinator.onUserAwayStateObserved(sid, event);
  }

  public void handleUserAccountStateObserved(
      Callbacks callbacks, String sid, IrcEvent.UserAccountStateObserved event) {
    if (event.accountState() == IrcEvent.AccountState.LOGGED_IN
        || event.accountState() == IrcEvent.AccountState.LOGGED_OUT) {
      callbacks.markPrivateMessagePeerOnline(sid, event.nick());
    }
    targetCoordinator.onUserAccountStateObserved(sid, event);
  }

  public void handleMonitorOnlineObserved(
      Callbacks callbacks, String sid, IrcEvent.MonitorOnlineObserved event) {
    TargetRef monitor = monitorTarget(sid);
    ui.ensureTargetExists(monitor);
    List<String> nicks = event.nicks();
    if (!nicks.isEmpty()) {
      for (String nick : nicks) {
        callbacks.markPrivateMessagePeerOnline(sid, nick);
      }
      ui.appendStatusAt(monitor, event.at(), "(monitor)", "Online: " + String.join(", ", nicks));
    }
  }

  public void handleMonitorOfflineObserved(
      Callbacks callbacks, String sid, IrcEvent.MonitorOfflineObserved event) {
    TargetRef monitor = monitorTarget(sid);
    ui.ensureTargetExists(monitor);
    List<String> nicks = event.nicks();
    if (!nicks.isEmpty()) {
      for (String nick : nicks) {
        callbacks.markPrivateMessagePeerOffline(sid, nick);
      }
      ui.appendStatusAt(monitor, event.at(), "(monitor)", "Offline: " + String.join(", ", nicks));
    }
  }

  public void handleMonitorListObserved(String sid, IrcEvent.MonitorListObserved event) {
    TargetRef monitor = monitorTarget(sid);
    ui.ensureTargetExists(monitor);
    List<String> nicks = event.nicks();
    String rendered =
        nicks.isEmpty() ? "Monitor list: (empty)" : ("Monitor list: " + String.join(", ", nicks));
    ui.appendStatusAt(monitor, event.at(), "(monitor)", rendered);
  }

  public void handleMonitorListEnded(String sid, IrcEvent.MonitorListEnded event) {
    TargetRef monitor = monitorTarget(sid);
    ui.ensureTargetExists(monitor);
    ui.appendStatusAt(monitor, event.at(), "(monitor)", "End of monitor list.");
  }

  public void handleMonitorListFull(String sid, IrcEvent.MonitorListFull event) {
    TargetRef monitor = monitorTarget(sid);
    ui.ensureTargetExists(monitor);

    String message = Objects.toString(event.message(), "").trim();
    if (message.isEmpty()) {
      message = "Monitor list is full.";
    }
    if (event.limit() > 0) {
      message = message + " (limit=" + event.limit() + ")";
    }
    if (event.nicks() != null && !event.nicks().isEmpty()) {
      message = message + " nicks=" + String.join(", ", event.nicks());
    }
    ui.appendErrorAt(monitor, event.at(), "(monitor)", message);
  }

  public void handleUserSetNameObserved(
      String sid, TargetRef status, IrcEvent.UserSetNameObserved event) {
    targetCoordinator.onUserSetNameObserved(sid, event);
    if (event.source() != IrcEvent.UserSetNameObserved.Source.SETNAME) {
      return;
    }

    String nick = Objects.toString(event.nick(), "").trim();
    List<TargetRef> sharedChannels = targetCoordinator.sharedChannelTargetsForNick(sid, nick);
    String realName = Objects.toString(event.realName(), "").trim();
    if (nick.isEmpty()) {
      nick = "(unknown)";
    }
    if (realName.isEmpty()) {
      realName = "(empty)";
    }
    String rendered = nick + " set name to: " + realName;

    postRosterStatusToSharedChannelsOrStatus(
        sharedChannels, status, event.at(), "(setname)", rendered);
  }

  private void postRosterStatusToSharedChannelsOrStatus(
      List<TargetRef> sharedChannels, TargetRef status, Instant at, String tag, String rendered) {
    if (sharedChannels != null && !sharedChannels.isEmpty()) {
      for (TargetRef dest : sharedChannels) {
        ui.ensureTargetExists(dest);
        ui.appendStatusAt(dest, at, tag, rendered);
      }
      return;
    }

    TargetRef dest = status != null ? status : targetCoordinator.safeStatusTarget();
    ui.ensureTargetExists(dest);
    ui.appendStatusAt(dest, at, tag, rendered);
  }

  private static TargetRef monitorTarget(String sid) {
    return TargetRef.monitorGroup(sid);
  }
}
