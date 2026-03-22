package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.OutboundRawCommandSupport;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /topic and /kick command flow. */
@Component
@ApplicationLayer
public final class OutboundTopicKickCommandService {

  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundTargetMembershipCommandSupport targetMembershipCommandSupport;

  public OutboundTopicKickCommandService(
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

  public void handleTopic(CompositeDisposable disposables, String first, String rest) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(topic)");
    if (at == null) {
      return;
    }

    String f = first == null ? "" : first.trim();
    String r = rest == null ? "" : rest.trim();

    String channel;
    String topicText = "";
    boolean settingTopic;

    if (commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), f)) {
      channel = f;
      topicText = r;
      settingTopic = !topicText.isEmpty();
    } else if (at.isChannel()) {
      channel = at.target();
      topicText = (f + (r.isEmpty() ? "" : " " + r)).trim();
      settingTopic = !topicText.isEmpty();
    } else {
      targetMembershipCommandSupport.appendStatus(
          at, "(topic)", "Usage: /topic [#channel] [new topic...]");
      targetMembershipCommandSupport.appendStatus(
          at, "(topic)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!targetMembershipCommandSupport.validateSingleLine(
        at.serverId(), "(topic)", "/topic", channel, topicText)) {
      return;
    }

    String line = settingTopic ? ("TOPIC " + channel + " :" + topicText) : ("TOPIC " + channel);
    TargetRef out = new TargetRef(at.serverId(), channel);
    targetMembershipCommandSupport.sendRaw(
        disposables, at.serverId(), out, "(topic)", line, "(topic-error)");
  }

  public void handleKick(
      CompositeDisposable disposables, String channel, String nick, String reason) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(kick)");
    if (at == null) {
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    String n = nick == null ? "" : nick.trim();
    String rsn = reason == null ? "" : reason.trim();

    if (ch == null || n.isEmpty()) {
      targetMembershipCommandSupport.appendStatus(
          at, "(kick)", "Usage: /kick [#channel] <nick> [reason]");
      targetMembershipCommandSupport.appendStatus(
          at, "(kick)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    if (!targetMembershipCommandSupport.validateSingleLine(
        at.serverId(), "(kick)", "/kick", ch, n, rsn)) {
      return;
    }

    String line = "KICK " + ch + " " + n + (rsn.isEmpty() ? "" : " :" + rsn);
    TargetRef out = new TargetRef(at.serverId(), ch);
    targetMembershipCommandSupport.sendRaw(
        disposables, at.serverId(), out, "(kick)", line, "(kick-error)");
  }
}
