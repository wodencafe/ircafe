package cafe.woden.ircclient.logging.viewer;

/**
 * Backend service used by the Swing log viewer.
 *
 * <p>Implementations are expected to be thread-safe and may be called off the EDT.
 */
public interface ChatLogViewerService {

  boolean enabled();

  ChatLogViewerResult search(ChatLogViewerQuery query);
}
