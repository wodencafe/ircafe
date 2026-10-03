package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QuasselCoreObservationMediatorTest {
  @Test
  void networkConfirmationRequiresTheCorrectServerAndObservationStream() {
    QuasselCoreObservationMediator mediator = new QuasselCoreObservationMediator();
    AtomicBoolean ready = new AtomicBoolean();
    var snapshots = mediator.networkEvents().test();
    var pending = mediator.whenNetwork(" first ", 5000, ready::get).test();
    ready.set(true);
    mediator.observeIdentity("first");
    mediator.observeNetwork(snapshot("second"));
    pending.assertNotComplete();
    mediator.observeNetwork(snapshot("first"));
    pending.assertComplete().assertNoErrors();
    snapshots.assertValues(snapshot("second"), snapshot("first"));
    snapshots.cancel();
  }

  @Test
  void identityConfirmationIgnoresNetworkAndOtherServerObservations() {
    QuasselCoreObservationMediator mediator = new QuasselCoreObservationMediator();
    AtomicBoolean ready = new AtomicBoolean();
    var pending = mediator.whenIdentity("first", 5000, ready::get).test();
    ready.set(true);
    mediator.observeNetwork(snapshot("first"));
    mediator.observeIdentity("second");
    pending.assertNotComplete();
    mediator.observeIdentity("first");
    pending.assertComplete().assertNoErrors();
  }

  @Test
  void conditionIsEvaluatedAtSubscriptionAndSatisfiedStateNeedsNoFutureObservation() {
    QuasselCoreObservationMediator mediator = new QuasselCoreObservationMediator();
    AtomicBoolean ready = new AtomicBoolean();
    var confirmation = mediator.whenNetwork("first", 5000, ready::get);
    ready.set(true);
    confirmation.test().assertComplete().assertNoErrors();
  }

  @Test
  void cancellationAndTimeoutReleaseTheirObservationSubscriptions() {
    QuasselCoreObservationMediator mediator = new QuasselCoreObservationMediator();
    AtomicInteger evaluations = new AtomicInteger();
    var cancelled =
        mediator
            .whenIdentity(
                "first",
                5000,
                () -> {
                  evaluations.incrementAndGet();
                  return false;
                })
            .test();
    cancelled.dispose();
    mediator.observeIdentity("first");
    assertEquals(1, evaluations.get());
    var timedOut =
        mediator
            .whenNetwork(
                "first",
                20,
                () -> {
                  evaluations.incrementAndGet();
                  return false;
                })
            .test();
    timedOut.awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors();
    mediator.observeNetwork(snapshot("first"));
    assertEquals(2, evaluations.get());
  }

  private static QuasselCoreNetworkSnapshotEvent snapshot(String serverId) {
    return new QuasselCoreNetworkSnapshotEvent(serverId, List.of(), "test");
  }
}
