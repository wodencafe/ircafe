package cafe.woden.ircclient.app.outbound.identity;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /nick and /away command flow. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundNickAwayCommandService {

  @NonNull private final NickCommandSupport nickCommandSupport;
  @NonNull private final AwayCommandSupport awayCommandSupport;

  public void handleNick(CompositeDisposable disposables, String newNick) {
    nickCommandSupport.handleNick(disposables, newNick);
  }

  public void handleAway(CompositeDisposable disposables, String message) {
    awayCommandSupport.handleAway(disposables, message);
  }
}
