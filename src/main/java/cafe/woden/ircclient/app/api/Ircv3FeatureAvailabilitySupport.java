package cafe.woden.ircclient.app.api;

/** Shared availability contract for optional IRCv3 features exposed above core transport. */
public interface Ircv3FeatureAvailabilitySupport {

  String featureId();

  boolean isAvailable(String serverId);

  default String requirementHint() {
    return "";
  }
}
