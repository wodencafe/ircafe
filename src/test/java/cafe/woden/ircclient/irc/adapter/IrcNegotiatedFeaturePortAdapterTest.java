package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IrcNegotiatedFeaturePortAdapterTest {

  @ParameterizedTest(name = "{0} remains live and server scoped")
  @MethodSource("capabilityQueries")
  void capabilitiesRemainLiveAndServerScoped(
      String name,
      BiPredicate<IrcClientService, String> backendQuery,
      BiPredicate<IrcNegotiatedFeaturePort, String> portQuery) {
    IrcClientService irc = mock(IrcClientService.class);
    when(backendQuery.test(irc, "libera")).thenReturn(true, false);
    IrcNegotiatedFeaturePort port = new IrcNegotiatedFeaturePortAdapter(irc);

    assertTrue(portQuery.test(port, "libera"));
    assertFalse(portQuery.test(port, "oftc"));
    assertFalse(portQuery.test(port, "libera"));
  }

  @ParameterizedTest(name = "{0} is unavailable without a client")
  @MethodSource("capabilityQueries")
  void allCapabilitiesAreUnavailableWithoutClient(
      String name,
      BiPredicate<IrcClientService, String> backendQuery,
      BiPredicate<IrcNegotiatedFeaturePort, String> portQuery) {
    assertFalse(portQuery.test(new IrcNegotiatedFeaturePortAdapter(null), "libera"));
  }

  @Test
  void negotiatedMultilineLimitsRemainLiveAndKeepTheirPrecision() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.negotiatedMultilineMaxBytes("libera")).thenReturn(5_000_000_000L, 4096L);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(12, 8);
    IrcNegotiatedFeaturePort port = new IrcNegotiatedFeaturePortAdapter(irc);

    assertEquals(5_000_000_000L, port.negotiatedMultilineMaxBytes("libera"));
    assertEquals(12, port.negotiatedMultilineMaxLines("libera"));
    assertEquals(4096L, port.negotiatedMultilineMaxBytes("libera"));
    assertEquals(8, port.negotiatedMultilineMaxLines("libera"));
    assertEquals(0L, port.negotiatedMultilineMaxBytes("oftc"));
    assertEquals(0, port.negotiatedMultilineMaxLines("oftc"));
  }

  @Test
  void missingClientHasZeroMultilineLimits() {
    IrcNegotiatedFeaturePort port = new IrcNegotiatedFeaturePortAdapter(null);
    assertEquals(0L, port.negotiatedMultilineMaxBytes("libera"));
    assertEquals(0, port.negotiatedMultilineMaxLines("libera"));
  }

  private static Stream<Arguments> capabilityQueries() {
    return Stream.of(
        capability(
            "history",
            IrcClientService::isChatHistoryAvailable,
            IrcNegotiatedFeaturePort::isChatHistoryAvailable),
        capability(
            "tags",
            IrcClientService::isMessageTagsAvailable,
            IrcNegotiatedFeaturePort::isMessageTagsAvailable),
        capability(
            "reply",
            IrcClientService::isDraftReplyAvailable,
            IrcNegotiatedFeaturePort::isDraftReplyAvailable),
        capability(
            "react",
            IrcClientService::isDraftReactAvailable,
            IrcNegotiatedFeaturePort::isDraftReactAvailable),
        capability(
            "unreact",
            IrcClientService::isDraftUnreactAvailable,
            IrcNegotiatedFeaturePort::isDraftUnreactAvailable),
        capability(
            "multiline",
            IrcClientService::isMultilineAvailable,
            IrcNegotiatedFeaturePort::isMultilineAvailable),
        capability(
            "edit",
            IrcClientService::isExperimentalMessageEditAvailable,
            IrcNegotiatedFeaturePort::isExperimentalMessageEditAvailable),
        capability(
            "redaction",
            IrcClientService::isMessageRedactionAvailable,
            IrcNegotiatedFeaturePort::isMessageRedactionAvailable),
        capability(
            "read marker",
            IrcClientService::isReadMarkerAvailable,
            IrcNegotiatedFeaturePort::isReadMarkerAvailable),
        capability(
            "labeled response",
            IrcClientService::isLabeledResponseAvailable,
            IrcNegotiatedFeaturePort::isLabeledResponseAvailable),
        capability(
            "monitor",
            IrcClientService::isMonitorAvailable,
            IrcNegotiatedFeaturePort::isMonitorAvailable));
  }

  private static Arguments capability(
      String name,
      BiPredicate<IrcClientService, String> backendQuery,
      BiPredicate<IrcNegotiatedFeaturePort, String> portQuery) {
    return Arguments.of(name, backendQuery, portQuery);
  }
}
