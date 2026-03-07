package cafe.woden.ircclient.app.commands;

import org.springframework.stereotype.Component;

/** Handles Quassel backend command aliases like /quasselsetup and /quasselnet. */
@Component
final class QuasselBackendNamedCommandHandler implements BackendNamedCommandHandler {

  @Override
  public ParsedInput parse(String line) {
    boolean setup = BackendNamedCommandParser.matchesCommand(line, "/quasselsetup");
    boolean setupAlias = BackendNamedCommandParser.matchesCommand(line, "/qsetup");
    if (setup || setupAlias) {
      String serverId =
          setup
              ? BackendNamedCommandParser.argAfter(line, "/quasselsetup")
              : BackendNamedCommandParser.argAfter(line, "/qsetup");
      return new ParsedInput.BackendNamed("quasselsetup", serverId);
    }

    boolean network = BackendNamedCommandParser.matchesCommand(line, "/quasselnet");
    boolean networkAlias = BackendNamedCommandParser.matchesCommand(line, "/qnet");
    if (network || networkAlias) {
      String args =
          network
              ? BackendNamedCommandParser.argAfter(line, "/quasselnet")
              : BackendNamedCommandParser.argAfter(line, "/qnet");
      return new ParsedInput.BackendNamed("quasselnet", args);
    }

    return null;
  }
}
