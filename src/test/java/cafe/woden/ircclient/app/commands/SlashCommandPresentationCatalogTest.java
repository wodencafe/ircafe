package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlashCommandPresentationCatalogTest {

  @Test
  void mergesContributorAndBackendAutocompleteCommands() {
    SlashCommandPresentationContributor builtInContributor =
        new SlashCommandPresentationContributor() {
          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/join", "Join channel"));
          }
        };
    BackendNamedCommandHandler backendHandler =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return new ParsedInput.BackendNamed("backendping", "");
          }

          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/backendping", "Plugin test command"));
          }
        };

    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(builtInContributor), new BackendNamedCommandCatalog(List.of(backendHandler)));

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/backendping"));
  }

  @Test
  void backendTopicHelpLinesAreExposedThroughCatalogHandlers() {
    BackendNamedCommandHandler backendHandler =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return new ParsedInput.BackendNamed("backendping", "");
          }

          @Override
          public Map<String, List<String>> topicHelpLines() {
            return Map.of("backendping", List.of("/backendping <arg>"));
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), new BackendNamedCommandCatalog(List.of(backendHandler)));
    AtomicReference<String> rendered = new AtomicReference<>();

    catalog
        .topicHelpHandlers((target, line) -> rendered.set(target.target() + ":" + line))
        .get("backendping")
        .accept(new TargetRef("libera", "status"));

    assertEquals("status:/backendping <arg>", rendered.get());
  }
}
