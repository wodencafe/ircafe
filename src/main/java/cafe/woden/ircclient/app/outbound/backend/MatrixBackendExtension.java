package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BuiltInBackendEditorProfiles;
import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import com.google.auto.service.AutoService;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Built-in backend extension for the Matrix transport. */
@Component
@SecondaryAdapter
@ApplicationLayer
@AutoService(BackendExtension.class)
public final class MatrixBackendExtension implements BackendExtension {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final MessageMutationOutboundCommands MESSAGE_MUTATION_COMMANDS =
      new MatrixMessageMutationOutboundCommands();

  private static final OutboundBackendFeatureAdapter FEATURE_ADAPTER =
      new MatrixOutboundBackendFeatureAdapter();

  private static final UploadCommandTranslationHandler UPLOAD_TRANSLATION_HANDLER =
      new MatrixUploadCommandTranslationHandler(new MatrixOutboundCommandSupport());

  @Override
  public String backendId() {
    return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX);
  }

  @Override
  public OutboundBackendFeatureAdapter featureAdapter() {
    return FEATURE_ADAPTER;
  }

  @Override
  public MessageMutationOutboundCommands messageMutationOutboundCommands() {
    return MESSAGE_MUTATION_COMMANDS;
  }

  @Override
  public UploadCommandTranslationHandler uploadCommandTranslationHandler() {
    return UPLOAD_TRANSLATION_HANDLER;
  }

  @Override
  public BackendEditorProfileSpec editorProfile() {
    return BuiltInBackendEditorProfiles.matrix();
  }
}
