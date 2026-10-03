package cafe.woden.ircclient.irc.quassel;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/** Session-owned decorator preventing interleaved frames, including multi-frame operations. */
final class QuasselCoreSerializedSignalProxySender implements QuasselCoreSignalProxySender {
  private final QuasselCoreSignalProxySender delegate;
  private final Object writeLock = new Object();

  QuasselCoreSerializedSignalProxySender(QuasselCoreSignalProxySender delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void send(Socket socket, Write operation) throws IOException {
    synchronized (writeLock) {
      delegate.send(socket, operation);
    }
  }
}
