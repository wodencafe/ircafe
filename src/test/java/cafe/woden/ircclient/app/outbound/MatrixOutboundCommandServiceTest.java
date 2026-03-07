package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatrixOutboundCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);

  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry backendFeatureRegistry =
      new OutboundBackendFeatureRegistry(List.of(new MatrixOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy capabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy, backendFeatureRegistry, IrcNegotiatedFeaturePort.from(irc), irc);
  private final MatrixOutboundCommandSupport matrixCommandSupport =
      new MatrixOutboundCommandSupport();
  private final BackendUploadCommandRegistry uploadCommandRegistry =
      new BackendUploadCommandRegistry(
          List.of(new MatrixUploadCommandTranslationHandler(matrixCommandSupport)));
  private final MatrixOutboundCommandService service =
      new MatrixOutboundCommandService(
          ui, capabilityPolicy, matrixCommandSupport, uploadCommandRegistry);

  @Test
  void appendUploadHelpAndUsageDelegateToUiStatus() {
    TargetRef out = new TargetRef("matrix", "!room:example.org");

    service.appendUploadHelp(out);
    service.appendUploadUsage(out);

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
        service.prepareUpload(room, "image", "/tmp/My File.png", "");

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
        service.prepareUpload(channel, "m.image", "/tmp/photo.png", "caption");

    assertFalse(preparation.showUsage());
    assertEquals("", preparation.line());
    assertTrue(preparation.statusMessage().contains("does not use the Matrix backend"));
  }

  @Test
  void prepareUploadWithInvalidInputReturnsUsage() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");

    SemanticUploadCommandHandler.UploadPreparation invalidMsgType =
        service.prepareUpload(room, "m.bad", "/tmp/photo.png", "caption");
    SemanticUploadCommandHandler.UploadPreparation blankPath =
        service.prepareUpload(room, "m.image", "   ", "caption");

    assertTrue(invalidMsgType.showUsage());
    assertEquals("", invalidMsgType.line());
    assertEquals("", invalidMsgType.statusMessage());

    assertTrue(blankPath.showUsage());
    assertEquals("", blankPath.line());
    assertEquals("", blankPath.statusMessage());
  }

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "matrix.example.org",
        443,
        true,
        "secret",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }
}
