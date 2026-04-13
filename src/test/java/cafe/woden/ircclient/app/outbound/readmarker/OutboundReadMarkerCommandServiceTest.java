package cafe.woden.ircclient.app.outbound.readmarker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ReadMarkerFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.*;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundCommandAvailabilitySupport;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundReadMarkerCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.builtInOutboundBackendFeatureRegistry();
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          IrcNegotiatedFeaturePort.from(irc),
          irc,
          cafe.woden.ircclient.app.api.AvailableBackendIdsPort.builtInsOnly());
  private final OutboundCommandAvailabilitySupport outboundCommandAvailabilitySupport =
      new OutboundCommandAvailabilitySupport(outboundBackendCapabilityPolicy);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final Ircv3ReadMarkerFeatureSupport readMarkerFeatureSupport =
      new Ircv3ReadMarkerFeatureSupport(
          IrcReadMarkerPort.from(irc),
          outboundBackendCapabilityPolicy,
          new Ircv3CapabilityNameResolverPort() {});
  private final OutboundReadMarkerCommandService service =
      new OutboundReadMarkerCommandService(
          readMarkerFeatureSupport,
          outboundCommandAvailabilitySupport,
          outboundConnectionStatusSupport,
          ui,
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
