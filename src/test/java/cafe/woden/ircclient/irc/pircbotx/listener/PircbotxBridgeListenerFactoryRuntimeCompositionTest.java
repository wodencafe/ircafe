package cafe.woden.ircclient.irc.pircbotx.listener;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3IsupportRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3SaslRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3ServerTimeRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3TypingRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3ZncPlaybackRuntimeSupport;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxActionEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChannelMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxInviteEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxNoticeEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateConversationSupport;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxUnknownCtcpEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxPresenceSignalSupport;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.state.ServerIsupportState;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PircbotxBridgeListenerFactoryRuntimeCompositionTest {

  @Test
  void reusesFactoryOwnedRuntimeAdaptersAcrossConnectionListeners() throws Exception {
    PircbotxBridgeListenerFactory factory =
        new PircbotxBridgeListenerFactory(
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            null,
            new NoOpPlaybackCursorProvider(),
            new ServerIsupportState(),
            new SojuProperties(Map.of(), new SojuProperties.Discovery(false)),
            new ZncProperties(Map.of(), new ZncProperties.Discovery(false)),
            mock(Ircv3InboundCommandSignalRuntimeCatalog.class),
            mock(Ircv3InboundTagSignalRuntimeCatalog.class),
            mock(Ircv3OutboundCommandRuntimeCatalog.class),
            mock(Ircv3ServerTimeRuntimeSupport.class),
            mock(Ircv3MessageTagsRuntimeSupport.class));

    Ircv3InboundCommandSignalRuntimeCatalog inboundCommands =
        field(
            factory, "inboundCommandRuntimeCatalog", Ircv3InboundCommandSignalRuntimeCatalog.class);
    Ircv3OutboundCommandRuntimeCatalog outboundCommands =
        field(factory, "outboundCommandRuntimeCatalog", Ircv3OutboundCommandRuntimeCatalog.class);
    Ircv3ServerTimeRuntimeSupport serverTime =
        field(factory, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class);
    Ircv3MessageTagsRuntimeSupport messageTags =
        field(factory, "messageTagsRuntimeSupport", Ircv3MessageTagsRuntimeSupport.class);
    Ircv3ZncPlaybackRuntimeSupport zncPlayback =
        field(factory, "zncPlaybackRuntimeSupport", Ircv3ZncPlaybackRuntimeSupport.class);
    Ircv3IsupportRuntimeSupport isupport =
        field(factory, "isupportRuntimeSupport", Ircv3IsupportRuntimeSupport.class);
    Ircv3TypingRuntimeSupport typing =
        field(factory, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class);
    Ircv3SaslRuntimeSupport sasl =
        field(factory, "saslRuntimeSupport", Ircv3SaslRuntimeSupport.class);

    PircbotxBridgeListener first = createListener(factory, "first");
    PircbotxBridgeListener second = createListener(factory, "second");

    assertNotSame(first, second);
    assertRuntimeComposition(
        first,
        inboundCommands,
        outboundCommands,
        serverTime,
        messageTags,
        zncPlayback,
        isupport,
        typing,
        sasl);
    assertRuntimeComposition(
        second,
        inboundCommands,
        outboundCommands,
        serverTime,
        messageTags,
        zncPlayback,
        isupport,
        typing,
        sasl);
  }

  @Test
  void eventEmittersExposeOnlyExplicitRuntimeConstructors() {
    assertExplicitRuntimeConstructors(PircbotxChannelMessageEmitter.class, false);
    assertExplicitRuntimeConstructors(PircbotxPrivateMessageEmitter.class, true);
    assertExplicitRuntimeConstructors(PircbotxActionEventEmitter.class, true);
    assertExplicitRuntimeConstructors(PircbotxNoticeEventEmitter.class, false);
    assertExplicitRuntimeConstructors(PircbotxServerResponseEmitter.class, false);
  }

  @Test
  void eventTranslatorHelpersExposeOnlyExplicitRuntimeBoundaries() {
    assertConstructorCount(PircbotxMonitorEventEmitter.class, 1);
    assertAllConstructorsRequire(
        PircbotxMonitorEventEmitter.class,
        Ircv3InboundCommandSignalRuntimeCatalog.class,
        Ircv3ServerTimeRuntimeSupport.class);
    assertConstructorCount(PircbotxInviteEventEmitter.class, 1);
    assertAllConstructorsRequire(
        PircbotxInviteEventEmitter.class, Ircv3InboundCommandSignalRuntimeCatalog.class);
    assertConstructorCount(PircbotxWhoEventEmitter.class, 1);
    assertAllConstructorsRequire(
        PircbotxWhoEventEmitter.class, Ircv3InboundCommandSignalRuntimeCatalog.class);
    assertConstructorCount(PircbotxUnknownCtcpEmitter.class, 1);
    assertAllConstructorsRequire(
        PircbotxUnknownCtcpEmitter.class, Ircv3ServerTimeRuntimeSupport.class);
    assertConstructorCount(PircbotxInboundCtcpHandler.class, 1);
    assertAllConstructorsRequire(
        PircbotxInboundCtcpHandler.class, Ircv3ServerTimeRuntimeSupport.class);
  }

  @Test
  void eventPipelineHelpersExposeOnlyExplicitRuntimeBoundaries() {
    assertConstructorCount(PircbotxChatHistoryBatchCollector.class, 1);
    assertAllConstructorsRequire(
        PircbotxChatHistoryBatchCollector.class,
        Ircv3InboundCommandSignalRuntimeCatalog.class,
        Ircv3InboundTagSignalRuntimeCatalog.class,
        Ircv3ServerTimeRuntimeSupport.class,
        Ircv3MessageTagsRuntimeSupport.class);
    assertConstructorCount(PircbotxPrivateConversationSupport.class, 1);
    assertAllConstructorsRequire(
        PircbotxPrivateConversationSupport.class, Ircv3ZncPlaybackRuntimeSupport.class);
    assertAllConstructorsRequire(
        PircbotxUnknownEventRouter.class,
        Ircv3ServerTimeRuntimeSupport.class,
        Ircv3InboundCommandSignalRuntimeCatalog.class);
    assertAllConstructorsRequire(
        PircbotxServerNumericRouter.class, PircbotxPresenceSignalSupport.class);
    assertAllConstructorsRequire(
        PircbotxUnknownLineFallbackHandler.class,
        Ircv3ServerTimeRuntimeSupport.class,
        Ircv3MessageTagsRuntimeSupport.class,
        PircbotxPresenceSignalSupport.class);
    assertMetadataMethodsRequireRuntimeSupport();
  }

  @Test
  void listenerLifecycleHelpersExposeOnlyExplicitRuntimeBoundaries() {
    assertConstructorCount(PircbotxRegistrationLifecycleHandler.class, 1);
    assertAllConstructorsRequire(
        PircbotxRegistrationLifecycleHandler.class,
        Ircv3OutboundCommandRuntimeCatalog.class,
        Ircv3ZncPlaybackRuntimeSupport.class);
    assertConstructorCount(PircbotxIsupportObserver.class, 1);
    assertAllConstructorsRequire(
        PircbotxIsupportObserver.class,
        Ircv3IsupportRuntimeSupport.class,
        Ircv3TypingRuntimeSupport.class);
    assertConstructorCount(PircbotxSaslFailureHandler.class, 1);
    assertAllConstructorsRequire(PircbotxSaslFailureHandler.class, Ircv3SaslRuntimeSupport.class);
  }

  private static PircbotxBridgeListener createListener(
      PircbotxBridgeListenerFactory factory, String serverId) {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    return (PircbotxBridgeListener)
        factory.create(
            serverId,
            new PircbotxConnectionState(serverId),
            bus,
            ignored -> {},
            (ignored, reason) -> {},
            (bot, fromNick, message) -> false,
            false);
  }

  private static void assertRuntimeComposition(
      PircbotxBridgeListener listener,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommands,
      Ircv3OutboundCommandRuntimeCatalog outboundCommands,
      Ircv3ServerTimeRuntimeSupport serverTime,
      Ircv3MessageTagsRuntimeSupport messageTags,
      Ircv3ZncPlaybackRuntimeSupport zncPlayback,
      Ircv3IsupportRuntimeSupport isupport,
      Ircv3TypingRuntimeSupport typing,
      Ircv3SaslRuntimeSupport sasl)
      throws Exception {
    assertEmitterRuntime(listener, "serverResponses", serverTime, messageTags, null);
    assertEmitterRuntime(listener, "channelMessageEvents", serverTime, messageTags, null);
    assertEmitterRuntime(listener, "privateMessageEvents", serverTime, messageTags, zncPlayback);
    assertEmitterRuntime(listener, "actionEvents", serverTime, messageTags, zncPlayback);
    assertEmitterRuntime(listener, "noticeEvents", serverTime, messageTags, null);

    Object monitorEvents = field(listener, "monitorEvents", Object.class);
    assertSame(
        inboundCommands,
        field(monitorEvents, "runtimeCatalog", Ircv3InboundCommandSignalRuntimeCatalog.class));
    assertSame(
        serverTime,
        field(monitorEvents, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));

    Object inviteEvents = field(listener, "inviteEvents", Object.class);
    assertSame(
        inboundCommands,
        field(
            inviteEvents,
            "inboundCommandRuntimeCatalog",
            Ircv3InboundCommandSignalRuntimeCatalog.class));

    Object whoEvents = field(listener, "whoEvents", Object.class);
    assertSame(
        inboundCommands,
        field(whoEvents, "runtimeCatalog", Ircv3InboundCommandSignalRuntimeCatalog.class));

    Object unknownCtcp = field(listener, "unknownCtcp", Object.class);
    assertSame(
        serverTime,
        field(unknownCtcp, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));

    Object inboundCtcpHandler = field(listener, "inboundCtcpHandler", Object.class);
    assertSame(
        serverTime,
        field(inboundCtcpHandler, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));

    Object historyBatches = field(listener, "chatHistoryBatches", Object.class);
    assertSame(
        inboundCommands,
        field(
            historyBatches,
            "inboundCommandRuntimeCatalog",
            Ircv3InboundCommandSignalRuntimeCatalog.class));
    assertSame(
        serverTime,
        field(historyBatches, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));
    assertSame(
        messageTags,
        field(historyBatches, "messageTagsRuntimeSupport", Ircv3MessageTagsRuntimeSupport.class));

    Object unknownLineFallback = field(listener, "unknownLineFallback", Object.class);
    assertSame(
        serverTime,
        field(
            unknownLineFallback, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));
    assertSame(
        messageTags,
        field(
            unknownLineFallback,
            "messageTagsRuntimeSupport",
            Ircv3MessageTagsRuntimeSupport.class));
    PircbotxPrivateConversationSupport unknownPrivateConversation =
        field(
            unknownLineFallback,
            "privateConversationSupport",
            PircbotxPrivateConversationSupport.class);
    assertSame(
        zncPlayback,
        field(
            unknownPrivateConversation,
            "zncPlaybackRuntimeSupport",
            Ircv3ZncPlaybackRuntimeSupport.class));

    Object unknownEventRouter = field(listener, "unknownEventRouter", Object.class);
    assertSame(
        inboundCommands,
        field(
            unknownEventRouter,
            "inboundCommandRuntimeCatalog",
            Ircv3InboundCommandSignalRuntimeCatalog.class));
    assertSame(
        serverTime,
        field(unknownEventRouter, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));

    Object serverNumericRouter = field(listener, "serverNumericRouter", Object.class);
    assertSame(
        field(unknownLineFallback, "presenceSignals", PircbotxPresenceSignalSupport.class),
        field(serverNumericRouter, "presenceSignals", PircbotxPresenceSignalSupport.class));

    Object registrationLifecycle = field(listener, "registrationLifecycle", Object.class);
    assertSame(
        outboundCommands,
        field(
            registrationLifecycle,
            "outboundCommandRuntimeCatalog",
            Ircv3OutboundCommandRuntimeCatalog.class));
    assertSame(
        zncPlayback,
        field(
            registrationLifecycle,
            "zncPlaybackRuntimeSupport",
            Ircv3ZncPlaybackRuntimeSupport.class));

    Object isupportObserver = field(listener, "isupportObserver", Object.class);
    assertSame(
        isupport,
        field(isupportObserver, "isupportRuntimeSupport", Ircv3IsupportRuntimeSupport.class));
    assertSame(
        typing, field(isupportObserver, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class));

    Object saslFailures = field(listener, "saslFailures", Object.class);
    assertSame(sasl, field(saslFailures, "runtimeSupport", Ircv3SaslRuntimeSupport.class));
  }

  private static void assertEmitterRuntime(
      PircbotxBridgeListener listener,
      String emitterField,
      Ircv3ServerTimeRuntimeSupport serverTime,
      Ircv3MessageTagsRuntimeSupport messageTags,
      Ircv3ZncPlaybackRuntimeSupport zncPlayback)
      throws Exception {
    Object emitter = field(listener, emitterField, Object.class);
    assertSame(
        serverTime,
        field(emitter, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));
    assertSame(
        messageTags,
        field(emitter, "messageTagsRuntimeSupport", Ircv3MessageTagsRuntimeSupport.class));
    if (zncPlayback != null) {
      PircbotxPrivateConversationSupport privateConversation =
          field(emitter, "privateConversationSupport", PircbotxPrivateConversationSupport.class);
      assertSame(
          zncPlayback,
          field(
              privateConversation,
              "zncPlaybackRuntimeSupport",
              Ircv3ZncPlaybackRuntimeSupport.class));
    }
  }

  private static void assertExplicitRuntimeConstructors(
      Class<?> emitterType, boolean requiresZncPlayback) {
    Constructor<?>[] publicConstructors =
        Arrays.stream(emitterType.getDeclaredConstructors())
            .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
            .toArray(Constructor<?>[]::new);
    assertTrue(publicConstructors.length > 0, emitterType.getSimpleName());
    for (Constructor<?> constructor : publicConstructors) {
      List<Class<?>> parameterTypes = List.of(constructor.getParameterTypes());
      assertTrue(
          parameterTypes.contains(Ircv3ServerTimeRuntimeSupport.class), constructor.toString());
      assertTrue(
          parameterTypes.contains(Ircv3MessageTagsRuntimeSupport.class), constructor.toString());
      if (requiresZncPlayback) {
        assertTrue(
            parameterTypes.contains(Ircv3ZncPlaybackRuntimeSupport.class)
                || parameterTypes.contains(PircbotxPrivateConversationSupport.class),
            constructor.toString());
      }
    }
  }

  private static void assertConstructorCount(Class<?> type, int expectedCount) {
    assertTrue(
        type.getDeclaredConstructors().length == expectedCount,
        type.getSimpleName() + " constructor count");
  }

  private static void assertAllConstructorsRequire(
      Class<?> type, Class<?>... requiredParameterTypes) {
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      List<Class<?>> parameterTypes = List.of(constructor.getParameterTypes());
      for (Class<?> requiredParameterType : requiredParameterTypes) {
        assertTrue(parameterTypes.contains(requiredParameterType), constructor.toString());
      }
    }
  }

  private static void assertMetadataMethodsRequireRuntimeSupport() {
    for (Method method : PircbotxEventMetadata.class.getDeclaredMethods()) {
      if (!List.of("inboundAt", "ircv3TagsFromEvent", "ircv3MessageId")
          .contains(method.getName())) {
        continue;
      }
      assertTrue(method.getParameterCount() == 2, method.toString());
      assertTrue(
          Arrays.stream(method.getParameterTypes())
              .anyMatch(type -> type.getSimpleName().endsWith("RuntimeSupport")),
          method.toString());
    }
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }
}
