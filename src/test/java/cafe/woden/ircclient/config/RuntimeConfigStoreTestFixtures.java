package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import cafe.woden.ircclient.config.api.IgnoreRulesConfigPort;
import cafe.woden.ircclient.config.api.Ircv3StsPolicyConfigPort;
import cafe.woden.ircclient.config.api.MonitorRosterConfigPort;
import cafe.woden.ircclient.config.api.ServerRegistryConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;

public final class RuntimeConfigStoreTestFixtures {

  private RuntimeConfigStoreTestFixtures() {}

  public static IrcProperties emptyIrcProperties() {
    return new IrcProperties(null, List.of());
  }

  public static RuntimeConfigStore inMemoryStore() {
    return inMemoryStore(emptyIrcProperties());
  }

  public static RuntimeConfigStore inMemoryStore(IrcProperties defaults) {
    return new RuntimeConfigStore(" ", defaults);
  }

  public static RuntimeConfigStore store(Path configPath) {
    return store(configPath, emptyIrcProperties());
  }

  public static RuntimeConfigStore store(Path configPath, IrcProperties defaults) {
    return new RuntimeConfigStore(configPath.toString(), defaults);
  }

  public static RuntimeConfigStore storeWithServers(
      Path configPath, IrcProperties.Server... servers) {
    return store(configPath, new IrcProperties(null, List.of(servers)));
  }

  public static BouncerDiscoveryConfigPort bouncerDiscoveryPort(RuntimeConfigStore store) {
    return new RuntimeConfigBouncerDiscoveryAdapter(store);
  }

  public static MonitorRosterConfigPort monitorRosterPort(RuntimeConfigStore store) {
    return new RuntimeConfigMonitorRosterAdapter(store);
  }

  public static Ircv3StsPolicyConfigPort ircv3StsPolicyPort(RuntimeConfigStore store) {
    return new RuntimeConfigIrcv3StsPolicyAdapter(store);
  }

  public static IgnoreRulesConfigPort ignoreRulesPort(RuntimeConfigStore store) {
    return new RuntimeConfigIgnoreRulesAdapter(store);
  }

  public static ServerRegistryConfigPort serverRegistryPort(RuntimeConfigStore store) {
    return new RuntimeConfigServerRegistryAdapter(store);
  }
}
