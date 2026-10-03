package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Coordinates state observations with the workflows awaiting Core confirmation. */
final class QuasselCoreObservationMediator {
  private final FlowableProcessor<QuasselCoreNetworkSnapshotEvent> networks =
      PublishProcessor.<QuasselCoreNetworkSnapshotEvent>create().toSerialized();
  private final FlowableProcessor<String> identities =
      PublishProcessor.<String>create().toSerialized();

  Flowable<QuasselCoreNetworkSnapshotEvent> networkEvents() {
    return networks.onBackpressureBuffer();
  }

  void observeNetwork(QuasselCoreNetworkSnapshotEvent event) {
    networks.onNext(event);
  }

  void observeIdentity(String serverId) {
    identities.onNext(serverId);
  }

  Completable whenNetwork(String serverId, long timeoutMs, BooleanSupplier condition) {
    return whenObserved(
        networks.map(QuasselCoreNetworkSnapshotEvent::serverId), serverId, timeoutMs, condition);
  }

  Completable whenIdentity(String serverId, long timeoutMs, BooleanSupplier condition) {
    return whenObserved(identities, serverId, timeoutMs, condition);
  }

  private static Completable whenObserved(
      Flowable<String> observations, String serverId, long timeoutMs, BooleanSupplier condition) {
    return Completable.defer(
        () -> {
          if (condition == null || timeoutMs <= 0L) return Completable.complete();
          if (condition.getAsBoolean()) return Completable.complete();
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isEmpty()) return Completable.complete();
          return observations
              .filter(observed -> sid.equals(Objects.toString(observed, "").trim()))
              .filter(ignored -> condition.getAsBoolean())
              .firstElement()
              .timeout(timeoutMs, TimeUnit.MILLISECONDS)
              .ignoreElement()
              .onErrorComplete();
        });
  }
}
