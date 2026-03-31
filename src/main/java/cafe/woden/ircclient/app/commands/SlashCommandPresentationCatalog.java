package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.model.TargetRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared autocomplete/help catalog for built-in and backend-named slash commands. */
@Component
@ApplicationLayer
public class SlashCommandPresentationCatalog {

  private final List<SlashCommandPresentationContributor> contributors;
  private final BackendNamedCommandCatalog backendNamedCommandCatalog;
  private final List<SlashCommandDescriptor> autocompleteCommands;
  private final Map<String, Consumer<TargetRef>> topicHelpHandlers;

  public SlashCommandPresentationCatalog(
      List<SlashCommandPresentationContributor> contributors,
      BackendNamedCommandCatalog backendNamedCommandCatalog) {
    this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors"));
    this.backendNamedCommandCatalog =
        Objects.requireNonNull(backendNamedCommandCatalog, "backendNamedCommandCatalog");
    this.autocompleteCommands = buildAutocompleteCommands();
    this.topicHelpHandlers = buildTopicHelpHandlers();
  }

  public List<SlashCommandDescriptor> autocompleteCommands() {
    return autocompleteCommands;
  }

  public void appendGeneralHelp(TargetRef out, BiConsumer<TargetRef, String> lineAppender) {
    Objects.requireNonNull(lineAppender, "lineAppender");
    for (SlashCommandPresentationContributor contributor : contributors) {
      contributor.appendGeneralHelp(out);
    }
    backendNamedCommandCatalog
        .generalHelpLines()
        .forEach(line -> appendStaticHelpLine(out, line, lineAppender));
  }

  public Map<String, Consumer<TargetRef>> topicHelpHandlers(
      BiConsumer<TargetRef, String> lineAppender) {
    Objects.requireNonNull(lineAppender, "lineAppender");
    LinkedHashMap<String, Consumer<TargetRef>> handlers = new LinkedHashMap<>(topicHelpHandlers);
    for (Map.Entry<String, List<String>> entry :
        backendNamedCommandCatalog.topicHelpLines().entrySet()) {
      String topic = normalizeHelpTopic(entry.getKey());
      List<String> lines = entry.getValue();
      if (topic.isEmpty() || lines == null || lines.isEmpty()) continue;
      handlers.put(
          topic, out -> lines.forEach(line -> appendStaticHelpLine(out, line, lineAppender)));
    }
    return Map.copyOf(handlers);
  }

  private List<SlashCommandDescriptor> buildAutocompleteCommands() {
    LinkedHashMap<String, SlashCommandDescriptor> merged = new LinkedHashMap<>();
    for (SlashCommandPresentationContributor contributor : contributors) {
      for (SlashCommandDescriptor command :
          Objects.requireNonNullElse(
              contributor.autocompleteCommands(), List.<SlashCommandDescriptor>of())) {
        if (command == null) continue;
        merged.putIfAbsent(command.command().toLowerCase(Locale.ROOT), command);
      }
    }
    for (SlashCommandDescriptor command : backendNamedCommandCatalog.autocompleteCommands()) {
      if (command == null) continue;
      merged.putIfAbsent(command.command().toLowerCase(Locale.ROOT), command);
    }
    return List.copyOf(merged.values());
  }

  private Map<String, Consumer<TargetRef>> buildTopicHelpHandlers() {
    LinkedHashMap<String, Consumer<TargetRef>> handlers = new LinkedHashMap<>();
    for (SlashCommandPresentationContributor contributor : contributors) {
      Map<String, Consumer<TargetRef>> topicHandlers =
          Objects.requireNonNullElse(contributor.topicHelpHandlers(), Map.of());
      for (Map.Entry<String, Consumer<TargetRef>> entry : topicHandlers.entrySet()) {
        String topic = normalizeHelpTopic(entry.getKey());
        Consumer<TargetRef> consumer = entry.getValue();
        if (!topic.isEmpty() && consumer != null) {
          handlers.put(topic, consumer);
        }
      }
    }
    return Map.copyOf(handlers);
  }

  private static void appendStaticHelpLine(
      TargetRef out, String line, BiConsumer<TargetRef, String> lineAppender) {
    if (out == null || line == null || line.isBlank() || lineAppender == null) return;
    lineAppender.accept(out, line);
  }

  private static String normalizeHelpTopic(String raw) {
    String topic = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (topic.startsWith("/")) topic = topic.substring(1).trim();
    return topic;
  }
}
