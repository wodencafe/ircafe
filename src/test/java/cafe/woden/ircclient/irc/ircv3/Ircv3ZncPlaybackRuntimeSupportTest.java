package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ircv3ZncPlaybackRuntimeSupportTest {

  @Test
  void builtInProviderDetectsZncAndSuppressesBootstrapTraffic() {
    Ircv3ZncPlaybackRuntimeSupport support = Ircv3RuntimeTestFixtures.zncPlayback();

    Ircv3ZncPlaybackRuntimeSupport.Detection capability =
        support.detectZncCapability("znc.in/playback");
    Ircv3ZncPlaybackRuntimeSupport.Detection myInfo =
        support.detectZncRpl004(":server 004 me irc.example ZNC-1.9.1 oiwsz biklmnopst");

    assertTrue(capability.detected());
    assertEquals("CAP", capability.source());
    assertEquals("znc.in/playback", capability.evidence());
    assertTrue(myInfo.detected());
    assertEquals("RPL_MYINFO/004", myInfo.source());
    assertTrue(support.shouldSuppressBootstrap(true, "*playback", "play * 25"));
    assertTrue(support.shouldSuppressBootstrap(true, "*status", "ListNetworks"));
    assertFalse(support.shouldSuppressBootstrap(false, "*status", "ListNetworks"));
  }

  @Test
  void higherPriorityRuntimeProvidersCanReplaceDetectionAndSuppression() {
    Ircv3ZncPlaybackRuntimeSupport support =
        new Ircv3ZncPlaybackRuntimeSupport(
            Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(
                List.of(new CustomDetectionProvider())),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(
                List.of(new CustomSuppressionProvider())));

    assertTrue(support.detectZncCapability("custom/bouncer").detected());
    assertFalse(support.detectZncCapability("znc.in/playback").detected());
    assertTrue(support.shouldSuppressBootstrap(true, "service", "bootstrap"));
    assertFalse(support.shouldSuppressBootstrap(true, "*status", "ListNetworks"));
  }

  @Test
  void ambiguousOrUnsafeProviderOutputIsRejected() {
    Ircv3InboundCommandSignalProvider ambiguous =
        new CustomDetectionProvider() {
          @Override
          public List<Ircv3InboundCommandSignal> parse(
              Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
            return List.of(
                new Ircv3InboundCommandSignal.ZncDetectedObserved("CAP", "one"),
                new Ircv3InboundCommandSignal.ZncDetectedObserved("CAP", "two"));
          }
        };
    Ircv3InboundTagSignalProvider ambiguousSuppression =
        new CustomSuppressionProvider() {
          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            return List.of(
                Ircv3InboundTagSignal.of(
                    Ircv3InboundTagSignalType.HISTORY_BOOTSTRAP_SUPPRESSED, "one"),
                Ircv3InboundTagSignal.of(
                    Ircv3InboundTagSignalType.HISTORY_BOOTSTRAP_SUPPRESSED, "two"));
          }
        };
    Ircv3ZncPlaybackRuntimeSupport support =
        new Ircv3ZncPlaybackRuntimeSupport(
            Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(ambiguous)),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(ambiguousSuppression)));

    assertFalse(support.detectZncCapability("custom/bouncer").detected());
    assertFalse(support.shouldSuppressBootstrap(true, "service", "bootstrap"));
  }

  private static class CustomDetectionProvider implements Ircv3InboundCommandSignalProvider {
    @Override
    public String providerId() {
      return "custom-history";
    }

    @Override
    public int inboundCommandPriority() {
      return 100;
    }

    @Override
    public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
      return Set.of(
          Ircv3InboundCommandOperation.HISTORY_ZNC_CAPABILITY,
          Ircv3InboundCommandOperation.HISTORY_ZNC_RPL004);
    }

    @Override
    public List<Ircv3InboundCommandSignal> parse(
        Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
      if (operation == Ircv3InboundCommandOperation.HISTORY_ZNC_CAPABILITY
          && request.parameters().contains("custom/bouncer")) {
        return List.of(
            new Ircv3InboundCommandSignal.ZncDetectedObserved("CUSTOM", "custom/bouncer"));
      }
      return List.of();
    }
  }

  private static class CustomSuppressionProvider implements Ircv3InboundTagSignalProvider {
    @Override
    public String providerId() {
      return "custom-history";
    }

    @Override
    public int inboundTagPriority() {
      return 100;
    }

    @Override
    public Set<Ircv3InboundTagOperation> inboundTagOperations() {
      return Set.of(Ircv3InboundTagOperation.HISTORY_BOOTSTRAP_SUPPRESSION);
    }

    @Override
    public List<Ircv3InboundTagSignal> parse(
        Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
      if (request.selfAuthored()
          && request.rawTarget().equals("service")
          && request.parameters().equals(List.of("bootstrap"))) {
        return List.of(
            Ircv3InboundTagSignal.of(
                Ircv3InboundTagSignalType.HISTORY_BOOTSTRAP_SUPPRESSED, "true"));
      }
      return List.of();
    }
  }
}
