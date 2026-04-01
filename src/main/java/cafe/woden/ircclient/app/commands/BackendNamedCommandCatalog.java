package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.PluginServiceLoaderSupport;
import jakarta.annotation.PreDestroy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Registry for backend named command contributions from built-ins and ServiceLoader plugins. */
@Component
@ApplicationLayer
public class BackendNamedCommandCatalog {

  private static final Logger log = LoggerFactory.getLogger(BackendNamedCommandCatalog.class);

  private final Map<String, BackendNamedCommandHandler> parseHandlersByCommandName;
  private final List<SlashCommandDescriptor> autocompleteCommands;
  private final List<String> generalHelpLines;
  private final Map<String, List<String>> topicHelpLines;
  private final List<URLClassLoader> pluginClassLoaders;

  @Autowired
  public BackendNamedCommandCatalog(
      RuntimeConfigPathPort runtimeConfigPathPort,
      List<BackendNamedCommandHandler> builtInHandlers) {
    this(
        loadInstalledCatalogState(
            List.copyOf(Objects.requireNonNullElse(builtInHandlers, List.of())),
            PluginServiceLoaderSupport.resolvePluginDirectory(
                runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath,
                log),
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendNamedCommandCatalog.class)));
  }

  public static BackendNamedCommandCatalog empty() {
    return fromHandlers(List.of());
  }

  public static BackendNamedCommandCatalog installed() {
    return installed(
        PluginServiceLoaderSupport.resolvePluginDirectory(null, log),
        PluginServiceLoaderSupport.defaultApplicationClassLoader(BackendNamedCommandCatalog.class));
  }

  public static BackendNamedCommandCatalog fromHandlers(List<BackendNamedCommandHandler> handlers) {
    return new BackendNamedCommandCatalog(
        List.copyOf(Objects.requireNonNull(handlers, "handlers")), List.of());
  }

  static BackendNamedCommandCatalog installed(
      RuntimeConfigPathPort runtimeConfigPathPort, ClassLoader applicationClassLoader) {
    return installed(
        PluginServiceLoaderSupport.resolvePluginDirectory(
            runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath, log),
        applicationClassLoader);
  }

  static BackendNamedCommandCatalog installed(
      Path pluginDirectory, ClassLoader applicationClassLoader) {
    return new BackendNamedCommandCatalog(
        loadInstalledCatalogState(List.of(), pluginDirectory, applicationClassLoader));
  }

  private BackendNamedCommandCatalog(LoadedCatalogState state) {
    this(Objects.requireNonNull(state, "state").handlers(), state.pluginClassLoaders());
  }

  private BackendNamedCommandCatalog(
      List<BackendNamedCommandHandler> handlers, List<URLClassLoader> pluginClassLoaders) {
    List<BackendNamedCommandHandler> safeHandlers =
        List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    this.parseHandlersByCommandName = indexParseHandlersByCommandName(safeHandlers);
    this.autocompleteCommands = buildAutocompleteCommands(safeHandlers);
    this.generalHelpLines = buildGeneralHelpLines(safeHandlers);
    this.topicHelpLines = buildTopicHelpLines(safeHandlers);
    this.pluginClassLoaders =
        List.copyOf(Objects.requireNonNull(pluginClassLoaders, "pluginClassLoaders"));
  }

  @PreDestroy
  void shutdown() {
    PluginServiceLoaderSupport.closePluginClassLoaders(
        pluginClassLoaders, log, "[ircafe] failed to close plugin classloader");
  }

  public ParsedInput parse(String line) {
    String raw = Objects.toString(line, "").trim();
    if (raw.isEmpty()) return null;
    if (!raw.startsWith("/")) return null;

    String commandName = extractCommandName(raw);
    if (commandName.isEmpty()) return null;
    BackendNamedCommandHandler handler = parseHandlersByCommandName.get(commandName);
    if (handler == null) return null;
    return handler.parse(raw, commandName);
  }

  public List<SlashCommandDescriptor> autocompleteCommands() {
    return autocompleteCommands;
  }

  public List<String> generalHelpLines() {
    return generalHelpLines;
  }

  public Map<String, List<String>> topicHelpLines() {
    return topicHelpLines;
  }

  private static Map<String, BackendNamedCommandHandler> indexParseHandlersByCommandName(
      List<BackendNamedCommandHandler> handlers) {
    LinkedHashMap<String, BackendNamedCommandHandler> index = new LinkedHashMap<>();
    for (BackendNamedCommandHandler handler : handlers) {
      Set<String> commandNames =
          Objects.requireNonNullElse(handler.supportedCommandNames(), Set.<String>of());
      for (String commandName : commandNames) {
        String normalized =
            BackendNamedCommandRegistrationSupport.normalizeCommandName(commandName);
        if (normalized.isEmpty()) continue;
        if (BackendNamedCommandRegistrationSupport.isReservedCommandName(normalized)) {
          throw new IllegalStateException(
              "Backend named command '"
                  + normalized
                  + "' collides with a reserved built-in command");
        }
        BackendNamedCommandHandler previous = index.putIfAbsent(normalized, handler);
        if (previous != null && previous != handler) {
          throw new IllegalStateException(
              "Duplicate backend named parser handler registered for command '" + normalized + "'");
        }
      }
    }
    return Map.copyOf(index);
  }

  private static List<SlashCommandDescriptor> buildAutocompleteCommands(
      List<BackendNamedCommandHandler> handlers) {
    LinkedHashMap<String, SlashCommandDescriptor> byCommand = new LinkedHashMap<>();
    for (BackendNamedCommandHandler handler : handlers) {
      List<SlashCommandDescriptor> commands =
          Objects.requireNonNullElse(handler.autocompleteCommands(), List.of());
      for (SlashCommandDescriptor command : commands) {
        if (command == null) continue;
        byCommand.putIfAbsent(command.command().toLowerCase(Locale.ROOT), command);
      }
    }
    return List.copyOf(byCommand.values());
  }

  private static List<String> buildGeneralHelpLines(List<BackendNamedCommandHandler> handlers) {
    ArrayList<String> lines = new ArrayList<>();
    for (BackendNamedCommandHandler handler : handlers) {
      List<String> handlerLines = Objects.requireNonNullElse(handler.generalHelpLines(), List.of());
      appendLines(lines, handlerLines);
    }
    return List.copyOf(lines);
  }

  private static Map<String, List<String>> buildTopicHelpLines(
      List<BackendNamedCommandHandler> handlers) {
    LinkedHashMap<String, ArrayList<String>> linesByTopic = new LinkedHashMap<>();
    for (BackendNamedCommandHandler handler : handlers) {
      Map<String, List<String>> handlerLines =
          Objects.requireNonNullElse(handler.topicHelpLines(), Map.of());
      for (Map.Entry<String, List<String>> entry : handlerLines.entrySet()) {
        String topic = normalizeHelpTopic(entry.getKey());
        if (topic.isEmpty()) continue;
        ArrayList<String> lines = linesByTopic.computeIfAbsent(topic, __ -> new ArrayList<>());
        appendLines(lines, entry.getValue());
      }
    }
    LinkedHashMap<String, List<String>> immutable = new LinkedHashMap<>();
    for (Map.Entry<String, ArrayList<String>> entry : linesByTopic.entrySet()) {
      immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Map.copyOf(immutable);
  }

  private static void appendLines(List<String> out, List<String> lines) {
    if (out == null || lines == null) return;
    for (String line : lines) {
      String normalized = Objects.toString(line, "").trim();
      if (!normalized.isEmpty()) {
        out.add(normalized);
      }
    }
  }

  private static LoadedCatalogState loadInstalledCatalogState(
      List<BackendNamedCommandHandler> builtInHandlers,
      Path pluginDirectory,
      ClassLoader applicationClassLoader) {
    PluginServiceLoaderSupport.LoadedServices<BackendNamedCommandHandler> loadedServices =
        PluginServiceLoaderSupport.loadInstalledServices(
            BackendNamedCommandHandler.class,
            builtInHandlers,
            pluginDirectory,
            applicationClassLoader,
            log);
    return new LoadedCatalogState(loadedServices.services(), loadedServices.pluginClassLoaders());
  }

  static Path resolvePluginDirectory(RuntimeConfigPathPort runtimeConfigPathPort) {
    return PluginServiceLoaderSupport.resolvePluginDirectory(
        runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath, log);
  }

  private static String extractCommandName(String line) {
    int end = line.indexOf(' ');
    String token = end < 0 ? line : line.substring(0, end);
    return BackendNamedCommandRegistrationSupport.normalizeCommandName(token);
  }

  private static String normalizeHelpTopic(String raw) {
    String topic = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (topic.startsWith("/")) topic = topic.substring(1).trim();
    return topic;
  }

  private record LoadedCatalogState(
      List<BackendNamedCommandHandler> handlers, List<URLClassLoader> pluginClassLoaders) {}
}
