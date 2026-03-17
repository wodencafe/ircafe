package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.IrcClientService;

/** Shutdown hook operations used during application teardown. */
public interface IrcShutdownPort {

  default void shutdownNow() {}

  static IrcShutdownPort from(IrcClientService irc) {
    if (irc instanceof IrcShutdownPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcShutdownPort() {};
    }
    return new IrcShutdownPort() {
      @Override
      public void shutdownNow() {
        irc.shutdownNow();
      }
    };
  }
}
