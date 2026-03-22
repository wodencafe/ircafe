package cafe.woden.ircclient.app.outbound.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.OutboundRawCommandSupport;
import cafe.woden.ircclient.app.outbound.OutboundRawLineCorrelationService;
import cafe.woden.ircclient.app.outbound.backend.*;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundUploadCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(List.of(new MatrixOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          IrcNegotiatedFeaturePort.from(irc),
          irc);
  private final MatrixOutboundCommandSupport matrixCommandSupport =
      new MatrixOutboundCommandSupport();
  private final BackendUploadCommandRegistry backendUploadCommandRegistry =
      new BackendUploadCommandRegistry(
          List.of(new MatrixUploadCommandTranslationHandler(matrixCommandSupport)));
  private final MatrixOutboundCommandService matrixOutboundCommandService =
      new MatrixOutboundCommandService(
          ui, outboundBackendCapabilityPolicy, matrixCommandSupport, backendUploadCommandRegistry);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(
          outboundBackendCapabilityPolicy, labeledResponseRoutingState);
  private final OutboundRawCommandSupport rawCommandSupport =
      new OutboundRawCommandSupport(rawLineCorrelationService);
  private final OutboundUploadCommandService service =
      new OutboundUploadCommandService(
          IrcTargetMembershipPort.from(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          matrixOutboundCommandService,
          rawCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void uploadSendsTaggedPrivmsgOnMatrixBackend() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.sendRaw(
            "matrix",
            "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/photo.png PRIVMSG !room:example.org :hello image"))
        .thenReturn(Completable.complete());

    service.handleUpload(disposables, "image", "/tmp/photo.png", "hello image");

    verify(irc)
        .sendRaw(
            "matrix",
            "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/photo.png PRIVMSG !room:example.org :hello image");
  }

  @Test
  void uploadDefaultsCaptionToFileNameWhenCaptionBlank() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.sendRaw(
            "matrix",
            "@+matrix/msgtype=m.file;+matrix/upload_path=/tmp/My\\sFile.txt PRIVMSG !room:example.org :My File.txt"))
        .thenReturn(Completable.complete());

    service.handleUpload(disposables, "m.file", "/tmp/My File.txt", "");

    verify(irc)
        .sendRaw(
            "matrix",
            "@+matrix/msgtype=m.file;+matrix/upload_path=/tmp/My\\sFile.txt PRIVMSG !room:example.org :My File.txt");
  }

  @Test
  void uploadOnNonMatrixBackendShowsUnsupportedMessageAndDoesNotSendRaw() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithBackend("libera", IrcProperties.Server.Backend.IRC)));

    service.handleUpload(disposables, "m.image", "/tmp/photo.png", "hello");

    verify(ui)
        .appendStatus(eq(status), eq("(upload)"), contains("does not use the Matrix backend"));
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void uploadWithInvalidMsgTypeShowsUsage() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);

    service.handleUpload(disposables, "m.bad", "/tmp/photo.png", "");

    verify(ui).appendStatus(room, "(upload)", "Usage: /upload <msgtype> <path> [caption]");
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void uploadHelpDelegatesToMatrixHelp() {
    TargetRef out = new TargetRef("matrix", "#help");

    service.appendUploadHelp(out);

    verify(ui)
        .appendStatus(
            out,
            "(help)",
            "/upload <m.image|m.file|m.video|m.audio> <path> [caption]  (msgtype shortcuts: image|file|video|audio)");
  }

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "core.example.net",
        4242,
        false,
        "",
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
