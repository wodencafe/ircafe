package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservedOutboundCommandDispatcherTest {

  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void delegatesSuccessfulDispatch() {
    StubDispatcher delegate = new StubDispatcher();
    ObservedOutboundCommandDispatcher observed = new ObservedOutboundCommandDispatcher(delegate);

    observed.dispatch(disposables, new ParsedInput.Say("hello"));

    assertEquals(1, delegate.calls);
  }

  @Test
  void rethrowsDispatchFailure() {
    StubDispatcher delegate = new StubDispatcher();
    delegate.throwOnDispatch = true;
    ObservedOutboundCommandDispatcher observed = new ObservedOutboundCommandDispatcher(delegate);

    assertThrows(IllegalStateException.class, () -> observed.dispatch(disposables, new ParsedInput.Say("hello")));

    assertEquals(1, delegate.calls);
  }

  private static final class StubDispatcher implements OutboundCommandDispatcher {
    int calls = 0;
    boolean throwOnDispatch = false;

    @Override
    public void dispatch(CompositeDisposable disposables, ParsedInput input) {
      calls++;
      if (throwOnDispatch) throw new IllegalStateException("boom");
    }
  }
}
