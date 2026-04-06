package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Port for persisting and reading last-known channel metadata snapshots. */
@SecondaryPort
@ApplicationLayer
public interface ChannelMetadataPort {

  String topicFor(TargetRef target);

  void rememberTopic(TargetRef target, String topic, String topicSetBy, Long topicSetAtEpochMs);

  default int topicPanelHeightPxFor(TargetRef target) {
    return 58;
  }

  default void rememberTopicPanelHeight(TargetRef target, int heightPx) {}
}
