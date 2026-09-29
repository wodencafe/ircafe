package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.upload.spi.SemanticUploadCommandHandler;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTargetView;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.adapter.IrcNegotiatedFeaturePortAdapter;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatrixOutboundCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);

  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry backendFeatureRegistry =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.builtInOutboundBackendFeatureRegistry();
  private final OutboundBackendCapabilityPolicy capabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          backendFeatureRegistry,
          new IrcNegotiatedFeaturePortAdapter(irc),
          irc,
          cafe.woden.ircclient.app.api.AvailableBackendIdsPort.builtInsOnly());
  private final MatrixOutboundCommandSupport matrixCommandSupport =
      new MatrixOutboundCommandSupport();
  private final BackendUploadCommandRegistry uploadCommandRegistry =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.backendUploadCommandRegistry(
          List.of(new MatrixUploadCommandTranslationHandler()));
  private final MatrixOutboundCommandService service =
      new MatrixOutboundCommandService(
          ui, capabilityPolicy, matrixCommandSupport, uploadCommandRegistry);

  @Test
  void appendUploadHelpAndUsageDelegateToUiStatus() {
    TargetRef out = new TargetRef("matrix", "!room:example.org");

    service.appendUploadHelp(targetView(out));
    service.appendUploadUsage(targetView(out));

    verify(ui)
        .appendStatus(
            out,
            "(help)",
            "/upload <m.image|m.file|m.video|m.audio> <path> [caption]  (msgtype shortcuts: image|file|video|audio)");
    verify(ui).appendStatus(out, "(upload)", "Usage: /upload <msgtype> <path> [caption]");
    verify(ui)
        .appendStatus(
            out,
            "(upload)",
            "msgtype: m.image | m.file | m.video | m.audio (shortcuts: image|file|video|audio)");
  }

  @Test
  void prepareUploadOnMatrixBackendReturnsTranslatedPrivmsgLine() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    SemanticUploadCommandHandler.UploadPreparation preparation =
        service.prepareUpload(targetView(room), "image", "/tmp/My File.png", "");

    assertFalse(preparation.showUsage());
    assertEquals("", preparation.statusMessage());
    assertEquals(
        "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/My\\sFile.png PRIVMSG !room:example.org :My File.png",
        preparation.line());
  }

  @Test
  void prepareUploadOnNonMatrixBackendReturnsUnsupportedStatus() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithBackend("libera", IrcProperties.Server.Backend.IRC)));

    SemanticUploadCommandHandler.UploadPreparation preparation =
        service.prepareUpload(targetView(channel), "m.image", "/tmp/photo.png", "caption");

    assertFalse(preparation.showUsage());
    assertEquals("", preparation.line());
    assertTrue(preparation.statusMessage().contains("does not use the Matrix backend"));
  }

  @Test
  void prepareUploadWithInvalidInputReturnsUsage() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");

    SemanticUploadCommandHandler.UploadPreparation invalidMsgType =
        service.prepareUpload(targetView(room), "m.bad", "/tmp/photo.png", "caption");
    SemanticUploadCommandHandler.UploadPreparation blankPath =
        service.prepareUpload(targetView(room), "m.image", "   ", "caption");

    assertTrue(invalidMsgType.showUsage());
    assertEquals("", invalidMsgType.line());
    assertEquals("", invalidMsgType.statusMessage());

    assertTrue(blankPath.showUsage());
    assertEquals("", blankPath.line());
    assertEquals("", blankPath.statusMessage());
  }

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return IrcPropertiesTestFixtures.serverBuilder(id)
        .host("matrix.example.org")
        .port(443)
        .serverPassword("secret")
        .backend(backend)
        .build();
  }

  private static UploadCommandTargetView targetView(TargetRef target) {
    return new UploadCommandTargetView(target.serverId(), target.target());
  }
}
