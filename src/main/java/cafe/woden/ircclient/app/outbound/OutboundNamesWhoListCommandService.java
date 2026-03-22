package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /names, /who, and /list command flow. */
@Component
@ApplicationLayer
final class OutboundNamesWhoListCommandService {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final OutboundMembershipQuerySupport membershipQuerySupport;

  @NonNull private final CommandTargetPolicy commandTargetPolicy;

  OutboundNamesWhoListCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      OutboundRawCommandSupport rawCommandSupport) {

    this.commandTargetPolicy = commandTargetPolicy;
    this.membershipQuerySupport =
        new OutboundMembershipQuerySupport(
            targetMembership,
            ui,
            connectionCoordinator,
            targetCoordinator,
            commandTargetPolicy,
            rawCommandSupport);
  }

  void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = membershipQuerySupport.requireActiveTarget("(names)");
    if (at == null) {
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    if (ch == null) {
      membershipQuerySupport.appendStatus(at, "(names)", "Usage: /names [#channel]");
      membershipQuerySupport.appendStatus(
          at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!membershipQuerySupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!membershipQuerySupport.validateSingleLine(at.serverId(), "(names)", "/names", ch)) {
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    membershipQuerySupport.requestNames(disposables, at.serverId(), out, ch);
  }

  void handleWho(CompositeDisposable disposables, String args) {
    TargetRef at = membershipQuerySupport.requireActiveTarget("(who)");
    if (at == null) {
      return;
    }

    String a = args == null ? "" : args.trim();
    if (a.isEmpty()) {
      if (at.isChannel()) {
        a = at.target();
      } else {
        membershipQuerySupport.appendStatus(at, "(who)", "Usage: /who [mask|#channel] [flags]");
        membershipQuerySupport.appendStatus(
            at, "(who)", "Tip: from a channel tab, /who defaults to that channel.");
        return;
      }
    }

    if (!membershipQuerySupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!membershipQuerySupport.validateSingleLine(at.serverId(), "(who)", "/who", a)) {
      return;
    }

    String line = "WHO " + a;
    TargetRef out = membershipQuerySupport.resolveWhoOutputTarget(at.serverId(), a);
    membershipQuerySupport.sendRaw(disposables, at.serverId(), out, "(who)", line, "(who-error)");
  }

  void handleList(CompositeDisposable disposables, String args) {
    TargetRef at = membershipQuerySupport.requireActiveTarget("(list)");
    if (at == null) {
      return;
    }

    String a = args == null ? "" : args.trim();
    if (!membershipQuerySupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!membershipQuerySupport.validateSingleLine(at.serverId(), "(list)", "/list", a)) {
      return;
    }

    TargetRef channelList =
        at.hasNetworkQualifier()
            ? TargetRef.channelList(at.serverId(), at.networkQualifierToken())
            : TargetRef.channelList(at.serverId());
    membershipQuerySupport.ensureTargetExists(channelList);
    membershipQuerySupport.beginChannelList(
        at.serverId(),
        a.isEmpty() ? "Loading channel list..." : ("Loading channel list (" + a + ")..."));
    membershipQuerySupport.selectTarget(channelList);

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    membershipQuerySupport.sendRaw(
        disposables,
        at.serverId(),
        new TargetRef(at.serverId(), "status"),
        "(list)",
        line,
        "(list-error)");
  }
}
