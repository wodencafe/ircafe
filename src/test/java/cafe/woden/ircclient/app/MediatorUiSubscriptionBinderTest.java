package cafe.woden.ircclient.app;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.function.Consumer;
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
    PublishProcessor<TargetRef> bouncerDetachRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> closeChannelRequests = PublishProcessor.create();

    when(ui.targetSelections()).thenReturn(Flowable.never());
    when(ui.targetActivations()).thenReturn(Flowable.never());
    when(ui.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(ui.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(ui.outboundLines()).thenReturn(Flowable.never());
    when(ui.backendNamedCommandRequests()).thenReturn(Flowable.never());
    when(ui.closeTargetRequests()).thenReturn(Flowable.never());
    when(ui.joinChannelRequests()).thenReturn(joinRequests);
    when(ui.disconnectChannelRequests()).thenReturn(detachRequests);
    when(ui.bouncerDetachChannelRequests()).thenReturn(bouncerDetachRequests);
    when(ui.closeChannelRequests()).thenReturn(closeChannelRequests);
    when(ui.clearLogRequests()).thenReturn(Flowable.never());

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(ui, targetCoordinator, disposables, req -> {}, line -> {}, cmd -> {});

    TargetRef channel = new TargetRef("libera", "#ircafe");
    joinRequests.onNext(channel);
    detachRequests.onNext(channel);
    bouncerDetachRequests.onNext(channel);
    closeChannelRequests.onNext(channel);

    verify(targetCoordinator, timeout(1_000)).joinChannel(channel);
    verify(targetCoordinator, timeout(1_000)).disconnectChannel(channel);
    verify(targetCoordinator, timeout(1_000)).bouncerDetachChannel(channel);
    verify(targetCoordinator, timeout(1_000)).closeChannel(channel);
  }

  @Test
  void quasselNetworkManagerRequestsRouteToProvidedHandler() {
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    @SuppressWarnings("unchecked")
    Consumer<ParsedInput.BackendNamed> onBackendNamedCommandRequest = Mockito.mock(Consumer.class);

    PublishProcessor<ParsedInput.BackendNamed> backendNamedRequests = PublishProcessor.create();

    when(ui.targetSelections()).thenReturn(Flowable.never());
    when(ui.targetActivations()).thenReturn(Flowable.never());
    when(ui.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(ui.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(ui.outboundLines()).thenReturn(Flowable.never());
    when(ui.backendNamedCommandRequests()).thenReturn(backendNamedRequests);
    when(ui.closeTargetRequests()).thenReturn(Flowable.never());
    when(ui.joinChannelRequests()).thenReturn(Flowable.never());
    when(ui.disconnectChannelRequests()).thenReturn(Flowable.never());
    when(ui.bouncerDetachChannelRequests()).thenReturn(Flowable.never());
    when(ui.closeChannelRequests()).thenReturn(Flowable.never());
    when(ui.clearLogRequests()).thenReturn(Flowable.never());

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(
        ui, targetCoordinator, disposables, req -> {}, line -> {}, onBackendNamedCommandRequest);

    backendNamedRequests.onNext(
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel"));

    verify(onBackendNamedCommandRequest, timeout(1_000))
        .accept(
            new ParsedInput.BackendNamed(
                BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel"));
  }

  @Test
  void quasselNetworkManagerRequestErrorsAreReportedToUi() {
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    TargetRef status = new TargetRef("quassel", "status");

    when(ui.targetSelections()).thenReturn(Flowable.never());
    when(ui.targetActivations()).thenReturn(Flowable.never());
    when(ui.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(ui.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(ui.outboundLines()).thenReturn(Flowable.never());
    when(ui.backendNamedCommandRequests())
        .thenReturn(Flowable.error(new IllegalStateException("boom")));
    when(ui.closeTargetRequests()).thenReturn(Flowable.never());
    when(ui.joinChannelRequests()).thenReturn(Flowable.never());
    when(ui.disconnectChannelRequests()).thenReturn(Flowable.never());
    when(ui.bouncerDetachChannelRequests()).thenReturn(Flowable.never());
    when(ui.closeChannelRequests()).thenReturn(Flowable.never());
    when(ui.clearLogRequests()).thenReturn(Flowable.never());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(ui, targetCoordinator, disposables, req -> {}, line -> {}, cmd -> {});

    verify(ui, timeout(1_000))
        .appendError(status, "(ui-error)", "java.lang.IllegalStateException: boom");
  }

  @Test
  void quasselSetupRequestsRouteToProvidedHandler() {
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    @SuppressWarnings("unchecked")
    Consumer<ParsedInput.BackendNamed> onBackendNamedCommandRequest = Mockito.mock(Consumer.class);

    PublishProcessor<ParsedInput.BackendNamed> backendNamedRequests = PublishProcessor.create();

    when(ui.targetSelections()).thenReturn(Flowable.never());
    when(ui.targetActivations()).thenReturn(Flowable.never());
    when(ui.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(ui.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(ui.outboundLines()).thenReturn(Flowable.never());
    when(ui.backendNamedCommandRequests()).thenReturn(backendNamedRequests);
    when(ui.closeTargetRequests()).thenReturn(Flowable.never());
    when(ui.joinChannelRequests()).thenReturn(Flowable.never());
    when(ui.disconnectChannelRequests()).thenReturn(Flowable.never());
    when(ui.bouncerDetachChannelRequests()).thenReturn(Flowable.never());
    when(ui.closeChannelRequests()).thenReturn(Flowable.never());
    when(ui.clearLogRequests()).thenReturn(Flowable.never());

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(
        ui, targetCoordinator, disposables, req -> {}, line -> {}, onBackendNamedCommandRequest);

    backendNamedRequests.onNext(
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"));

    verify(onBackendNamedCommandRequest, timeout(1_000))
        .accept(new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"));
  }
}
