package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

public interface IrcClientService {
  Flowable<IrcEvent> events();
  java.util.Optional<String> currentNick();

  Completable connect();
  Completable disconnect();

  Completable changeNick(String newNick);

  Completable requestNames(String channel);
  Completable joinChannel(String channel);
  Completable sendToChannel(String channel, String message);
}
