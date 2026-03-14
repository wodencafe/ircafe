package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.TopicEvent;

/** Emits structured topic-change events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxTopicEventEmitter {
  @NonNull private final String serverId;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  void onTopic(TopicEvent event) {
    if (event == null || event.getChannel() == null) return;
    String channel = event.getChannel().getName();
    String topic = event.getTopic();
    if (channel == null || channel.isBlank()) return;

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelTopicUpdated(Instant.now(), channel, topic)));
  }
}
