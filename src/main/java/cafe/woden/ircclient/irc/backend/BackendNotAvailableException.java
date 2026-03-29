package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;
import java.util.function.Function;

/** Raised when a configured backend exists in config but is not available for operation yet. */
public final class BackendNotAvailableException extends UnsupportedOperationException {
  private final String backendId;
  private final String operation;
  private final String serverId;
  private final String detail;

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
    this.detail = normalizeDetail(detail);
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

  public String detail() {
    return detail;
  }

  public String displayMessage(Function<String, String> backendDisplayNameResolver) {
    return buildMessage(backendId, operation, serverId, detail, backendDisplayNameResolver);
  }

  private static String buildMessage(
      String backendId, String operation, String serverId, String detail) {
    return buildMessage(
        backendId, operation, serverId, detail, BackendNotAvailableException::builtInDisplayName);
  }

  private static String buildMessage(
      String backendId,
      String operation,
      String serverId,
      String detail,
      Function<String, String> backendDisplayNameResolver) {
    String normalizedBackendId =
        BackendDescriptorCatalog.builtIns().normalizeIdOrDefault(backendId);
    String backendLabel = renderBackendLabel(normalizedBackendId, backendDisplayNameResolver);
    String op = Objects.toString(operation, "").trim();
    if (!op.isEmpty()) op = " (" + op + ")";
    String sid = Objects.toString(serverId, "").trim();
    String sidPart = sid.isEmpty() ? "" : (" for server '" + sid + "'");
    String d = normalizeDetail(detail);
    return backendLabel + " is " + d + op + sidPart;
  }

  private static String renderBackendLabel(
      String normalizedBackendId, Function<String, String> backendDisplayNameResolver) {
    String displayName =
        Objects.toString(
                backendDisplayNameResolver == null
                    ? ""
                    : backendDisplayNameResolver.apply(normalizedBackendId),
                "")
            .trim();
    if (displayName.isEmpty()) {
      return normalizedBackendId.isBlank() ? "backend" : normalizedBackendId + " backend";
    }
    if (displayName.toLowerCase().endsWith("backend")) {
      return displayName;
    }
    return displayName + " backend";
  }

  private static String builtInDisplayName(String backendId) {
    return BackendDescriptorCatalog.builtIns()
        .descriptorForId(backendId)
        .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
        .orElse("");
  }

  private static String normalizeDetail(String detail) {
    String normalized = Objects.toString(detail, "").trim();
    return normalized.isEmpty() ? "not available" : normalized;
  }
}
