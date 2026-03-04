package cafe.woden.ircclient.app.api;

/** Port for persisting and reading last-known channel metadata snapshots. */
public interface ChannelMetadataPort {

  String topicFor(TargetRef target);

  void rememberTopic(TargetRef target, String topic, String topicSetBy, Long topicSetAtEpochMs);
}
