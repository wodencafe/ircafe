package cafe.woden.ircclient.logging.viewer;

import java.util.List;

/**
 * Backend service used by the Swing log viewer.
 *
 * <p>Implementations are expected to be thread-safe and may be called off the EDT.
 */
public interface ChatLogViewerService {

  boolean enabled();

  ChatLogViewerResult search(ChatLogViewerQuery query);

  /**
   * Returns unique known channel names for a server (best effort), for channel-filter picker UX.
   */
  List<String> listUniqueChannels(String serverId, int limit);
}
