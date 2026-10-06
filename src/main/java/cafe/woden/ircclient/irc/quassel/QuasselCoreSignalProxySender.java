package cafe.woden.ircclient.irc.quassel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/** Sends one logical operation, which may contain several related SignalProxy frames. */
@FunctionalInterface
interface QuasselCoreSignalProxySender {
  void send(Socket socket, Write operation) throws IOException;

  @FunctionalInterface
  interface Write {
    void write(QuasselCoreDatastreamCodec codec, OutputStream output) throws IOException;
  }
}
