package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Raised when a configured backend exists in config but is not available for operation yet. */
public final class BackendNotAvailableException extends UnsupportedOperationException {
  private final IrcProperties.Server.Backend backend;
  private final String operation;
  private final String serverId;

  public BackendNotAvailableException(
      IrcProperties.Server.Backend backend, String operation, String serverId, String detail) {
    super(buildMessage(backend, operation, serverId, detail));
    this.backend = Objects.requireNonNull(backend, "backend");
    this.operation = Objects.toString(operation, "").trim();
    this.serverId = Objects.toString(serverId, "").trim();
  }

  public IrcProperties.Server.Backend backend() {
    return backend;
  }

  public String operation() {
    return operation;
  }

  public String serverId() {
    return serverId;
  }

  private static String buildMessage(
      IrcProperties.Server.Backend backend, String operation, String serverId, String detail) {
    String backendLabel =
        backend == null
            ? "backend"
            : switch (backend) {
              case IRC -> "IRC backend";
              case QUASSEL_CORE -> "Quassel Core backend";
              case MATRIX -> "Matrix backend";
            };
    String op = Objects.toString(operation, "").trim();
    if (!op.isEmpty()) op = " (" + op + ")";
    String sid = Objects.toString(serverId, "").trim();
    String sidPart = sid.isEmpty() ? "" : (" for server '" + sid + "'");
    String d = Objects.toString(detail, "").trim();
    if (d.isEmpty()) d = "not available";
    return backendLabel + " is " + d + op + sidPart;
  }
}
