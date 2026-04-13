package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalog;
import cafe.woden.ircclient.app.outbound.backend.BackendUploadCommandRegistry;
import cafe.woden.ircclient.app.outbound.backend.IrcBackendExtension;
import cafe.woden.ircclient.app.outbound.backend.MatrixBackendExtension;
import cafe.woden.ircclient.app.outbound.backend.MessageMutationOutboundCommandsRouter;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendFeatureRegistry;
import cafe.woden.ircclient.app.outbound.backend.QuasselBackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TestBackendSupport {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private TestBackendSupport() {}

  public static BackendExtensionCatalog builtInBackendExtensionCatalog() {
    return BackendExtensionCatalog.fromExtensions(
        List.of(
            new IrcBackendExtension(),
            new MatrixBackendExtension(),
            new QuasselBackendExtension()));
  }

  public static CommandTargetPolicy commandTargetPolicy(ServerCatalog serverCatalog) {
    BackendExtensionCatalog catalog = builtInBackendExtensionCatalog();
    return new CommandTargetPolicy(serverCatalog, catalog, catalog);
  }

  public static CommandTargetPolicy commandTargetPolicy(
      ServerCatalog serverCatalog, AvailableBackendIdsPort backendMetadata) {
    return new CommandTargetPolicy(
        serverCatalog, builtInBackendExtensionCatalog(), backendMetadata);
  }

  public static OutboundBackendFeatureRegistry builtInOutboundBackendFeatureRegistry() {
    return new OutboundBackendFeatureRegistry(builtInBackendExtensionCatalog());
  }

  public static OutboundBackendFeatureRegistry outboundBackendFeatureRegistry(
      List<OutboundBackendFeatureAdapter> adapters) {
    return new OutboundBackendFeatureRegistry(
        BackendExtensionCatalog.fromExtensions(
            Objects.requireNonNullElse(adapters, List.<OutboundBackendFeatureAdapter>of()).stream()
                .filter(Objects::nonNull)
                .map(TestBackendSupport::backendExtension)
                .toList()));
  }

  public static MessageMutationOutboundCommandsRouter
      builtInMessageMutationOutboundCommandsRouter() {
    return new MessageMutationOutboundCommandsRouter(builtInBackendExtensionCatalog());
  }

  public static MessageMutationOutboundCommandsRouter messageMutationOutboundCommandsRouter(
      List<MessageMutationOutboundCommands> handlers) {
    return new MessageMutationOutboundCommandsRouter(
        BackendExtensionCatalog.fromExtensions(
            Objects.requireNonNullElse(handlers, List.<MessageMutationOutboundCommands>of())
                .stream()
                .filter(Objects::nonNull)
                .map(TestBackendSupport::backendExtension)
                .toList()));
  }

  public static BackendUploadCommandRegistry backendUploadCommandRegistry(
      List<UploadCommandTranslationHandler> handlers) {
    return new BackendUploadCommandRegistry(
        BackendExtensionCatalog.fromExtensions(
            Objects.requireNonNullElse(handlers, List.<UploadCommandTranslationHandler>of())
                .stream()
                .filter(Objects::nonNull)
                .map(TestBackendSupport::backendExtension)
                .toList()));
  }

  private static BackendExtension backendExtension(OutboundBackendFeatureAdapter adapter) {
    String backendId = backendIdOf(adapter);
    return new BackendExtension() {
      @Override
      public String backendId() {
        return backendId;
      }

      @Override
      public OutboundBackendFeatureAdapter featureAdapter() {
        return adapter;
      }
    };
  }

  private static BackendExtension backendExtension(MessageMutationOutboundCommands handler) {
    String backendId = backendIdOf(handler);
    return new BackendExtension() {
      @Override
      public String backendId() {
        return backendId;
      }

      @Override
      public MessageMutationOutboundCommands messageMutationOutboundCommands() {
        return handler;
      }
    };
  }

  private static BackendExtension backendExtension(UploadCommandTranslationHandler handler) {
    String backendId = backendIdOf(handler);
    return new BackendExtension() {
      @Override
      public String backendId() {
        return backendId;
      }

      @Override
      public UploadCommandTranslationHandler uploadCommandTranslationHandler() {
        return handler;
      }
    };
  }

  private static String backendIdOf(OutboundBackendFeatureAdapter adapter) {
    String backendId = Objects.toString(adapter == null ? "" : adapter.backendId(), "").trim();
    if (!backendId.isEmpty()) {
      return backendId.toLowerCase(Locale.ROOT);
    }
    IrcProperties.Server.Backend backend = adapter == null ? null : adapter.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static String backendIdOf(MessageMutationOutboundCommands handler) {
    String backendId = Objects.toString(handler == null ? "" : handler.backendId(), "").trim();
    if (!backendId.isEmpty()) {
      return backendId.toLowerCase(Locale.ROOT);
    }
    IrcProperties.Server.Backend backend = handler == null ? null : handler.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static String backendIdOf(UploadCommandTranslationHandler handler) {
    String backendId = Objects.toString(handler == null ? "" : handler.backendId(), "").trim();
    if (!backendId.isEmpty()) {
      return backendId.toLowerCase(Locale.ROOT);
    }
    IrcProperties.Server.Backend backend = handler == null ? null : handler.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }
}
