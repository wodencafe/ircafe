package cafe.woden.ircclient.irc.ircv3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Canonical metadata registry for IRCv3 capabilities and related tag features. */
public final class Ircv3ExtensionRegistry {

  public enum ExtensionKind {
    CAPABILITY,
    TAG_FEATURE,
    EXPERIMENTAL
  }

  public enum SpecStatus {
    STABLE,
    DRAFT,
    EXPERIMENTAL
  }

  public enum UiGroup {
    CORE("Core metadata and sync"),
    CONVERSATION("Conversation features"),
    HISTORY("History and playback"),
    OTHER("Other capabilities");

    private final String title;

    UiGroup(String title) {
      this.title = title;
    }

    public String title() {
      return title;
    }
  }

  public record UiMetadata(String label, UiGroup group, int sortOrder, String impactSummary) {
    public UiMetadata {
      label = normalizeLabel(label);
      group = Objects.requireNonNullElse(group, UiGroup.OTHER);
      impactSummary = Objects.toString(impactSummary, "").trim();
    }
  }

  public record ExtensionDefinition(
      String id,
      ExtensionKind kind,
      SpecStatus specStatus,
      List<String> aliases,
      String requestToken,
      String preferenceKey,
      UiMetadata uiMetadata) {
    public ExtensionDefinition {
      id = normalize(id);
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(specStatus, "specStatus");
      aliases = copyNormalized(aliases);
      requestToken = normalize(requestToken);
      preferenceKey = normalize(preferenceKey.isBlank() ? id : preferenceKey);
      Objects.requireNonNull(uiMetadata, "uiMetadata");
    }

    public boolean requestable() {
      return kind == ExtensionKind.CAPABILITY && !requestToken.isEmpty();
    }

    public List<String> allNames() {
      LinkedHashSet<String> names = new LinkedHashSet<>();
      if (!id.isEmpty()) {
        names.add(id);
      }
      if (!preferenceKey.isEmpty()) {
        names.add(preferenceKey);
      }
      if (!requestToken.isEmpty()) {
        names.add(requestToken);
      }
      names.addAll(aliases);
      return List.copyOf(names);
    }
  }

  public record FeatureDefinition(
      int sortOrder, String label, List<String> requiredAll, List<String> requiredAny) {
    public FeatureDefinition {
      label = normalizeLabel(label);
      requiredAll = copyNormalized(requiredAll);
      requiredAny = copyNormalized(requiredAny);
    }
  }

  private static final List<Ircv3ExtensionDefinitionProvider> BUILT_IN_PROVIDERS =
      List.of(
          new Ircv3CoreTransportExtensionProvider(),
          new Ircv3CoreHistoryExtensionProvider(),
          new Ircv3CoreMiscExtensionProvider());

  private static final Snapshot DEFAULT_SNAPSHOT = new Snapshot(loadProviders());

  private Ircv3ExtensionRegistry() {}

  public static final class Snapshot {

    private final List<Ircv3ExtensionDefinitionProvider> providers;
    private final List<String> providerIds;
    private final List<ExtensionDefinition> extensions;
    private final List<ExtensionDefinition> requestableCapabilities;
    private final List<String> requestableCapabilityTokens;
    private final List<FeatureDefinition> visibleFeatures;
    private final Map<String, ExtensionDefinition> byName;

    private Snapshot(List<Ircv3ExtensionDefinitionProvider> providers) {
      this.providers = List.copyOf(Objects.requireNonNullElse(providers, List.of()));
      this.providerIds =
          this.providers.stream().map(Ircv3ExtensionDefinitionProvider::providerId).toList();
      this.extensions = collectExtensions(this.providers);
      this.requestableCapabilities =
          this.extensions.stream().filter(ExtensionDefinition::requestable).toList();
      this.requestableCapabilityTokens =
          this.requestableCapabilities.stream().map(ExtensionDefinition::requestToken).toList();
      this.visibleFeatures = collectVisibleFeatures(this.providers);
      this.byName = indexDefinitions(this.extensions);
    }

    public List<ExtensionDefinition> all() {
      return extensions;
    }

    public List<String> providerIds() {
      return providerIds;
    }

    public Optional<ExtensionDefinition> find(String name) {
      return Optional.ofNullable(byName.get(normalize(name)));
    }

    public List<ExtensionDefinition> requestableCapabilities() {
      return requestableCapabilities;
    }

    public List<String> requestableCapabilityTokens() {
      return requestableCapabilityTokens;
    }

    public List<FeatureDefinition> visibleFeatures() {
      return visibleFeatures;
    }

    public String requestTokenFor(String name) {
      return find(name)
          .filter(ExtensionDefinition::requestable)
          .map(ExtensionDefinition::requestToken)
          .orElse("");
    }

    public String preferenceKeyFor(String name) {
      return find(name).map(ExtensionDefinition::preferenceKey).orElse(normalize(name));
    }
  }

  public static Snapshot snapshot() {
    return DEFAULT_SNAPSHOT;
  }

  public static List<ExtensionDefinition> all() {
    return DEFAULT_SNAPSHOT.all();
  }

  public static List<String> providerIds() {
    return DEFAULT_SNAPSHOT.providerIds();
  }

  public static Optional<ExtensionDefinition> find(String name) {
    return DEFAULT_SNAPSHOT.find(name);
  }

  public static List<ExtensionDefinition> requestableCapabilities() {
    return DEFAULT_SNAPSHOT.requestableCapabilities();
  }

  public static List<String> requestableCapabilityTokens() {
    return DEFAULT_SNAPSHOT.requestableCapabilityTokens();
  }

  public static List<FeatureDefinition> visibleFeatures() {
    return DEFAULT_SNAPSHOT.visibleFeatures();
  }

  public static String requestTokenFor(String name) {
    return DEFAULT_SNAPSHOT.requestTokenFor(name);
  }

  public static String preferenceKeyFor(String name) {
    return DEFAULT_SNAPSHOT.preferenceKeyFor(name);
  }

  static List<Ircv3ExtensionDefinitionProvider> builtInProviders() {
    return BUILT_IN_PROVIDERS;
  }

  static Snapshot snapshotForProviders(List<Ircv3ExtensionDefinitionProvider> providers) {
    return new Snapshot(normalizeProviders(providers));
  }

  private static List<Ircv3ExtensionDefinitionProvider> loadProviders() {
    ArrayList<Ircv3ExtensionDefinitionProvider> providers = new ArrayList<>(BUILT_IN_PROVIDERS);
    try {
      ServiceLoader<Ircv3ExtensionDefinitionProvider> loader =
          ServiceLoader.load(
              Ircv3ExtensionDefinitionProvider.class,
              Ircv3ExtensionDefinitionProvider.class.getClassLoader());
      for (Ircv3ExtensionDefinitionProvider provider : loader) {
        if (provider != null) {
          providers.add(provider);
        }
      }
    } catch (ServiceConfigurationError error) {
      throw new IllegalStateException("Failed to load IRCv3 extension definition providers", error);
    }
    return normalizeProviders(providers);
  }

  private static List<Ircv3ExtensionDefinitionProvider> normalizeProviders(
      List<Ircv3ExtensionDefinitionProvider> providers) {
    ArrayList<Ircv3ExtensionDefinitionProvider> sorted =
        new ArrayList<>(Objects.requireNonNullElse(providers, List.of()));
    sorted.sort(
        Comparator.<Ircv3ExtensionDefinitionProvider>comparingInt(
                provider -> provider == null ? Integer.MAX_VALUE : provider.sortOrder())
            .thenComparing(provider -> normalize(provider == null ? "" : provider.providerId())));

    LinkedHashMap<String, Ircv3ExtensionDefinitionProvider> byId = new LinkedHashMap<>();
    for (Ircv3ExtensionDefinitionProvider provider : sorted) {
      String providerId = normalize(provider == null ? "" : provider.providerId());
      if (provider == null || providerId.isEmpty()) {
        throw new IllegalStateException("IRCv3 extension provider must declare a non-blank id");
      }
      Ircv3ExtensionDefinitionProvider previous = byId.putIfAbsent(providerId, provider);
      if (previous != null && previous.getClass() != provider.getClass()) {
        throw new IllegalStateException(
            "Duplicate IRCv3 extension provider id registered: " + providerId);
      }
    }
    return List.copyOf(byId.values());
  }

  private static List<ExtensionDefinition> collectExtensions(
      List<Ircv3ExtensionDefinitionProvider> providers) {
    ArrayList<ExtensionDefinition> definitions = new ArrayList<>();
    for (Ircv3ExtensionDefinitionProvider provider : providers) {
      if (provider == null) {
        continue;
      }
      definitions.addAll(Objects.requireNonNullElse(provider.extensions(), List.of()));
    }
    return List.copyOf(definitions);
  }

  private static List<FeatureDefinition> collectVisibleFeatures(
      List<Ircv3ExtensionDefinitionProvider> providers) {
    ArrayList<FeatureDefinition> features = new ArrayList<>();
    for (Ircv3ExtensionDefinitionProvider provider : providers) {
      if (provider == null) {
        continue;
      }
      features.addAll(Objects.requireNonNullElse(provider.visibleFeatures(), List.of()));
    }
    features.sort(
        Comparator.comparingInt(FeatureDefinition::sortOrder)
            .thenComparing(FeatureDefinition::label, String.CASE_INSENSITIVE_ORDER));
    LinkedHashMap<String, FeatureDefinition> byLabel = new LinkedHashMap<>();
    for (FeatureDefinition feature : features) {
      if (feature == null) {
        continue;
      }
      String label = normalizeLabel(feature.label());
      if (label.isEmpty()) {
        continue;
      }
      FeatureDefinition previous = byLabel.putIfAbsent(label.toLowerCase(Locale.ROOT), feature);
      if (previous != null && !previous.equals(feature)) {
        throw new IllegalStateException(
            "Duplicate IRCv3 visible feature label registered: " + label);
      }
    }
    return List.copyOf(features);
  }

  private static Map<String, ExtensionDefinition> indexDefinitions(
      List<ExtensionDefinition> definitions) {
    LinkedHashMap<String, ExtensionDefinition> byName = new LinkedHashMap<>();
    for (ExtensionDefinition definition : definitions) {
      if (definition == null) {
        continue;
      }
      for (String name : definition.allNames()) {
        if (name.isEmpty()) {
          continue;
        }
        ExtensionDefinition previous = byName.putIfAbsent(name, definition);
        if (previous != null && !previous.equals(definition)) {
          throw new IllegalStateException("Duplicate IRCv3 extension name registered: " + name);
        }
      }
    }
    return Map.copyOf(byName);
  }

  private static List<String> copyNormalized(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    ArrayList<String> normalized = new ArrayList<>(values.size());
    for (String value : values) {
      String key = normalize(value);
      if (!key.isEmpty()) {
        normalized.add(key);
      }
    }
    return List.copyOf(normalized);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeLabel(String value) {
    return Objects.toString(value, "").trim();
  }
}
