package cafe.woden.ircclient.irc;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing negotiated-feature checks via a narrow capability port. */
@Component("ircNegotiatedFeaturePort")
public class IrcNegotiatedFeaturePortAdapter implements IrcNegotiatedFeaturePort {

  private final IrcNegotiatedFeaturePort delegate;

  public IrcNegotiatedFeaturePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcNegotiatedFeaturePort.from(irc);
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    return delegate.isChatHistoryAvailable(serverId);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    return delegate.isDraftReplyAvailable(serverId);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    return delegate.isDraftReactAvailable(serverId);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    return delegate.isDraftUnreactAvailable(serverId);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    return delegate.isMultilineAvailable(serverId);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    return delegate.negotiatedMultilineMaxBytes(serverId);
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    return delegate.negotiatedMultilineMaxLines(serverId);
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    return delegate.isMessageEditAvailable(serverId);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    return delegate.isMessageRedactionAvailable(serverId);
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return delegate.isReadMarkerAvailable(serverId);
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    return delegate.isLabeledResponseAvailable(serverId);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    return delegate.isMonitorAvailable(serverId);
  }
}
