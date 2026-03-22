package cafe.woden.ircclient.app.outbound.dispatch;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Registers a cohesive slice of parsed outbound commands. */
@ApplicationLayer
public interface OutboundCommandRegistrar {
  void registerCommands(OutboundCommandRegistry registry);
}
