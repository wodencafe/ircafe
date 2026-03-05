package cafe.woden.ircclient.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class StateModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final WhoisRoutingState whoisRoutingState;
  private final WhoisRoutingPort whoisRoutingPort;
  private final AwayRoutingPort awayRoutingPort;
  private final ModeRoutingPort modeRoutingPort;
  private final CtcpRoutingPort ctcpRoutingPort;
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingPort;
  private final LabeledResponseRoutingPort labeledResponseRoutingPort;
  private final PendingEchoMessagePort pendingEchoMessagePort;
  private final PendingInvitePort pendingInvitePort;

  StateModuleIntegrationTest(
      ApplicationContext applicationContext,
      WhoisRoutingState whoisRoutingState,
      WhoisRoutingPort whoisRoutingPort,
      AwayRoutingPort awayRoutingPort,
      ModeRoutingPort modeRoutingPort,
      CtcpRoutingPort ctcpRoutingPort,
      ChatHistoryRequestRoutingPort chatHistoryRequestRoutingPort,
      LabeledResponseRoutingPort labeledResponseRoutingPort,
      PendingEchoMessagePort pendingEchoMessagePort,
      PendingInvitePort pendingInvitePort) {
    this.applicationContext = applicationContext;
    this.whoisRoutingState = whoisRoutingState;
    this.whoisRoutingPort = whoisRoutingPort;
    this.awayRoutingPort = awayRoutingPort;
    this.modeRoutingPort = modeRoutingPort;
    this.ctcpRoutingPort = ctcpRoutingPort;
    this.chatHistoryRequestRoutingPort = chatHistoryRequestRoutingPort;
    this.labeledResponseRoutingPort = labeledResponseRoutingPort;
    this.pendingEchoMessagePort = pendingEchoMessagePort;
    this.pendingInvitePort = pendingInvitePort;
  }

  @Test
  void exposesStateModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(WhoisRoutingState.class).size());
    assertEquals(1, applicationContext.getBeansOfType(WhoisRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(AwayRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ModeRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(CtcpRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ChatHistoryRequestRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(LabeledResponseRoutingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(PendingEchoMessagePort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(PendingInvitePort.class).size());
    assertNotNull(whoisRoutingState);
    assertNotNull(whoisRoutingPort);
    assertNotNull(awayRoutingPort);
    assertNotNull(modeRoutingPort);
    assertNotNull(ctcpRoutingPort);
    assertNotNull(chatHistoryRequestRoutingPort);
    assertNotNull(labeledResponseRoutingPort);
    assertNotNull(pendingEchoMessagePort);
    assertNotNull(pendingInvitePort);
    assertEquals(WhoisRoutingState.class, AopUtils.getTargetClass(whoisRoutingPort));
  }

  @Test
  void stateBeansSupportBasicRoutingAndInviteTracking() {
    TargetRef origin = new TargetRef("libera", "#module");

    whoisRoutingState.put("libera", "Alice", origin);
    assertEquals(origin, whoisRoutingState.remove("libera", "alice"));

    labeledResponseRoutingPort.remember("libera", "L1", origin, "WHOIS alice", Instant.now());
    assertNotNull(labeledResponseRoutingPort.findIfFresh("libera", "L1", Duration.ofHours(1)));

    PendingInvitePort.RecordResult result =
        pendingInvitePort.record(
            Instant.parse("2026-01-01T00:00:01Z"),
            "libera",
            "#module",
            "alice",
            "bob",
            "join us",
            true);
    assertEquals(1, result.invite().repeatCount());
  }
}
