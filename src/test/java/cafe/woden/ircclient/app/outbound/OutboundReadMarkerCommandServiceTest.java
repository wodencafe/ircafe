package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundReadMarkerCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(
          List.of(
              new MatrixOutboundBackendFeatureAdapter(),
              new QuasselOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          IrcNegotiatedFeaturePort.from(irc),
          irc);
  private final OutboundReadMarkerCommandService service =
      new OutboundReadMarkerCommandService(
          IrcReadMarkerPort.from(irc),
          outboundBackendCapabilityPolicy,
          ui,
          connectionCoordinator,
          targetCoordinator);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void markReadSendsReadMarkerAndClearsUnreadForActiveConversation() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class)))
        .thenReturn(Completable.complete());

    service.handleMarkRead(disposables);

    verify(ui).setReadMarker(eq(chan), anyLong());
    verify(ui).clearUnread(chan);
    verify(irc).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadShowsCapabilityHintWhenReadMarkerUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "read-marker is not negotiated on this server.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadShowsBackendAvailabilityReasonWhenProvided() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "Quassel Core backend is not implemented yet.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadFallsBackToNegotiationReasonWhenBackendHasNoSpecificReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "read-marker is not negotiated on this server.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }
}
