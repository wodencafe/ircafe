package cafe.woden.ircclient.app.outbound.help.spi;

import cafe.woden.ircclient.app.commands.SlashCommandPresentationContributor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Transitional alias for command presentation contributors used by help/autocomplete surfaces. */
@Deprecated(forRemoval = false)
@ApplicationLayer
public interface OutboundHelpContributor extends SlashCommandPresentationContributor {}
