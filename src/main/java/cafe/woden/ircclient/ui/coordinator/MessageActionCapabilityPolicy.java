package cafe.woden.ircclient.ui.coordinator;

/** Backend capability policy for transcript message actions and history affordances. */
public interface MessageActionCapabilityPolicy {

  boolean canReply(String serverId);

  boolean canReact(String serverId);

  boolean canUnreact(String serverId);

  boolean canEdit(String serverId);

  boolean canRedact(String serverId);

  boolean canLoadAroundMessage(String serverId);

  boolean canLoadNewerHistory(String serverId);
}
