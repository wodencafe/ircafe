package cafe.woden.ircclient.irc.quassel;

import java.io.EOFException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads native frames until cancellation or failure; the service owns session lifecycle events. */
final class QuasselCoreReadLoop {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreReadLoop.class);
  private final QuasselCoreDatastreamCodec codec;

  QuasselCoreReadLoop(QuasselCoreDatastreamCodec codec) {
    this.codec = Objects.requireNonNull(codec, "codec");
  }

  record Failure(String reason, Exception cause) {}

  @FunctionalInterface
  interface MessageHandler {
    void handle(QuasselCoreDatastreamCodec.SignalProxyMessage message) throws Exception;
  }

  void read(
      String serverId,
      Socket socket,
      BooleanSupplier stopped,
      MessageHandler messages,
      Consumer<Failure> failures) {
    try (InputStream in = socket.getInputStream()) {
      while (!stopped.getAsBoolean()) {
        QuasselCoreDatastreamCodec.SignalProxyMessage message;
        try {
          message = codec.readSignalProxyMessage(in);
        } catch (SocketTimeoutException timeout) {
          continue;
        } catch (EOFException eof) {
          log.debug("Quassel read loop EOF: serverId={}", serverId);
          if (stopped.getAsBoolean()) return;
          failures.accept(new Failure("Quassel Core connection closed", null));
          return;
        }
        messages.handle(message);
      }
    } catch (Exception error) {
      if (!stopped.getAsBoolean()) {
        log.warn("Quassel read loop error: serverId={}", serverId, error);
        String detail = Objects.toString(error.getMessage(), "").trim();
        if (detail.isEmpty()) detail = error.getClass().getSimpleName();
        String reason = detail.isEmpty() ? "Connection error" : "Connection error: " + detail;
        failures.accept(new Failure(reason, error));
      }
    }
  }
}
