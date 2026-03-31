package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
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
public final class OutboundNamesWhoListCommandService {

  private final OutboundTargetMembershipCommandSupport targetMembershipCommandSupport;

  @NonNull private final CommandTargetPolicy commandTargetPolicy;

  public OutboundNamesWhoListCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      OutboundRawCommandSupport rawCommandSupport) {

    this.commandTargetPolicy = commandTargetPolicy;
    this.targetMembershipCommandSupport =
        new OutboundTargetMembershipCommandSupport(
            targetMembership,
            ui,
            connectionCoordinator,
            targetCoordinator,
            commandTargetPolicy,
            rawCommandSupport);
  }

  public void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(names)");
    if (at == null) {
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    if (ch == null) {
      targetMembershipCommandSupport.appendStatus(at, "(names)", "Usage: /names [#channel]");
      targetMembershipCommandSupport.appendStatus(
          at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!targetMembershipCommandSupport.validateSingleLine(
        at.serverId(), "(names)", "/names", ch)) {
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    targetMembershipCommandSupport.requestNames(disposables, at.serverId(), out, ch);
  }

  public void handleWho(CompositeDisposable disposables, String args) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(who)");
    if (at == null) {
      return;
    }

    String a = args == null ? "" : args.trim();
    if (a.isEmpty()) {
      if (at.isChannel()) {
        a = at.target();
      } else {
        targetMembershipCommandSupport.appendStatus(
            at, "(who)", "Usage: /who [mask|#channel] [flags]");
        targetMembershipCommandSupport.appendStatus(
            at, "(who)", "Tip: from a channel tab, /who defaults to that channel.");
        return;
      }
    }

    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!targetMembershipCommandSupport.validateSingleLine(at.serverId(), "(who)", "/who", a)) {
      return;
    }

    String line = "WHO " + a;
    TargetRef out = targetMembershipCommandSupport.resolveWhoOutputTarget(at.serverId(), a);
    targetMembershipCommandSupport.sendRaw(
        disposables, at.serverId(), out, "(who)", line, "(who-error)");
  }

  public void handleList(CompositeDisposable disposables, String args) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(list)");
    if (at == null) {
      return;
    }

    String a = args == null ? "" : args.trim();
    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!targetMembershipCommandSupport.validateSingleLine(at.serverId(), "(list)", "/list", a)) {
      return;
    }

    TargetRef channelList =
        at.hasNetworkQualifier()
            ? TargetRef.channelList(at.serverId(), at.networkQualifierToken())
            : TargetRef.channelList(at.serverId());
    targetMembershipCommandSupport.ensureTargetExists(channelList);
    targetMembershipCommandSupport.beginChannelList(
        at.serverId(),
        a.isEmpty() ? "Loading channel list..." : ("Loading channel list (" + a + ")..."));
    targetMembershipCommandSupport.selectTarget(channelList);

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    targetMembershipCommandSupport.sendRaw(
        disposables,
        at.serverId(),
        new TargetRef(at.serverId(), "status"),
        "(list)",
        line,
        "(list-error)");
  }
}
