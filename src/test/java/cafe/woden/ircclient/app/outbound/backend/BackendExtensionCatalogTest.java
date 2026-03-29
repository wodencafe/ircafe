package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import org.junit.jupiter.api.Test;

class BackendExtensionCatalogTest {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

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

  @Test
  void acceptsLegacyEnumOnlyExtensions() {
    BackendExtensionCatalog catalog =
        new BackendExtensionCatalog(java.util.List.of(new LegacyMatrixBackendExtension()));

    assertTrue(catalog.featureAdapterFor("matrix").supportsSemanticUpload());
    assertInstanceOf(
        LegacyMatrixMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor("matrix"));
  }

  private static final class DuplicateIrcBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
    }
  }

  private static final class MismatchedBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX);
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new QuasselOutboundBackendFeatureAdapter();
    }
  }

  private static final class PluginBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return "plugin-backend";
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new OutboundBackendFeatureAdapter() {
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

  private static final class LegacyMatrixBackendExtension implements BackendExtension {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new LegacyMatrixFeatureAdapter();
    }

    @Override
    public MessageMutationOutboundCommands messageMutationOutboundCommands() {
      return new LegacyMatrixMessageMutationOutboundCommands();
    }
  }

  private static final class LegacyMatrixFeatureAdapter implements OutboundBackendFeatureAdapter {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public boolean supportsSemanticUpload() {
      return true;
    }
  }

  private static final class LegacyMatrixMessageMutationOutboundCommands
      implements MessageMutationOutboundCommands {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public String buildReplyRawLine(TargetRef target, String replyToMessageId, String message) {
      return "";
    }

    @Override
    public String buildReactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildUnreactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildEditRawLine(TargetRef target, String targetMessageId, String editedText) {
      return "";
    }

    @Override
    public String buildRedactRawLine(TargetRef target, String targetMessageId, String reason) {
      return "";
    }
  }
}
