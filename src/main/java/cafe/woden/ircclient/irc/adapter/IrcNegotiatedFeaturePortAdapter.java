package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing negotiated-feature checks via a narrow capability port. */
@Component("ircNegotiatedFeaturePort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcNegotiatedFeaturePortAdapter implements IrcNegotiatedFeaturePort {

  private final IrcClientService irc;

  public IrcNegotiatedFeaturePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isChatHistoryAvailable(serverId)
        : irc.isChatHistoryAvailable(serverId);
  }

  @Override
  public boolean isMessageTagsAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isMessageTagsAvailable(serverId)
        : irc.isMessageTagsAvailable(serverId);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isDraftReplyAvailable(serverId)
        : irc.isDraftReplyAvailable(serverId);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isDraftReactAvailable(serverId)
        : irc.isDraftReactAvailable(serverId);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isDraftUnreactAvailable(serverId)
        : irc.isDraftUnreactAvailable(serverId);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isMultilineAvailable(serverId)
        : irc.isMultilineAvailable(serverId);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.negotiatedMultilineMaxBytes(serverId)
        : irc.negotiatedMultilineMaxBytes(serverId);
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.negotiatedMultilineMaxLines(serverId)
        : irc.negotiatedMultilineMaxLines(serverId);
  }

  @Override
  public boolean isExperimentalMessageEditAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isExperimentalMessageEditAvailable(serverId)
        : irc.isExperimentalMessageEditAvailable(serverId);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isMessageRedactionAvailable(serverId)
        : irc.isMessageRedactionAvailable(serverId);
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isReadMarkerAvailable(serverId)
        : irc.isReadMarkerAvailable(serverId);
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isLabeledResponseAvailable(serverId)
        : irc.isLabeledResponseAvailable(serverId);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    return irc == null
        ? IrcNegotiatedFeaturePort.super.isMonitorAvailable(serverId)
        : irc.isMonitorAvailable(serverId);
  }
}
