package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates invite handling side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorInviteEventHandler {

  interface Callbacks {
    void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

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

    boolean isMutedChannel(String serverId, String channel);

    void addDisposable(Disposable disposable);
  }

  private final IrcMediatorInteractionPort irc;
  private final UiPort ui;
  private final PendingInvitePort pendingInviteState;
  private final TrayNotificationsPort trayNotificationService;
  private final ConnectionCoordinator connectionCoordinator;
  private final IrcEventNotifierPort ircEventNotifierPort;

  public void handleInvitedToChannel(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.InvitedToChannel event) {
    TargetRef dest = status;
    String inviter = Objects.toString(event.from(), "").trim();
    if (inviter.isEmpty()) {
      inviter = "server";
    }
    String channel = Objects.toString(event.channel(), "").trim();
    if (channel.isEmpty()) {
      String invalidLine = inviter + " sent an invalid invite.";
      callbacks.postTo(dest, true, d -> ui.appendStatusAt(d, event.at(), "(invite)", invalidLine));
      callbacks.recordInterceptorEvent(
          sid,
          "status",
          inviter,
          callbacks.learnedHostmaskForNick(sid, inviter),
          invalidLine,
          InterceptorEventType.INVITE);
      return;
    }

    String invitee = Objects.toString(event.invitee(), "").trim();
    String selfNick = irc.currentNick(sid).orElse("");
    boolean isSelfInvite =
        invitee.isBlank() || (!selfNick.isBlank() && invitee.equalsIgnoreCase(selfNick));

    if (!isSelfInvite) {
      String rendered = inviter + " invited " + invitee + " to " + channel;
      callbacks.postTo(
          dest, true, d -> ui.appendStatusAt(d, event.at(), "(invite-notify)", rendered));
      callbacks.recordInterceptorEvent(
          sid,
          channel,
          inviter,
          callbacks.learnedHostmaskForNick(sid, inviter),
          rendered,
          InterceptorEventType.INVITE);
      return;
    }

    PendingInvitePort.RecordResult recorded =
        pendingInviteState.record(
            event.at(), sid, channel, inviter, invitee, event.reason(), event.inviteNotify());
    PendingInvitePort.PendingInvite invite = recorded.invite();
    TargetRef inviteStatus = new TargetRef(sid, "status");

    if (recorded.collapsed()) {
      String rendered =
          inviter + " invited you to " + channel + " (repeated x" + invite.repeatCount() + ")";
      callbacks.postTo(dest, true, d -> ui.appendStatusAt(d, event.at(), "(invite)", rendered));
      return;
    }

    String reason = Objects.toString(invite.reason(), "").trim();
    String rendered = inviter + " invited you to " + channel + " on " + sid;
    if (!reason.isEmpty()) {
      rendered = rendered + " (" + reason + ")";
    }
    String actions =
        "Actions: /invjoin "
            + invite.id()
            + " | /join -i"
            + " | /invignore "
            + invite.id()
            + " | /invwhois "
            + invite.id()
            + " | /invblock "
            + invite.id()
            + " | /invites";

    String finalRendered = rendered;
    callbacks.postTo(
        dest,
        true,
        d -> {
          ui.appendStatusAt(d, event.at(), "(invite)", finalRendered);
          ui.appendStatus(d, "(invite)", actions);
        });
    callbacks.recordInterceptorEvent(
        sid,
        channel,
        inviter,
        callbacks.learnedHostmaskForNick(sid, inviter),
        finalRendered,
        InterceptorEventType.INVITE);

    maybeNotifyInviteReceived(callbacks, sid, channel, inviter, reason, finalRendered);
    maybeAutoJoinInvite(callbacks, sid, channel, inviteStatus, invite);
  }

  private void maybeNotifyInviteReceived(
      Callbacks callbacks,
      String sid,
      String channel,
      String inviter,
      String reason,
      String rendered) {
    boolean customInviteNotified =
        callbacks.notifyIrcEvent(
            IrcEventNotificationRule.EventType.INVITE_RECEIVED,
            sid,
            channel,
            inviter,
            "Invite" + (channel.isBlank() ? "" : " to " + channel),
            rendered);
    boolean inviteRulesEnabled =
        ircEventNotifierPort != null
            && ircEventNotifierPort.hasEnabledRuleFor(
                IrcEventNotificationRule.EventType.INVITE_RECEIVED);
    if (!customInviteNotified && !inviteRulesEnabled && !callbacks.isMutedChannel(sid, channel)) {
      try {
        trayNotificationService.notifyInvite(sid, channel, inviter, reason);
      } catch (Exception ignored) {
      }
    }
  }

  private void maybeAutoJoinInvite(
      Callbacks callbacks,
      String sid,
      String channel,
      TargetRef inviteStatus,
      PendingInvitePort.PendingInvite invite) {
    if (!pendingInviteState.inviteAutoJoinEnabled()) {
      return;
    }
    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(inviteStatus, "(invite)", "Auto-join is enabled, but you are not connected.");
      return;
    }
    if (containsCrlf(channel)) {
      ui.appendStatus(inviteStatus, "(invite)", "Refusing to auto-join malformed invite channel.");
      return;
    }

    ui.appendStatus(inviteStatus, "(invite)", "Auto-join enabled, joining " + channel + "...");
    callbacks.addDisposable(
        irc.joinChannel(sid, channel)
            .subscribe(
                () -> pendingInviteState.remove(invite.id()),
                err -> ui.appendError(inviteStatus, "(invite-error)", String.valueOf(err))));
  }

  private static boolean containsCrlf(String value) {
    return value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
  }
}
