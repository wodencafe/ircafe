package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreIrcv3StsPoliciesTest {

  @TempDir Path tempDir;

  @Test
  void stsPoliciesCanBePersistedAndReadBack() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberIrcv3StsPolicy(
        "IRC.Example.NET",
        1_900_000_000_000L,
        6697,
        true,
        86_400L,
        "duration=86400,port=6697,preload");

    Map<String, RuntimeConfigStore.Ircv3StsPolicySnapshot> policies = store.readIrcv3StsPolicies();
    assertEquals(1, policies.size());
    RuntimeConfigStore.Ircv3StsPolicySnapshot policy = policies.get("irc.example.net");
    assertNotNull(policy);
    assertEquals(1_900_000_000_000L, policy.expiresAtEpochMs());
    assertEquals(Integer.valueOf(6697), policy.port());
    assertTrue(policy.preload());
    assertEquals(86_400L, policy.durationSeconds());
  }

  @Test
  void forgettingPolicyRemovesPersistedEntry() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberIrcv3StsPolicy(
        "irc.example.net", 1_900_000_000_000L, 6697, false, 86_400L, "duration=86400,port=6697");
    assertEquals(1, store.readIrcv3StsPolicies().size());

    store.forgetIrcv3StsPolicy("IRC.EXAMPLE.NET");
    assertTrue(store.readIrcv3StsPolicies().isEmpty());
  }
}
