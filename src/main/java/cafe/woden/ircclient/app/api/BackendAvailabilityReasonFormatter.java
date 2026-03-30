package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Formats backend-readiness reasons with plugin-aware backend labels when available. */
public final class BackendAvailabilityReasonFormatter {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final AvailableBackendIdsPort BUILT_INS_BACKEND_METADATA =
      new AvailableBackendIdsPort() {
        @Override
        public List<String> availableBackendIds() {
          return List.of();
        }

        @Override
        public String backendDisplayName(String backendId) {
          String normalized = canonicalBackendId(backendId);
          if (normalized.isEmpty()) return "";
          return BACKEND_DESCRIPTORS
              .descriptorForId(normalized)
              .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
              .orElse(normalized);
        }
      };

  private BackendAvailabilityReasonFormatter() {}

  public static String decorate(String backendId, String reason) {
    return decorate(backendId, reason, builtInsBackendMetadata());
  }

  public static String decorate(
      String backendId, String reason, AvailableBackendIdsPort backendMetadata) {
    String text = Objects.toString(reason, "").trim();
    if (text.isEmpty()) return "";
    String normalizedBackendId = canonicalBackendId(backendId);
    String backendLabel = backendDisplayLabel(normalizedBackendId, backendMetadata);
    if (backendLabel.isEmpty() || mentionsBackend(text, backendLabel, normalizedBackendId)) {
      return text;
    }
    return backendLabel + ": " + text;
  }

  public static AvailableBackendIdsPort builtInsBackendMetadata() {
    return BUILT_INS_BACKEND_METADATA;
  }

  private static String canonicalBackendId(String backendId) {
    String normalized = Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) return "";
    return BACKEND_DESCRIPTORS
        .descriptorForId(normalized)
        .map(descriptor -> Objects.toString(descriptor.id(), "").trim())
        .orElse(normalized);
  }

  private static String backendDisplayLabel(
      String backendId, AvailableBackendIdsPort backendMetadata) {
    if (backendId.isEmpty()) return "";
    AvailableBackendIdsPort metadata =
        Objects.requireNonNullElseGet(
            backendMetadata, BackendAvailabilityReasonFormatter::builtInsBackendMetadata);
    String displayName = Objects.toString(metadata.backendDisplayName(backendId), "").trim();
    String label = displayName.isEmpty() ? backendId : displayName;
    if (label.isEmpty()) return "";
    return label.toLowerCase(Locale.ROOT).endsWith("backend") ? label : label + " backend";
  }

  private static boolean mentionsBackend(String text, String backendLabel, String backendId) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains(" backend")) {
      return true;
    }
    if (!Objects.toString(backendLabel, "").isBlank()
        && lower.contains(backendLabel.toLowerCase(Locale.ROOT))) {
      return true;
    }
    return !Objects.toString(backendId, "").isBlank()
        && lower.contains(backendId.toLowerCase(Locale.ROOT));
  }
}
