package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZncEphemeralNamingTest {

  @Test
  void derive_usesLoginUser_whenNoSasl() {
    IrcProperties.Server s = server("znc", "user", null);
    ZncNetwork net = new ZncNetwork("znc", "Libera.Chat", true);

    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(s, net);

    assertEquals("znc:znc:libera.chat", d.serverId());
    assertEquals("user/Libera.Chat", d.loginUser());
    assertEquals("libera.chat", d.networkKey());
  }

  @Test
  void derive_stripsExistingNetworkFromLogin() {
    IrcProperties.Server s = server("znc", "user/oldnet", null);
    ZncNetwork net = new ZncNetwork("znc", "oftc", true);

    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(s, net);

    assertEquals("user/oftc", d.loginUser());
    assertEquals("znc:znc:oftc", d.serverId());
    assertEquals("oftc", d.networkKey());
  }

  @Test
  void derive_preservesClientIdInLogin() {
    IrcProperties.Server s = server("znc", "user@laptop/oldnet", null);
    ZncNetwork net = new ZncNetwork("znc", "Libera", true);

    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(s, net);

    assertEquals("user@laptop/Libera", d.loginUser());
    assertEquals("znc:znc:libera", d.serverId());
    assertEquals("libera", d.networkKey());
  }

  @Test
  void derive_prefersSaslUsernameOverLogin() {
    IrcProperties.Server s = server("znc", "loginUser", "saslUser@desktop/ignored");
    ZncNetwork net = new ZncNetwork("znc", "OFTC", true);

    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(s, net);

    assertEquals("saslUser@desktop/OFTC", d.loginUser());
    assertEquals("znc:znc:oftc", d.serverId());
    assertEquals("oftc", d.networkKey());
  }

  @Test
  void sanitize_replacesWeirdChars() {
    String seg = ZncEphemeralNaming.sanitizeNetworkSegment("  my net! (cool)  ");
    assertEquals("my_net___cool", seg);
    assertEquals("my_net___cool", ZncEphemeralNaming.normalizeNetworkKey("  my net! (cool)  "));
  }

  private static IrcProperties.Server server(String id, String login, String saslUser) {
    IrcProperties.Server.Sasl sasl = (saslUser == null)
        ? new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null)
        : new IrcProperties.Server.Sasl(true, saslUser, "pw", "PLAIN", null);

    return new IrcProperties.Server(
        id,
        "znc.example",
        6697,
        true,
        "",
        "nick",
        login,
        "real",
        sasl,
        List.of(),
        List.of(),
        null
    );
  }
}
