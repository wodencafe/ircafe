package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class BackendExtensionCatalogTest {

  @Test
  void resolvesBuiltInBackendStrategiesFromExtensions() {
    BackendExtensionCatalog catalog =
        new BackendExtensionCatalog(
            java.util.List.of(
                new IrcBackendExtension(),
                new MatrixBackendExtension(),
                new QuasselBackendExtension()));

    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor(IrcProperties.Server.Backend.IRC));
    assertInstanceOf(
        MatrixUploadCommandTranslationHandler.class,
        catalog.uploadTranslationHandlerFor(IrcProperties.Server.Backend.MATRIX));
    assertInstanceOf(
        QuasselMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor(IrcProperties.Server.Backend.QUASSEL_CORE));
    assertTrue(
        catalog.featureAdapterFor(IrcProperties.Server.Backend.MATRIX).supportsSemanticUpload());
    assertTrue(
        catalog
            .featureAdapterFor(IrcProperties.Server.Backend.QUASSEL_CORE)
            .supportsQuasselCoreCommands());
    assertNull(catalog.uploadTranslationHandlerFor(IrcProperties.Server.Backend.QUASSEL_CORE));
  }

  @Test
  void rejectsDuplicateBackendExtensions() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new BackendExtensionCatalog(
                java.util.List.of(new IrcBackendExtension(), new DuplicateIrcBackendExtension())));
  }

  @Test
  void rejectsContributionBackendMismatch() {
    assertThrows(
        IllegalStateException.class,
        () -> new BackendExtensionCatalog(java.util.List.of(new MismatchedBackendExtension())));
  }

  @Test
  void resolvesCustomBackendIdExtensions() {
    BackendExtensionCatalog catalog =
        new BackendExtensionCatalog(java.util.List.of(new PluginBackendExtension()));

    assertTrue(catalog.featureAdapterFor("plugin-backend").supportsSemanticUpload());
  }

  private static final class DuplicateIrcBackendExtension implements BackendExtension {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.IRC;
    }
  }

  private static final class MismatchedBackendExtension implements BackendExtension {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new QuasselOutboundBackendFeatureAdapter();
    }
  }

  private static final class PluginBackendExtension implements BackendExtension {
    @Override
    public IrcProperties.Server.Backend backend() {
      return null;
    }

    @Override
    public String backendId() {
      return "plugin-backend";
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new OutboundBackendFeatureAdapter() {
        @Override
        public IrcProperties.Server.Backend backend() {
          return null;
        }

        @Override
        public String backendId() {
          return "plugin-backend";
        }

        @Override
        public boolean supportsSemanticUpload() {
          return true;
        }
      };
    }
  }
}
