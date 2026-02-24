package cafe.woden.ircclient.irc.soju;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class SojuEphemeralNamingTest {

  @Test
  void derivesDeterministicIdAndUser() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "zimmerdon", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "zimmedon",
            "zimmerdon",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    SojuNetwork net = new SojuNetwork("soju", "123", "libera", java.util.Map.of("name", "libera"));

    SojuEphemeralNaming.Derived d = SojuEphemeralNaming.derive(bouncer, net);
    assertEquals("soju:soju:123", d.serverId());
    assertEquals("zimmerdon/libera@ircafe", d.loginUser());
    assertEquals("libera", d.networkName());
  }

  @Test
  void stripsExistingNetworkAndClientSuffixFromBaseUser() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user/libera@laptop", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    SojuNetwork net = new SojuNetwork("soju", "9", "oftc", java.util.Map.of());
    SojuEphemeralNaming.Derived d = SojuEphemeralNaming.derive(bouncer, net);
    assertEquals("user/oftc@ircafe", d.loginUser());
  }

  @Test
  void sanitizesNetworkNameForUsernames() {
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "user",
            "Real",
            new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null),
            List.of(),
            List.of(),
            null);

    SojuNetwork net = new SojuNetwork("soju", "42", "my weird net", java.util.Map.of());
    SojuEphemeralNaming.Derived d = SojuEphemeralNaming.derive(bouncer, net);
    assertEquals("my_weird_net", d.networkName());
    assertEquals("user/my_weird_net@ircafe", d.loginUser());
  }
}
