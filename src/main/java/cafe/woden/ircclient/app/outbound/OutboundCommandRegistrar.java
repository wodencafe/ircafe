package cafe.woden.ircclient.app.outbound;

/** Registers a cohesive slice of parsed outbound commands. */
interface OutboundCommandRegistrar {
  void registerCommands(OutboundCommandRegistry registry);
}
