package cafe.woden.ircclient.bouncer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registry of available bouncer backends and their metadata descriptors. */
@Component
@ApplicationLayer
public class BouncerBackendRegistry {

  private final List<BouncerBackendDescriptor> descriptors;
  private final Map<String, BouncerBackendDescriptor> byBackendId;

  public BouncerBackendRegistry(List<BouncerNetworkMappingStrategy> mappingStrategies) {
    Objects.requireNonNull(mappingStrategies, "mappingStrategies");

    ArrayList<BouncerBackendDescriptor> discovered = new ArrayList<>();
    for (BouncerNetworkMappingStrategy strategy : mappingStrategies) {
      if (strategy == null) continue;
      String backend = normalize(strategy.backendId());
      if (backend == null) continue;
      discovered.add(
          new BouncerBackendDescriptor(
              backend,
              strategy.ephemeralIdPrefix(),
              strategy.networksGroupLabel(),
              strategy.capabilityHints()));
    }

    discovered.sort(java.util.Comparator.comparing(BouncerBackendDescriptor::backendId));

    LinkedHashMap<String, BouncerBackendDescriptor> map = new LinkedHashMap<>();
    for (BouncerBackendDescriptor descriptor : discovered) {
      if (descriptor == null) continue;
      map.putIfAbsent(descriptor.backendId(), descriptor);
    }

    this.descriptors = List.copyOf(map.values());
    this.byBackendId = java.util.Collections.unmodifiableMap(map);
  }

  public List<BouncerBackendDescriptor> descriptors() {
    return descriptors;
  }

  public Set<String> backendIds() {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (BouncerBackendDescriptor descriptor : descriptors) {
      out.add(descriptor.backendId());
    }
    return java.util.Collections.unmodifiableSet(out);
  }

  public Optional<BouncerBackendDescriptor> find(String backendId) {
    return Optional.ofNullable(byBackendId.get(normalize(backendId)));
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v.toLowerCase(Locale.ROOT);
  }
}
