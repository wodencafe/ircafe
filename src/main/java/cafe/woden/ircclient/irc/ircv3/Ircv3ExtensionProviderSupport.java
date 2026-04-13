package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Shared factory helpers for built-in IRCv3 extension definition providers. */
final class Ircv3ExtensionProviderSupport {

  private Ircv3ExtensionProviderSupport() {}

  static Ircv3ExtensionRegistry.ExtensionDefinition capability(
      String id,
      Ircv3ExtensionRegistry.SpecStatus specStatus,
      String label,
      Ircv3ExtensionRegistry.UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return capability(id, specStatus, id, id, label, group, sortOrder, impactSummary, aliases);
  }

  static Ircv3ExtensionRegistry.ExtensionDefinition capability(
      String id,
      Ircv3ExtensionRegistry.SpecStatus specStatus,
      String requestToken,
      String preferenceKey,
      String label,
      Ircv3ExtensionRegistry.UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new Ircv3ExtensionRegistry.ExtensionDefinition(
        id,
        Ircv3ExtensionRegistry.ExtensionKind.CAPABILITY,
        specStatus,
        List.of(aliases),
        requestToken,
        preferenceKey,
        new Ircv3ExtensionRegistry.UiMetadata(label, group, sortOrder, impactSummary));
  }

  static Ircv3ExtensionRegistry.ExtensionDefinition nonRequestableCapability(
      String id,
      Ircv3ExtensionRegistry.SpecStatus specStatus,
      String label,
      Ircv3ExtensionRegistry.UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new Ircv3ExtensionRegistry.ExtensionDefinition(
        id,
        Ircv3ExtensionRegistry.ExtensionKind.CAPABILITY,
        specStatus,
        List.of(aliases),
        "",
        id,
        new Ircv3ExtensionRegistry.UiMetadata(label, group, sortOrder, impactSummary));
  }

  static Ircv3ExtensionRegistry.ExtensionDefinition tagFeature(
      String id,
      Ircv3ExtensionRegistry.SpecStatus specStatus,
      String label,
      Ircv3ExtensionRegistry.UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new Ircv3ExtensionRegistry.ExtensionDefinition(
        id,
        Ircv3ExtensionRegistry.ExtensionKind.TAG_FEATURE,
        specStatus,
        List.of(aliases),
        "",
        id,
        new Ircv3ExtensionRegistry.UiMetadata(label, group, sortOrder, impactSummary));
  }

  static Ircv3ExtensionRegistry.ExtensionDefinition experimental(
      String id,
      String label,
      Ircv3ExtensionRegistry.UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new Ircv3ExtensionRegistry.ExtensionDefinition(
        id,
        Ircv3ExtensionRegistry.ExtensionKind.EXPERIMENTAL,
        Ircv3ExtensionRegistry.SpecStatus.EXPERIMENTAL,
        List.of(aliases),
        "",
        id,
        new Ircv3ExtensionRegistry.UiMetadata(label, group, sortOrder, impactSummary));
  }

  static Ircv3ExtensionRegistry.FeatureDefinition feature(
      int sortOrder, String label, List<String> requiredAll, List<String> requiredAny) {
    return new Ircv3ExtensionRegistry.FeatureDefinition(sortOrder, label, requiredAll, requiredAny);
  }
}
