package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;

/** Backend-specific outbound command feature adapter. */
interface OutboundBackendFeatureAdapter {

  IrcProperties.Server.Backend backend();

  default boolean supportsSemanticUpload() {
    return false;
  }

  default boolean supportsQuasselCoreCommands() {
    return false;
  }
}
