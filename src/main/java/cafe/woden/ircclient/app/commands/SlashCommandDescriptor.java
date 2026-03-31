package cafe.woden.ircclient.app.commands;

import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Presentation metadata for a slash command exposed in autocomplete/help surfaces. */
@ApplicationLayer
public record SlashCommandDescriptor(String command, String summary) {

  public SlashCommandDescriptor {
    String normalized = Objects.toString(command, "").trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    command = normalized.toLowerCase(Locale.ROOT);
    summary = Objects.toString(summary, "").trim();
  }
}
