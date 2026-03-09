package cafe.woden.ircclient.app.commands;

import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles Quassel backend command aliases like /quasselsetup and /quasselnet. */
@Component
@ApplicationLayer
final class QuasselBackendNamedCommandHandler implements BackendNamedCommandHandler {

  @Override
  public Set<String> supportedCommandNames() {
    return Set.of("quasselsetup", "qsetup", "quasselnet", "qnet");
  }

  @Override
  public ParsedInput parse(String line, String matchedCommandName) {
    String commandToken = "/" + matchedCommandName;
    return switch (matchedCommandName) {
      case "quasselsetup", "qsetup" ->
          new ParsedInput.BackendNamed(
              BackendNamedCommandNames.QUASSEL_SETUP,
              BackendNamedCommandParser.argAfter(line, commandToken));
      case "quasselnet", "qnet" ->
          new ParsedInput.BackendNamed(
              BackendNamedCommandNames.QUASSEL_NETWORK,
              BackendNamedCommandParser.argAfter(line, commandToken));
      default -> null;
    };
  }
}
