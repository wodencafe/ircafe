package cafe.woden.ircclient.app.commands;

import java.util.Set;

/** Strategy for parsing backend-scoped command names into typed inputs. */
interface BackendNamedCommandHandler {

  Set<String> supportedCommandNames();

  ParsedInput parse(String line, String matchedCommandName);
}
