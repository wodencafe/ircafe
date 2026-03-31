package cafe.woden.ircclient.app.core;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class IrcMediatorTickerTest {

  @Test
  void timeoutMaintenanceTickerDoesNotFailWhenEdtFallsBehind() {
    TestScheduler intervalScheduler = new TestScheduler();
    TestScheduler observeScheduler = new TestScheduler();

    var subscriber =
        IrcMediator.timeoutMaintenanceTicks(intervalScheduler, observeScheduler).test(0L);

    intervalScheduler.advanceTimeBy(11, TimeUnit.MINUTES);
    observeScheduler.triggerActions();

    subscriber.assertNoValues();
    subscriber.assertNoErrors();

    subscriber.requestMore(1);
    observeScheduler.triggerActions();

    subscriber.assertValueCount(1);
    subscriber.assertNoErrors();
  }

  @Test
  void offloadedInboundPreparationPreservesSourceOrder() {
    TestScheduler offloadScheduler = new TestScheduler();
    TestScheduler observeScheduler = new TestScheduler();

    var subscriber =
        IrcMediator.offloadSelectedEventProcessing(
                io.reactivex.rxjava3.core.Flowable.just("status", "channel", "action"),
                value -> !"status".equals(value),
                String::toUpperCase,
                offloadScheduler,
                observeScheduler)
            .test();

    observeScheduler.triggerActions();
    subscriber.assertValue("STATUS");

    offloadScheduler.triggerActions();
    observeScheduler.triggerActions();

    subscriber.assertValues("STATUS", "CHANNEL", "ACTION");
    subscriber.assertNoErrors();
  }
}
