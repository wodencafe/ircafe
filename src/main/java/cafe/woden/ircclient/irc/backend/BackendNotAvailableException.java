package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Raised when a configured backend exists in config but is not available for operation yet. */
public final class BackendNotAvailableException extends UnsupportedOperationException {
  private final String backendId;
  private final String operation;
  private final String serverId;

  public BackendNotAvailableException(
      IrcProperties.Server.Backend backend, String operation, String serverId, String detail) {
    this(BackendDescriptorCatalog.builtIns().idFor(backend), operation, serverId, detail);
  }

  public BackendNotAvailableException(
      String backendId, String operation, String serverId, String detail) {
    super(buildMessage(backendId, operation, serverId, detail));
    this.backendId = BackendDescriptorCatalog.builtIns().normalizeIdOrDefault(backendId);
    this.operation = Objects.toString(operation, "").trim();
    this.serverId = Objects.toString(serverId, "").trim();
  }

  public String backendId() {
    return backendId;
  }

  @Deprecated(forRemoval = false)
  public IrcProperties.Server.Backend backend() {
    return BackendDescriptorCatalog.builtIns().backendForId(backendId).orElse(null);
  }

  public String operation() {
    return operation;
  }

  public String serverId() {
    return serverId;
  }

  private static String buildMessage(
      String backendId, String operation, String serverId, String detail) {
    String backendLabel =
        BackendDescriptorCatalog.builtIns()
            .descriptorForId(backendId)
            .map(descriptor -> descriptor.displayName() + " backend")
            .orElseGet(
                () -> {
                  String normalized =
                      BackendDescriptorCatalog.builtIns().normalizeIdOrDefault(backendId);
                  return normalized.isBlank() ? "backend" : normalized + " backend";
                });
    String op = Objects.toString(operation, "").trim();
    if (!op.isEmpty()) op = " (" + op + ")";
    String sid = Objects.toString(serverId, "").trim();
    String sidPart = sid.isEmpty() ? "" : (" for server '" + sid + "'");
    String d = Objects.toString(detail, "").trim();
    if (d.isEmpty()) d = "not available";
    return backendLabel + " is " + d + op + sidPart;
  }
}
