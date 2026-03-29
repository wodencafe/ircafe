package cafe.woden.ircclient.config;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Built-in backend descriptor registry keyed by enum and stable string id. */
public final class BackendDescriptorCatalog {

  private static final BackendDescriptorCatalog BUILT_INS =
      new BackendDescriptorCatalog(
          List.of(
              new BackendDescriptor(IrcProperties.Server.Backend.IRC, "irc", "IRC"),
              new BackendDescriptor(
                  IrcProperties.Server.Backend.QUASSEL_CORE, "quassel-core", "Quassel Core"),
              new BackendDescriptor(IrcProperties.Server.Backend.MATRIX, "matrix", "Matrix")));

  private final Map<IrcProperties.Server.Backend, BackendDescriptor> descriptorsByBackend;
  private final Map<String, BackendDescriptor> descriptorsById;

  private BackendDescriptorCatalog(List<BackendDescriptor> descriptors) {
    EnumMap<IrcProperties.Server.Backend, BackendDescriptor> byBackend =
        new EnumMap<>(IrcProperties.Server.Backend.class);
    LinkedHashMap<String, BackendDescriptor> byId = new LinkedHashMap<>();
    for (BackendDescriptor descriptor :
        Objects.requireNonNullElse(descriptors, List.<BackendDescriptor>of())) {
      if (descriptor == null) continue;
      BackendDescriptor previousBackend = byBackend.putIfAbsent(descriptor.backend(), descriptor);
      if (previousBackend != null) {
        throw new IllegalStateException(
            "Duplicate backend descriptor registered for " + descriptor.backend());
      }
      String id = normalizeId(descriptor.id());
      if (id.isEmpty()) {
        throw new IllegalStateException(
            "Backend descriptor id must not be blank for " + descriptor.backend());
      }
      BackendDescriptor previousId = byId.putIfAbsent(id, descriptor);
      if (previousId != null) {
        throw new IllegalStateException("Duplicate backend descriptor id registered: " + id);
      }
    }
    if (!byBackend.containsKey(IrcProperties.Server.Backend.IRC)) {
      throw new IllegalStateException("Missing IRC backend descriptor");
    }
    this.descriptorsByBackend = Map.copyOf(byBackend);
    this.descriptorsById = Map.copyOf(byId);
  }

  public static BackendDescriptorCatalog builtIns() {
    return BUILT_INS;
  }

  public BackendDescriptor descriptorFor(IrcProperties.Server.Backend backend) {
    IrcProperties.Server.Backend resolved =
        backend == null ? IrcProperties.Server.Backend.IRC : backend;
    return descriptorsByBackend.getOrDefault(
        resolved, descriptorsByBackend.get(IrcProperties.Server.Backend.IRC));
  }

  public Optional<BackendDescriptor> descriptorForId(String backendId) {
    String id = normalizeId(backendId);
    if (id.isEmpty()) return Optional.empty();
    return Optional.ofNullable(descriptorsById.get(id));
  }

  public String normalizeIdOrDefault(String backendId) {
    String id = normalizeId(backendId);
    if (id.isEmpty()) {
      return idFor(IrcProperties.Server.Backend.IRC);
    }
    return descriptorForId(id).map(BackendDescriptor::id).orElse(id);
  }

  public String idFor(IrcProperties.Server.Backend backend) {
    return descriptorFor(backend).id();
  }

  public String displayNameFor(IrcProperties.Server.Backend backend) {
    return descriptorFor(backend).displayName();
  }

  public Optional<IrcProperties.Server.Backend> backendForId(String backendId) {
    return descriptorForId(backendId).map(BackendDescriptor::backend);
  }

  private static String normalizeId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }
}
