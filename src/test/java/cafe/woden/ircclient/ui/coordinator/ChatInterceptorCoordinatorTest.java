package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.interceptors.InterceptorPanel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatInterceptorCoordinatorTest {

  @Test
  void bindSelectionCallbackDelegatesToTargetSelectionHandler() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicReference<TargetRef> selectedTarget = new AtomicReference<>();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> null,
            () -> {},
            selectedTarget::set,
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    Consumer<TargetRef> callback = captureSelectionCallback(interceptorPanel);
    TargetRef interceptorTarget = TargetRef.interceptor("libera", "audit");
    callback.accept(interceptorTarget);

    assertEquals(interceptorTarget, selectedTarget.get());
    disposables.dispose();
  }

  @Test
  void bindJumpCallbackDelegatesToHitMessageNavigator() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicReference<TargetRef> selectedTarget = new AtomicReference<>();
    AtomicReference<String> selectedMessageId = new AtomicReference<>();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> null,
            () -> {},
            ref -> {},
            (ref, msgId) -> {
              selectedTarget.set(ref);
              selectedMessageId.set(msgId);
            });
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    BiConsumer<TargetRef, String> callback = captureJumpCallback(interceptorPanel);
    TargetRef hitTarget = new TargetRef("libera", "#ircafe");
    callback.accept(hitTarget, "msg-123");

    assertEquals(hitTarget, selectedTarget.get());
    assertEquals("msg-123", selectedMessageId.get());
    disposables.dispose();
  }

  @Test
  void onActiveTargetChangedClearsInterceptorPanelWhenLeavingInterceptor() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> null,
            () -> {},
            ref -> {},
            (ref, msgId) -> {});

    coordinator.onActiveTargetChanged(
        TargetRef.interceptor("libera", "audit"), new TargetRef("libera", "#ircafe"));

    verify(interceptorPanel).setInterceptorTarget("", "", "");
  }

  @Test
  void onActiveTargetChangedDoesNotClearWhenNextTargetIsInterceptor() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> null,
            () -> {},
            ref -> {},
            (ref, msgId) -> {});

    coordinator.onActiveTargetChanged(
        TargetRef.interceptor("libera", "audit"), TargetRef.interceptor("libera", "other"));

    verify(interceptorPanel, never()).setInterceptorTarget(anyString(), anyString(), anyString());
  }

  @Test
  void bindLocalNameChangeCallbackRefreshesDockTitle() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicInteger dockTitleRefreshes = new AtomicInteger();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> TargetRef.interceptor("libera", "audit"),
            dockTitleRefreshes::incrementAndGet,
            ref -> {},
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    Runnable callback = captureLocalNameChangedCallback(interceptorPanel);
    callback.run();

    assertEquals(1, dockTitleRefreshes.get());
    disposables.dispose();
  }

  @Test
  void bindRefreshesActiveInterceptorViewWhenMatchingStoreChangeArrives() throws Exception {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicInteger dockTitleRefreshes = new AtomicInteger();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> TargetRef.interceptor("libera", "audit"),
            dockTitleRefreshes::incrementAndGet,
            ref -> {},
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    changes.onNext(new InterceptorStore.Change("libera", "audit"));
    flushEdt();

    assertEquals(1, dockTitleRefreshes.get());
    verify(interceptorPanel).setInterceptorTarget("libera", "", "audit");
    disposables.dispose();
  }

  @Test
  void bindSkipsCoordinatorRefreshForLocalDefinitionSaveChange() throws Exception {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    doReturn(true).when(interceptorPanel).consumeLocalDefinitionStoreChangeRefreshSkip();
    AtomicInteger dockTitleRefreshes = new AtomicInteger();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> TargetRef.interceptor("libera", "audit"),
            dockTitleRefreshes::incrementAndGet,
            ref -> {},
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    changes.onNext(new InterceptorStore.Change("libera", "audit"));
    flushEdt();

    assertEquals(0, dockTitleRefreshes.get());
    verify(interceptorPanel, never()).setInterceptorTarget(anyString(), anyString(), anyString());
    disposables.dispose();
  }

  @Test
  void bindIgnoresStoreChangesForDifferentInterceptors() throws Exception {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicInteger dockTitleRefreshes = new AtomicInteger();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> TargetRef.interceptor("libera", "audit"),
            dockTitleRefreshes::incrementAndGet,
            ref -> {},
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    changes.onNext(new InterceptorStore.Change("libera", "other"));
    changes.onNext(new InterceptorStore.Change("oftc", "audit"));
    flushEdt();

    assertEquals(0, dockTitleRefreshes.get());
    verify(interceptorPanel, never()).setInterceptorTarget(anyString(), anyString(), anyString());
    disposables.dispose();
  }

  @Test
  void bindMatchesQualifiedInterceptorScopeServerId() throws Exception {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    FlowableProcessor<InterceptorStore.Change> changes =
        PublishProcessor.<InterceptorStore.Change>create().toSerialized();
    when(interceptorStore.changes()).thenReturn(changes.onBackpressureBuffer());
    AtomicInteger dockTitleRefreshes = new AtomicInteger();

    ChatInterceptorCoordinator coordinator =
        new ChatInterceptorCoordinator(
            interceptorStore,
            interceptorPanel,
            () -> TargetRef.interceptor("quassel", "audit", "libera"),
            dockTitleRefreshes::incrementAndGet,
            ref -> {},
            (ref, msgId) -> {});
    CompositeDisposable disposables = new CompositeDisposable();

    coordinator.bind(disposables);
    changes.onNext(new InterceptorStore.Change("quassel{net:libera}", "audit"));
    flushEdt();

    assertEquals(1, dockTitleRefreshes.get());
    verify(interceptorPanel).setInterceptorTarget("quassel", "libera", "audit");
    disposables.dispose();
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      return;
    }
    SwingUtilities.invokeAndWait(() -> {});
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Consumer<TargetRef> captureSelectionCallback(InterceptorPanel interceptorPanel) {
    ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(interceptorPanel).setOnSelectTarget(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static BiConsumer<TargetRef, String> captureJumpCallback(
      InterceptorPanel interceptorPanel) {
    ArgumentCaptor<BiConsumer> captor = ArgumentCaptor.forClass(BiConsumer.class);
    verify(interceptorPanel).setOnJumpToMessage(captor.capture());
    return captor.getValue();
  }

  private static Runnable captureLocalNameChangedCallback(InterceptorPanel interceptorPanel) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(interceptorPanel).setOnLocalDefinitionNameChanged(captor.capture());
    return captor.getValue();
  }
}
