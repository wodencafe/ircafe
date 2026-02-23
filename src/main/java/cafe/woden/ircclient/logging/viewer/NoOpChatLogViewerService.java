package cafe.woden.ircclient.logging.viewer;

import java.util.List;

/** No-op fallback used when chat logging is disabled. */
public final class NoOpChatLogViewerService implements ChatLogViewerService {

  @Override
  public boolean enabled() {
    return false;
  }

  @Override
  public ChatLogViewerResult search(ChatLogViewerQuery query) {
    return new ChatLogViewerResult(java.util.List.of(), 0, false, false);
  }

  @Override
  public List<String> listUniqueChannels(String serverId, int limit) {
    return List.of();
  }
}
