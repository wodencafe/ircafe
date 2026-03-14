package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableMap;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxBridgeListenerBouncerDiscoveryTest {

  @Test
  void sojuAdapterWinsBeforeGenericFallbackWhenEnabled() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    BouncerDiscoveryEventPort bouncerEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxBridgeListener listener = newListener(conn, bus, true, false, bouncerEvents);

    listener.onUnknown(
        unknownEvent(":bouncer.example BOUNCER NETWORK 123 name=Libera;caps=message-tags"));

    verify(bouncerEvents)
        .onNetworkDiscovered(
            argThat(
                network ->
                    "soju".equals(network.backendId())
                        && "123".equals(network.networkId())
                        && "Libera".equals(network.displayName())));
    assertTrue(conn.sojuNetworksByNetId.containsKey("123"));
    assertTrue(conn.genericBouncerNetworksById.isEmpty());
  }

  @Test
  void genericFallbackCapturesBouncerLineWhenSojuAdapterDisabled() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    BouncerDiscoveryEventPort bouncerEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxBridgeListener listener = newListener(conn, bus, false, false, bouncerEvents);

    listener.onUnknown(
        unknownEvent(
            ":bouncer.example BOUNCER NETWORK NetA name=Libera;loginUser=alice/lib;caps=message-tags"));

    verify(bouncerEvents)
        .onNetworkDiscovered(
            argThat(
                network ->
                    "generic".equals(network.backendId())
                        && "NetA".equals(network.networkId())
                        && "alice/lib".equals(network.loginUserHint())
                        && network.hasCapability("message-tags")));
    assertTrue(conn.sojuNetworksByNetId.isEmpty());
    assertTrue(conn.genericBouncerNetworksById.containsKey("neta"));
  }

  @Test
  void backendTaggedAsSojuIsIgnoredWhenSojuDiscoveryDisabled() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    BouncerDiscoveryEventPort bouncerEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxBridgeListener listener = newListener(conn, bus, false, false, bouncerEvents);

    listener.onUnknown(
        unknownEvent(":bouncer.example BOUNCER NETWORK 123 backend=soju;name=Libera"));

    verify(bouncerEvents, never()).onNetworkDiscovered(any());
    assertTrue(conn.sojuNetworksByNetId.isEmpty());
    assertTrue(conn.genericBouncerNetworksById.isEmpty());
  }

  private static PircbotxBridgeListener newListener(
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      BouncerDiscoveryEventPort bouncerEvents) {
    return new PircbotxBridgeListenerFactory(
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            bouncerEvents,
            new NoOpPlaybackCursorProvider(),
            new ServerIsupportState(),
            new SojuProperties(Map.of(), new SojuProperties.Discovery(sojuDiscoveryEnabled)),
            new ZncProperties(Map.of(), new ZncProperties.Discovery(zncDiscoveryEnabled)))
        .create(
            "libera",
            conn,
            bus,
            c -> {},
            (c, reason) -> {},
            (bot, fromNick, message) -> false,
            false);
  }

  private static UnknownEvent unknownEvent(String rawLine) {
    return new UnknownEvent(
        null, "*", "bouncer.example", "NOTICE", rawLine, List.of("*"), ImmutableMap.of());
  }
}
