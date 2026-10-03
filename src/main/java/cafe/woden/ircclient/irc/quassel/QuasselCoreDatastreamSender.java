package cafe.woden.ircclient.irc.quassel;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/** Executes a SignalProxy operation on the selected connection without retrying it. */
final class QuasselCoreDatastreamSender implements QuasselCoreSignalProxySender {
  private final QuasselCoreDatastreamCodec codec;

  QuasselCoreDatastreamSender(QuasselCoreDatastreamCodec codec) {
    this.codec = Objects.requireNonNull(codec, "codec");
  }

  @Override
  public void send(Socket socket, Write operation) throws IOException {
    Objects.requireNonNull(operation, "operation").write(codec, socket.getOutputStream());
  }
}
