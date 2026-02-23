package cafe.woden.ircclient.logging.viewer;

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
}
