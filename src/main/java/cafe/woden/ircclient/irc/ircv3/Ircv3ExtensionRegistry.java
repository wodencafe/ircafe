package cafe.woden.ircclient.irc.ircv3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
      String label, List<String> requiredAll, List<String> requiredAny) {
    public FeatureDefinition {
      label = normalizeLabel(label);
      requiredAll = copyNormalized(requiredAll);
      requiredAny = copyNormalized(requiredAny);
    }
  }

  private static final List<ExtensionDefinition> BUILT_IN_EXTENSIONS =
      List.of(
          capability(
              "multi-prefix",
              SpecStatus.STABLE,
              "multi-prefix",
              UiGroup.CORE,
              130,
              "Preserves all nick privilege prefixes (not just the highest) in user data."),
          capability(
              "cap-notify",
              SpecStatus.STABLE,
              "CAP updates",
              UiGroup.CORE,
              140,
              "Allows capability change notifications after initial connection."),
          capability(
              "invite-notify",
              SpecStatus.STABLE,
              "Invite notifications",
              UiGroup.CORE,
              145,
              "Receives invite events for channels you share without extra queries."),
          capability(
              "away-notify",
              SpecStatus.STABLE,
              "Away status updates",
              UiGroup.CORE,
              90,
              "Tracks away/back state transitions for users."),
          capability(
              "account-notify",
              SpecStatus.STABLE,
              "Account status updates",
              UiGroup.CORE,
              80,
              "Tracks account login/logout changes for users."),
          capability(
              "monitor",
              SpecStatus.STABLE,
              "MONITOR",
              UiGroup.CORE,
              155,
              "Lets IRCafe track online/offline state for monitored nicknames."),
          capability(
              "extended-monitor",
              SpecStatus.STABLE,
              "Extended MONITOR",
              UiGroup.CORE,
              160,
              "Extends MONITOR presence notifications to additional events."),
          capability(
              "extended-join",
              SpecStatus.STABLE,
              "Extended join data",
              UiGroup.CORE,
              100,
              "Adds account/realname metadata to join events when available."),
          capability(
              "setname",
              SpecStatus.STABLE,
              "Setname updates",
              UiGroup.CORE,
              120,
              "Receives user real-name changes without extra lookups."),
          capability(
              "chghost",
              SpecStatus.STABLE,
              "Hostmask changes",
              UiGroup.CORE,
              110,
              "Keeps hostmask/userhost identity changes in sync."),
          capability(
              "message-tags",
              SpecStatus.STABLE,
              "Message tags",
              UiGroup.CORE,
              10,
              "Foundation for many IRCv3 features: carries structured metadata on messages."),
          capability(
              "server-time",
              SpecStatus.STABLE,
              "Server timestamps",
              UiGroup.CORE,
              30,
              "Uses server-provided timestamps to improve ordering and replay accuracy."),
          capability(
              "standard-replies",
              SpecStatus.STABLE,
              "Standard replies",
              UiGroup.CORE,
              60,
              "Provides structured success/error replies from the server."),
          capability(
              "echo-message",
              SpecStatus.STABLE,
              "Echo own messages",
              UiGroup.CORE,
              40,
              "Server echoes your outbound messages, improving multi-client/bouncer consistency."),
          capability(
              "labeled-response",
              SpecStatus.STABLE,
              "Labeled responses",
              UiGroup.CORE,
              50,
              "Correlates command responses with requests more reliably."),
          capability(
              "read-marker",
              SpecStatus.DRAFT,
              "draft/read-marker",
              "read-marker",
              "Read markers (draft)",
              UiGroup.CONVERSATION,
              240,
              "Enables read-position markers on servers that support them.",
              "draft/read-marker"),
          capability(
              "multiline",
              SpecStatus.DRAFT,
              "draft/multiline",
              "multiline",
              "Multiline messages (draft)",
              UiGroup.CONVERSATION,
              220,
              "Allows sending and receiving multiline messages as a single logical message.",
              "draft/multiline"),
          capability(
              "message-redaction",
              SpecStatus.DRAFT,
              "draft/message-redaction",
              "message-redaction",
              "Message redaction (draft)",
              UiGroup.CONVERSATION,
              300,
              "Allows delete/redaction updates for messages.",
              "draft/message-redaction"),
          capability(
              "batch",
              SpecStatus.STABLE,
              "batch",
              UiGroup.HISTORY,
              410,
              "Groups related events into coherent batches (useful for playback/history)."),
          capability(
              "chathistory",
              SpecStatus.DRAFT,
              "draft/chathistory",
              "chathistory",
              "Chat history (draft)",
              UiGroup.HISTORY,
              430,
              "Enables server-side history retrieval and backfill features.",
              "draft/chathistory"),
          capability(
              "znc.in/playback",
              SpecStatus.STABLE,
              "ZNC playback",
              UiGroup.HISTORY,
              440,
              "Requests playback support from ZNC bouncers when available."),
          capability(
              "account-tag",
              SpecStatus.STABLE,
              "Account tags",
              UiGroup.CORE,
              70,
              "Attaches account metadata to messages for richer identity info."),
          capability(
              "userhost-in-names",
              SpecStatus.STABLE,
              "USERHOST in NAMES",
              UiGroup.CORE,
              150,
              "May provide richer host/user identity details during names lists."),
          nonRequestableCapability(
              "sts",
              SpecStatus.STABLE,
              "Strict transport security",
              UiGroup.CORE,
              20,
              "Learns strict transport policy and upgrades future connects for this host to TLS."),
          tagFeature(
              "reply",
              SpecStatus.STABLE,
              "Replies",
              UiGroup.CONVERSATION,
              250,
              "Reply threading is carried by message tags on top of message-tags transport.",
              "draft/reply"),
          tagFeature(
              "react",
              SpecStatus.DRAFT,
              "Reactions",
              UiGroup.CONVERSATION,
              260,
              "Reactions are carried by message tags on top of message-tags transport.",
              "draft/react"),
          tagFeature(
              "unreact",
              SpecStatus.DRAFT,
              "Reaction removal",
              UiGroup.CONVERSATION,
              265,
              "Reaction removals are carried by message tags on top of message-tags transport.",
              "draft/unreact"),
          tagFeature(
              "typing",
              SpecStatus.STABLE,
              "Typing",
              UiGroup.CONVERSATION,
              230,
              "Typing indicators are sent as client-only tags and depend on CLIENTTAGDENY policy.",
              "draft/typing"),
          tagFeature(
              "channel-context",
              SpecStatus.DRAFT,
              "Channel context",
              UiGroup.CONVERSATION,
              245,
              "Channel-context is a client tag layered on top of message-tags transport.",
              "draft/channel-context"),
          experimental(
              "message-edit",
              "Message edits (experimental)",
              UiGroup.CONVERSATION,
              280,
              "Experimental message editing support; not part of the published IRCv3 surface.",
              "draft/message-edit"));

  private static final List<ExtensionDefinition> REQUESTABLE_CAPABILITIES =
      BUILT_IN_EXTENSIONS.stream().filter(ExtensionDefinition::requestable).toList();

  private static final List<String> REQUESTABLE_CAPABILITY_TOKENS =
      REQUESTABLE_CAPABILITIES.stream().map(ExtensionDefinition::requestToken).toList();

  private static final List<FeatureDefinition> VISIBLE_FEATURES =
      List.of(
          new FeatureDefinition("Replies", List.of("message-tags"), List.of()),
          new FeatureDefinition("Reactions", List.of("message-tags"), List.of()),
          new FeatureDefinition("Reaction removal", List.of("message-tags"), List.of()),
          new FeatureDefinition(
              "Message redaction",
              List.of(),
              List.of("message-redaction", "draft/message-redaction")),
          new FeatureDefinition(
              "History", List.of(), List.of("chathistory", "draft/chathistory", "znc.in/playback")),
          new FeatureDefinition("Typing", List.of("message-tags"), List.of()),
          new FeatureDefinition(
              "Read markers", List.of(), List.of("read-marker", "draft/read-marker")));

  private static final Map<String, ExtensionDefinition> BY_NAME =
      indexDefinitions(BUILT_IN_EXTENSIONS);

  private Ircv3ExtensionRegistry() {}

  public static List<ExtensionDefinition> all() {
    return BUILT_IN_EXTENSIONS;
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

  private static ExtensionDefinition capability(
      String id,
      SpecStatus specStatus,
      String label,
      UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return capability(id, specStatus, id, id, label, group, sortOrder, impactSummary, aliases);
  }

  private static ExtensionDefinition capability(
      String id,
      SpecStatus specStatus,
      String requestToken,
      String preferenceKey,
      String label,
      UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new ExtensionDefinition(
        id,
        ExtensionKind.CAPABILITY,
        specStatus,
        List.of(aliases),
        requestToken,
        preferenceKey,
        new UiMetadata(label, group, sortOrder, impactSummary));
  }

  private static ExtensionDefinition nonRequestableCapability(
      String id,
      SpecStatus specStatus,
      String label,
      UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new ExtensionDefinition(
        id,
        ExtensionKind.CAPABILITY,
        specStatus,
        List.of(aliases),
        "",
        id,
        new UiMetadata(label, group, sortOrder, impactSummary));
  }

  private static ExtensionDefinition tagFeature(
      String id,
      SpecStatus specStatus,
      String label,
      UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new ExtensionDefinition(
        id,
        ExtensionKind.TAG_FEATURE,
        specStatus,
        List.of(aliases),
        "",
        id,
        new UiMetadata(label, group, sortOrder, impactSummary));
  }

  private static ExtensionDefinition experimental(
      String id,
      String label,
      UiGroup group,
      int sortOrder,
      String impactSummary,
      String... aliases) {
    return new ExtensionDefinition(
        id,
        ExtensionKind.EXPERIMENTAL,
        SpecStatus.EXPERIMENTAL,
        List.of(aliases),
        "",
        id,
        new UiMetadata(label, group, sortOrder, impactSummary));
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
