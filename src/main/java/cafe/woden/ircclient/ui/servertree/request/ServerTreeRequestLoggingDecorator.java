package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lightweight request-emission diagnostics that keeps behavior unchanged. */
public final class ServerTreeRequestLoggingDecorator extends ServerTreeRequestEmitterDecorator {

  private static final Logger log =
      LoggerFactory.getLogger(ServerTreeRequestLoggingDecorator.class);

  public ServerTreeRequestLoggingDecorator(ServerTreeRequestEmitter delegate) {
    super(delegate);
  }

  @Override
  public void emitConnectServer(String serverId) {
    logRequest("connectServer", serverId);
    super.emitConnectServer(serverId);
  }

  @Override
  public void emitDisconnectServer(String serverId) {
    logRequest("disconnectServer", serverId);
    super.emitDisconnectServer(serverId);
  }

  @Override
  public void emitCloseTarget(TargetRef ref) {
    logRequest("closeTarget", ref);
    super.emitCloseTarget(ref);
  }

  @Override
  public void emitJoinChannel(TargetRef ref) {
    logRequest("joinChannel", ref);
    super.emitJoinChannel(ref);
  }

  @Override
  public void emitDisconnectChannel(TargetRef ref) {
    logRequest("disconnectChannel", ref);
    super.emitDisconnectChannel(ref);
  }

  @Override
  public void emitBouncerDetachChannel(TargetRef ref) {
    logRequest("bouncerDetachChannel", ref);
    super.emitBouncerDetachChannel(ref);
  }

  @Override
  public void emitCloseChannel(TargetRef ref) {
    logRequest("closeChannel", ref);
    super.emitCloseChannel(ref);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    logRequest("managedChannelsChanged", serverId);
    super.emitManagedChannelsChanged(serverId);
  }

  @Override
  public void emitClearLog(TargetRef target) {
    logRequest("clearLog", target);
    super.emitClearLog(target);
  }

  @Override
  public void emitOpenPinnedChat(TargetRef ref) {
    logRequest("openPinnedChat", ref);
    super.emitOpenPinnedChat(ref);
  }

  @Override
  public void emitIrcv3CapabilityToggle(Ircv3CapabilityToggleRequest request) {
    logRequest("ircv3CapabilityToggle", request);
    super.emitIrcv3CapabilityToggle(request);
  }

  private static void logRequest(String kind, Object payload) {
    if (!log.isTraceEnabled()) return;
    log.trace("[ircafe] server tree request kind={} payload={}", kind, payload);
  }
}
