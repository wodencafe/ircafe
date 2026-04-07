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

  private static final List<Ircv3ExtensionDefinitionProvider> ALL_PROVIDERS = loadProviders();

  private static final List<ExtensionDefinition> REGISTERED_EXTENSIONS =
      collectExtensions(ALL_PROVIDERS);

  private static final List<ExtensionDefinition> REQUESTABLE_CAPABILITIES =
      REGISTERED_EXTENSIONS.stream().filter(ExtensionDefinition::requestable).toList();

  private static final List<String> REQUESTABLE_CAPABILITY_TOKENS =
      REQUESTABLE_CAPABILITIES.stream().map(ExtensionDefinition::requestToken).toList();

  private static final List<FeatureDefinition> VISIBLE_FEATURES =
      collectVisibleFeatures(ALL_PROVIDERS);

  private static final Map<String, ExtensionDefinition> BY_NAME =
      indexDefinitions(REGISTERED_EXTENSIONS);

  private Ircv3ExtensionRegistry() {}

  public static List<ExtensionDefinition> all() {
    return REGISTERED_EXTENSIONS;
  }

  public static List<String> providerIds() {
    return ALL_PROVIDERS.stream().map(Ircv3ExtensionDefinitionProvider::providerId).toList();
  }

  public static Optional<ExtensionDefinition> find(String name) {
    return Optional.ofNullable(BY_NAME.get(normalize(name)));
  }

  public static List<ExtensionDefinition> requestableCapabilities() {
    return REQUESTABLE_CAPABILITIES;
  }

  public static List<String> requestableCapabilityTokens() {
    return REQUESTABLE_CAPABILITY_TOKENS;
  }

  public static List<FeatureDefinition> visibleFeatures() {
    return VISIBLE_FEATURES;
  }

  public static String requestTokenFor(String name) {
    return find(name)
        .filter(ExtensionDefinition::requestable)
        .map(ExtensionDefinition::requestToken)
        .orElse("");
  }

  public static String preferenceKeyFor(String name) {
    return find(name).map(ExtensionDefinition::preferenceKey).orElse(normalize(name));
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

    providers.sort(
        Comparator.comparingInt(Ircv3ExtensionDefinitionProvider::sortOrder)
            .thenComparing(provider -> normalize(provider.providerId())));

    LinkedHashMap<String, Ircv3ExtensionDefinitionProvider> byId = new LinkedHashMap<>();
    for (Ircv3ExtensionDefinitionProvider provider : providers) {
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
