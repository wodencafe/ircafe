package cafe.woden.ircclient.app;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.UiEventPort;
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
    UiEventPort uiEvents = Mockito.mock(UiEventPort.class);
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);

    PublishProcessor<TargetRef> joinRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> detachRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> bouncerDetachRequests = PublishProcessor.create();
    PublishProcessor<TargetRef> closeChannelRequests = PublishProcessor.create();

    stubIdleUiEvents(uiEvents);
    when(uiEvents.joinChannelRequests()).thenReturn(joinRequests);
    when(uiEvents.disconnectChannelRequests()).thenReturn(detachRequests);
    when(uiEvents.bouncerDetachChannelRequests()).thenReturn(bouncerDetachRequests);
    when(uiEvents.closeChannelRequests()).thenReturn(closeChannelRequests);

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(uiEvents, ui, targetCoordinator, disposables, req -> {}, line -> {}, cmd -> {});

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
    UiEventPort uiEvents = Mockito.mock(UiEventPort.class);
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    @SuppressWarnings("unchecked")
    Consumer<ParsedInput.BackendNamed> onBackendNamedCommandRequest = Mockito.mock(Consumer.class);

    PublishProcessor<ParsedInput.BackendNamed> backendNamedRequests = PublishProcessor.create();

    stubIdleUiEvents(uiEvents);
    when(uiEvents.backendNamedCommandRequests()).thenReturn(backendNamedRequests);

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(
        uiEvents,
        ui,
        targetCoordinator,
        disposables,
        req -> {},
        line -> {},
        onBackendNamedCommandRequest);

    backendNamedRequests.onNext(
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel"));

    verify(onBackendNamedCommandRequest, timeout(1_000))
        .accept(
            new ParsedInput.BackendNamed(
                BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel"));
  }

  @Test
  void quasselNetworkManagerRequestErrorsAreReportedToUi() {
    UiEventPort uiEvents = Mockito.mock(UiEventPort.class);
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    TargetRef status = new TargetRef("quassel", "status");

    stubIdleUiEvents(uiEvents);
    when(uiEvents.backendNamedCommandRequests())
        .thenReturn(Flowable.error(new IllegalStateException("boom")));
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(uiEvents, ui, targetCoordinator, disposables, req -> {}, line -> {}, cmd -> {});

    verify(ui, timeout(1_000))
        .appendError(status, "(ui-error)", "java.lang.IllegalStateException: boom");
  }

  @Test
  void quasselSetupRequestsRouteToProvidedHandler() {
    UiEventPort uiEvents = Mockito.mock(UiEventPort.class);
    UiPort ui = Mockito.mock(UiPort.class);
    TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
    @SuppressWarnings("unchecked")
    Consumer<ParsedInput.BackendNamed> onBackendNamedCommandRequest = Mockito.mock(Consumer.class);

    PublishProcessor<ParsedInput.BackendNamed> backendNamedRequests = PublishProcessor.create();

    stubIdleUiEvents(uiEvents);
    when(uiEvents.backendNamedCommandRequests()).thenReturn(backendNamedRequests);

    MediatorUiSubscriptionBinder binder = new MediatorUiSubscriptionBinder();
    binder.bind(
        uiEvents,
        ui,
        targetCoordinator,
        disposables,
        req -> {},
        line -> {},
        onBackendNamedCommandRequest);

    backendNamedRequests.onNext(
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"));

    verify(onBackendNamedCommandRequest, timeout(1_000))
        .accept(new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"));
  }

  private static void stubIdleUiEvents(UiEventPort uiEvents) {
    when(uiEvents.targetSelections()).thenReturn(Flowable.never());
    when(uiEvents.targetActivations()).thenReturn(Flowable.never());
    when(uiEvents.privateMessageRequests()).thenReturn(Flowable.<PrivateMessageRequest>never());
    when(uiEvents.userActionRequests()).thenReturn(Flowable.<UserActionRequest>never());
    when(uiEvents.outboundLines()).thenReturn(Flowable.never());
    when(uiEvents.connectClicks()).thenReturn(Flowable.never());
    when(uiEvents.disconnectClicks()).thenReturn(Flowable.never());
    when(uiEvents.connectServerRequests()).thenReturn(Flowable.never());
    when(uiEvents.disconnectServerRequests()).thenReturn(Flowable.never());
    when(uiEvents.backendNamedCommandRequests()).thenReturn(Flowable.never());
    when(uiEvents.closeTargetRequests()).thenReturn(Flowable.never());
    when(uiEvents.joinChannelRequests()).thenReturn(Flowable.never());
    when(uiEvents.disconnectChannelRequests()).thenReturn(Flowable.never());
    when(uiEvents.bouncerDetachChannelRequests()).thenReturn(Flowable.never());
    when(uiEvents.closeChannelRequests()).thenReturn(Flowable.never());
    when(uiEvents.clearLogRequests()).thenReturn(Flowable.never());
    when(uiEvents.ircv3CapabilityToggleRequests()).thenReturn(Flowable.never());
  }
}
