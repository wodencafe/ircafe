package cafe.woden.ircclient.app.commands;

/** Strategy for parsing backend-scoped command names into typed inputs. */
interface BackendNamedCommandHandler {

  ParsedInput parse(String line);
}
