package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /query, /msg, /notice, /me and shared message send flow. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundMessagingCommandService {

  @NonNull private final IrcClientService irc;
  @NonNull private final IrcEchoCapabilityPort echoCapabilityPort;
  @NonNull private final OutboundMultilineMessageSupport outboundMultilineMessageSupport;
  @NonNull private final OutboundConnectionStatusSupport outboundConnectionStatusSupport;
  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final PendingEchoMessagePort pendingEchoMessageState;

  void handleQuery(String nick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(query)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(query)", "Usage: /query <nick>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  void handleMsg(CompositeDisposable disposables, String nick, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(msg)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(msg)", "Usage: /msg <nick> <message>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
    sendMessage(disposables, pm, m);
  }

  void handleNotice(CompositeDisposable disposables, String target, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(notice)", "Select a server first.");
      return;
    }

    String t = target == null ? "" : target.trim();
    String m = body == null ? "" : body.trim();
    if (t.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(notice)", "Usage: /notice <target> <message>");
      return;
    }

    sendNotice(disposables, at, t, m);
  }

  void handleMe(CompositeDisposable disposables, String action) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(me)", "Select a server first.");
      return;
    }

    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus(at, "(me)", "Usage: /me <action>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(me)", "Select a channel or PM first.");
      return;
    }

    if (!outboundConnectionStatusSupport.ensureConnectedStatusOnly(at)) {
      return;
    }

    if (shouldUseLocalEcho(at.serverId())) {
      String me = irc.currentNick(at.serverId()).orElse("me");
      ui.appendAction(at, me, a, true);
    }

    disposables.add(
        irc.sendAction(at.serverId(), at.target(), a)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(send-error)",
                        String.valueOf(err))));
  }

  void sendMessage(CompositeDisposable disposables, TargetRef target, String message) {
    if (target == null) return;
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) return;

    if (!outboundConnectionStatusSupport.ensureConnected(target)) {
      return;
    }

    OutboundMultilineMessageSupport.MultilineSendPlan plan =
        outboundMultilineMessageSupport.plan(target, m, "(send)");
    if (plan.shouldCancel()) {
      return;
    }
    if (plan.shouldSplitLines()) {
      for (String line : plan.lines()) {
        sendMessage(disposables, target, line);
      }
      return;
    }
    m = plan.payload();

    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    String me = irc.currentNick(target.serverId()).orElse("me");
    final PendingEchoMessagePort.PendingOutboundChat pendingEntry;
    if (useLocalEcho) {
      pendingEntry = null;
    } else {
      pendingEntry = pendingEchoMessageState.register(target, me, m, Instant.now());
      ui.appendPendingOutgoingChat(
          target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, m);
    }

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m)
            .subscribe(
                () -> {},
                err -> {
                  if (pendingEntry != null) {
                    pendingEchoMessageState.removeById(pendingEntry.pendingId());
                    ui.failPendingOutgoingChat(
                        target,
                        pendingEntry.pendingId(),
                        Instant.now(),
                        pendingEntry.fromNick(),
                        pendingEntry.text(),
                        String.valueOf(err));
                  }
                  ui.appendError(
                      targetCoordinator.safeStatusTarget(), "(send-error)", String.valueOf(err));
                }));

    if (useLocalEcho) {
      ui.appendChat(target, "(" + me + ")", m, true);
    }
  }

  private void sendNotice(
      CompositeDisposable disposables, TargetRef echoTarget, String target, String message) {
    if (echoTarget == null) return;
    String t = target == null ? "" : target.trim();
    String m = message == null ? "" : message.trim();
    if (t.isEmpty() || m.isEmpty()) return;

    if (!outboundConnectionStatusSupport.ensureConnected(echoTarget)) {
      return;
    }

    OutboundMultilineMessageSupport.MultilineSendPlan plan =
        outboundMultilineMessageSupport.plan(echoTarget, m, "(notice)");
    if (plan.shouldCancel()) {
      return;
    }
    if (plan.shouldSplitLines()) {
      for (String line : plan.lines()) {
        sendNotice(disposables, echoTarget, t, line);
      }
      return;
    }
    m = plan.payload();

    disposables.add(
        irc.sendNotice(echoTarget.serverId(), t, m)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(send-error)",
                        String.valueOf(err))));

    if (shouldUseLocalEcho(echoTarget.serverId())) {
      String me = irc.currentNick(echoTarget.serverId()).orElse("me");
      ui.appendNotice(echoTarget, "(" + me + ")", "NOTICE → " + t + ": " + m);
    }
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !echoCapabilityPort.isEchoMessageAvailable(serverId);
  }
}
