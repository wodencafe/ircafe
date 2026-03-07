package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;

/** Read-marker capability/readiness + send API used by UI coordinators. */
public interface IrcReadMarkerPort {

  default boolean isReadMarkerAvailable(String serverId) {
    return false;
  }

  default Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.error(
        new UnsupportedOperationException("read-marker capability not supported"));
  }

  static IrcReadMarkerPort from(IrcClientService irc) {
    if (irc instanceof IrcReadMarkerPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcReadMarkerPort() {};
    }
    return new IrcReadMarkerPort() {
      @Override
      public boolean isReadMarkerAvailable(String serverId) {
        return irc.isReadMarkerAvailable(serverId);
      }

      @Override
      public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
        return irc.sendReadMarker(serverId, target, markerAt);
      }
    };
  }
}
