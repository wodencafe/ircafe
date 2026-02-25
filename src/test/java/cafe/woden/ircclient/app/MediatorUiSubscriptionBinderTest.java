package cafe.woden.ircclient.app;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MediatorUiSubscriptionBinderTest {

  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void joinDetachAndCloseChannelRequestsRouteToTargetCoordinator() {
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);

    PublishProcessor<TargetRef> joinRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> detachRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> closeChannelRequests = PublishProcessor.create();

    when(ui.targetSelections()).thenReturn(Flowable.never());
    when(ui.targetActivations()).thenReturn(Flowable.never());
    when(ui.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(ui.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(ui.outboundLines()).thenReturn(Flowable.never());
    when(ui.closeTargetRequests()).thenReturn(Flowable.never());
    when(ui.joinChannelRequests()).thenReturn(joinRequests);
    when(ui.detachChannelRequests()).thenReturn(detachRequests);
    when(ui.closeChannelRequests()).thenReturn(closeChannelRequests);
    when(ui.clearLogRequests()).thenReturn(Flowable.never());

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(ui, targetCoordinator, disposables, req -> {}, line -> {});

    TargetRef channel = new TargetRef("libera", "#ircafe");
    joinRequests.onNext(channel);
    detachRequests.onNext(channel);
    closeChannelRequests.onNext(channel);

    verify(targetCoordinator, timeout(1_000)).joinChannel(channel);
    verify(targetCoordinator, timeout(1_000)).detachChannel(channel);
    verify(targetCoordinator, timeout(1_000)).closeChannel(channel);
  }
}
