package cafe.woden.ircclient.app.commands;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Internal strategy for parsing a subset of slash commands. */
@ApplicationLayer
interface SlashCommandParseStrategy {

  ParsedInput tryParse(String line);
}
