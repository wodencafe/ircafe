package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.springframework.beans.factory.annotation.Autowired;

class PircbotxInputParserHookInstallerRuntimeCompositionTest {

  @Test
  void reusesInstallerOwnedRuntimeAdaptersAcrossInputParsers() throws Exception {
    Ircv3RuntimeCatalogs catalogs =
        new Ircv3RuntimeCatalogs(
            mock(Ircv3InboundCommandSignalRuntimeCatalog.class),
            mock(Ircv3InboundTagSignalRuntimeCatalog.class),
            mock(Ircv3OutboundCommandRuntimeCatalog.class),
            mock(Ircv3MessageMutationRuntimeCatalog.class),
            mock(Ircv3MessageTagsRuntimeCatalog.class));
    PircbotxInputParserHookInstaller installer =
        new PircbotxInputParserHookInstaller(
            Ircv3RuntimeTestFixtures.stsPolicyService(catalogs), catalogs);

    PircbotxIrcv3InputParser first =
        installer.createParser(
            dummyBot(), "first", new PircbotxConnectionState("first"), ignored -> {});
    PircbotxIrcv3InputParser second =
        installer.createParser(
            dummyBot(), "second", new PircbotxConnectionState("second"), ignored -> {});

    assertNotSame(first, second);
    assertRuntimeComposition(installer, first);
    assertRuntimeComposition(installer, second);
  }

  @Test
  void keepsOnlyExplicitInstallerCompositionBoundaries() {
    Constructor<?>[] constructors = PircbotxInputParserHookInstaller.class.getConstructors();
    assertEquals(2, constructors.length);
    assertEquals(
        1L,
        Arrays.stream(constructors)
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .count());
    for (Constructor<?> constructor : constructors) {
      assertFalse(constructor.isAnnotationPresent(Deprecated.class), constructor.toString());
      assertTrue(
          Arrays.stream(constructor.getParameterTypes())
              .anyMatch(parameterType -> parameterType == Ircv3RuntimeCatalogs.class),
          constructor.toString());
    }
    assertEquals(1, PircbotxIrcv3InputParser.class.getDeclaredConstructors().length);
    assertEquals(
        18, PircbotxIrcv3InputParser.class.getDeclaredConstructors()[0].getParameterCount());
  }

  @Test
  void parserHelpersExposeOnlyExplicitRuntimeConstructors() {
    assertConstructor(PircbotxAccountTagSupport.class, 3);
    assertConstructor(PircbotxCapabilityNegotiationSupport.class, 7);
    assertConstructor(PircbotxMultilineCapStateSupport.class, 1);
    assertConstructor(PircbotxPresenceSignalSupport.class, 3);
    assertConstructor(PircbotxStandardReplySupport.class, 3);
    assertConstructor(PircbotxTagSignalSupport.class, 6);
  }

  private static void assertRuntimeComposition(
      PircbotxInputParserHookInstaller installer, PircbotxIrcv3InputParser parser)
      throws Exception {
    PircbotxCapabilityNegotiationSupport capabilityNegotiationSupport =
        field(parser, "capabilityNegotiationSupport", PircbotxCapabilityNegotiationSupport.class);
    assertSame(
        field(
            installer,
            "capabilityNegotiationRuntimeSupport",
            Ircv3CapabilityNegotiationRuntimeSupport.class),
        field(
            capabilityNegotiationSupport,
            "runtimeSupport",
            Ircv3CapabilityNegotiationRuntimeSupport.class));
    assertSame(
        field(
            installer, "historyTransportRuntimeSupport", Ircv3HistoryTransportRuntimeSupport.class),
        field(
            capabilityNegotiationSupport,
            "historyTransportRuntimeSupport",
            Ircv3HistoryTransportRuntimeSupport.class));

    assertSame(
        field(installer, "messageMutationRuntimeSupport", Ircv3MessageMutationRuntimeSupport.class),
        field(parser, "messageMutationRuntimeSupport", Ircv3MessageMutationRuntimeSupport.class));
    assertSame(
        field(installer, "readMarkerRuntimeSupport", Ircv3ReadMarkerRuntimeSupport.class),
        field(parser, "readMarkerRuntimeSupport", Ircv3ReadMarkerRuntimeSupport.class));
    assertSame(
        field(installer, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class),
        field(parser, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class));
    assertSame(
        field(installer, "multilineCapStateSupport", PircbotxMultilineCapStateSupport.class),
        field(parser, "multilineCapStateSupport", PircbotxMultilineCapStateSupport.class));
    assertSame(
        field(installer, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class),
        field(parser, "serverTimeRuntimeSupport", Ircv3ServerTimeRuntimeSupport.class));
    assertSame(
        field(installer, "echoMessageRuntimeSupport", Ircv3EchoMessageRuntimeSupport.class),
        field(parser, "echoMessageRuntimeSupport", Ircv3EchoMessageRuntimeSupport.class));
    assertSame(
        field(installer, "labeledResponseRuntimeSupport", Ircv3LabeledResponseRuntimeSupport.class),
        field(parser, "labeledResponseRuntimeSupport", Ircv3LabeledResponseRuntimeSupport.class));

    PircbotxAccountTagSupport accountTagSupport =
        field(parser, "accountTagSupport", PircbotxAccountTagSupport.class);
    assertSame(
        field(installer, "accountTagRuntimeSupport", Ircv3AccountTagRuntimeSupport.class),
        field(accountTagSupport, "runtimeSupport", Ircv3AccountTagRuntimeSupport.class));

    PircbotxPresenceSignalSupport presenceSignalSupport =
        field(parser, "presenceSignalSupport", PircbotxPresenceSignalSupport.class);
    assertSame(
        field(
            installer,
            "inboundCommandRuntimeCatalog",
            Ircv3InboundCommandSignalRuntimeCatalog.class),
        field(
            presenceSignalSupport,
            "runtimeCatalog",
            Ircv3InboundCommandSignalRuntimeCatalog.class));

    PircbotxStandardReplySupport standardReplySupport =
        field(parser, "standardReplySupport", PircbotxStandardReplySupport.class);
    assertSame(
        field(installer, "standardReplyRuntimeSupport", Ircv3StandardReplyRuntimeSupport.class),
        field(standardReplySupport, "runtimeSupport", Ircv3StandardReplyRuntimeSupport.class));

    PircbotxTagSignalSupport tagSignalSupport =
        field(parser, "tagSignalSupport", PircbotxTagSignalSupport.class);
    assertSame(
        field(installer, "channelContextRuntimeSupport", Ircv3ChannelContextRuntimeSupport.class),
        field(
            tagSignalSupport,
            "channelContextRuntimeSupport",
            Ircv3ChannelContextRuntimeSupport.class));
    assertSame(
        field(installer, "messageMutationRuntimeSupport", Ircv3MessageMutationRuntimeSupport.class),
        field(
            tagSignalSupport,
            "messageMutationRuntimeSupport",
            Ircv3MessageMutationRuntimeSupport.class));
    assertSame(
        field(installer, "readMarkerRuntimeSupport", Ircv3ReadMarkerRuntimeSupport.class),
        field(tagSignalSupport, "readMarkerRuntimeSupport", Ircv3ReadMarkerRuntimeSupport.class));
    assertSame(
        field(installer, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class),
        field(tagSignalSupport, "typingRuntimeSupport", Ircv3TypingRuntimeSupport.class));
  }

  private static void assertConstructor(Class<?> type, int parameterCount) {
    Constructor<?>[] constructors = type.getConstructors();
    assertEquals(1, constructors.length, type.getSimpleName());
    assertEquals(parameterCount, constructors[0].getParameterCount(), type.getSimpleName());
  }

  private static PircBotX dummyBot() {
    Configuration configuration =
        new Configuration.Builder()
            .setName("ircafe-test")
            .addServer("example.invalid", 6667)
            .buildConfiguration();
    return new PircBotX(configuration);
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }
}
