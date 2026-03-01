package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;

/** Emits outbound server-tree UI requests to downstream consumers. */
public interface ServerTreeRequestEmitter {

  void emitConnectServer(String serverId);

  void emitDisconnectServer(String serverId);

  void emitCloseTarget(TargetRef ref);

  void emitJoinChannel(TargetRef ref);

  void emitDisconnectChannel(TargetRef ref);

  void emitBouncerDetachChannel(TargetRef ref);

  void emitCloseChannel(TargetRef ref);

  void emitManagedChannelsChanged(String serverId);

  void emitClearLog(TargetRef target);

  void emitOpenPinnedChat(TargetRef ref);

  void emitIrcv3CapabilityToggle(Ircv3CapabilityToggleRequest request);
}
