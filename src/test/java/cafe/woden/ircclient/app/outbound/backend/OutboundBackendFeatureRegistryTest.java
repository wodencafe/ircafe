package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.outbound.*;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundBackendFeatureRegistryTest {

  @Test
  void adapterDefaultsToNoFeaturesWhenBackendHasNoAdapter() {
    OutboundBackendFeatureRegistry registry =
        new OutboundBackendFeatureRegistry(List.of(new MatrixOutboundBackendFeatureAdapter()));

    OutboundBackendFeatureAdapter ircAdapter =
        registry.adapterFor(IrcProperties.Server.Backend.IRC);
    assertFalse(ircAdapter.supportsSemanticUpload());
    assertFalse(ircAdapter.supportsQuasselCoreCommands());
  }

  @Test
  void matrixAndQuasselAdaptersExposeBackendSpecificFeatures() {
    OutboundBackendFeatureRegistry registry =
        new OutboundBackendFeatureRegistry(
            List.of(
                new MatrixOutboundBackendFeatureAdapter(),
                new QuasselOutboundBackendFeatureAdapter()));

    assertTrue(registry.adapterFor(IrcProperties.Server.Backend.MATRIX).supportsSemanticUpload());
    assertFalse(
        registry.adapterFor(IrcProperties.Server.Backend.MATRIX).supportsQuasselCoreCommands());

    assertFalse(
        registry.adapterFor(IrcProperties.Server.Backend.QUASSEL_CORE).supportsSemanticUpload());
    assertTrue(
        registry
            .adapterFor(IrcProperties.Server.Backend.QUASSEL_CORE)
            .supportsQuasselCoreCommands());
  }

  @Test
  void duplicateBackendAdaptersFailFast() {
    OutboundBackendFeatureAdapter first =
        new OutboundBackendFeatureAdapter() {
          @Override
          public IrcProperties.Server.Backend backend() {
            return IrcProperties.Server.Backend.MATRIX;
          }
        };
    OutboundBackendFeatureAdapter second =
        new OutboundBackendFeatureAdapter() {
          @Override
          public IrcProperties.Server.Backend backend() {
            return IrcProperties.Server.Backend.MATRIX;
          }
        };

    assertThrows(
        IllegalStateException.class,
        () -> new OutboundBackendFeatureRegistry(List.of(first, second)));
  }
}
