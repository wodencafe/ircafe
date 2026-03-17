package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IrcPortAdaptersContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              IrcConnectionLifecyclePortAdapter.class,
              IrcCurrentNickPortAdapter.class,
              IrcEchoCapabilityPortAdapter.class,
              IrcLagProbePortAdapter.class,
              IrcMediatorInteractionPortAdapter.class,
              IrcNegotiatedFeaturePortAdapter.class,
              IrcReadMarkerPortAdapter.class,
              IrcShutdownPortAdapter.class,
              IrcTargetMembershipPortAdapter.class,
              IrcTypingPortAdapter.class)
          .withBean("ircClientService", IrcClientService.class, () -> mock(IrcClientService.class));

  @Test
  void createsAllNamedIrcPortAdapterBeans() {
    runner.run(
        ctx -> {
          assertNotNull(
              ctx.getBean("ircConnectionLifecyclePort", IrcConnectionLifecyclePort.class));
          assertNotNull(ctx.getBean("ircCurrentNickPort", IrcCurrentNickPort.class));
          assertNotNull(ctx.getBean("ircEchoCapabilityPort", IrcEchoCapabilityPort.class));
          assertNotNull(ctx.getBean("ircLagProbePort", IrcLagProbePort.class));
          assertNotNull(
              ctx.getBean("ircMediatorInteractionPort", IrcMediatorInteractionPort.class));
          assertNotNull(ctx.getBean("ircNegotiatedFeaturePort", IrcNegotiatedFeaturePort.class));
          assertNotNull(ctx.getBean("ircReadMarkerPort", IrcReadMarkerPort.class));
          assertNotNull(ctx.getBean("ircShutdownPort", IrcShutdownPort.class));
          assertNotNull(ctx.getBean("ircTargetMembershipPort", IrcTargetMembershipPort.class));
          assertNotNull(ctx.getBean("ircTypingPort", IrcTypingPort.class));
        });
  }
}
