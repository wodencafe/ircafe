package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.hooks.events.TopicEvent;

class PircbotxTopicEventEmitterTest {

  @Test
  void onTopicEmitsChannelTopicUpdated() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxTopicEventEmitter emitter = new PircbotxTopicEventEmitter("libera", events::add);
    TopicEvent event = mock(TopicEvent.class);
    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn("#ircafe");
    when(event.getChannel()).thenReturn(channel);
    when(event.getTopic()).thenReturn("new topic");

    emitter.onTopic(event);

    assertEquals(1, events.size());
    IrcEvent.ChannelTopicUpdated updated =
        assertInstanceOf(IrcEvent.ChannelTopicUpdated.class, events.getFirst().event());
    assertEquals("#ircafe", updated.channel());
    assertEquals("new topic", updated.topic());
  }

  @Test
  void onTopicIgnoresBlankChannel() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxTopicEventEmitter emitter = new PircbotxTopicEventEmitter("libera", events::add);
    TopicEvent event = mock(TopicEvent.class);
    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(" ");
    when(event.getChannel()).thenReturn(channel);

    emitter.onTopic(event);

    assertTrue(events.isEmpty());
  }
}
