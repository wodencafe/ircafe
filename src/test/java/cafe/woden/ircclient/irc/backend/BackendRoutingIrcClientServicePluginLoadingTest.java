package cafe.woden.ircclient.irc.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.BackendMetadataPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendRoutingIrcClientServicePluginLoadingTest {

  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    PluginProvidedIrcBackendClientService.reset();
  }

  @Test
  void loadsBackendTransportFromPluginDirectoryJar() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    writePluginJar(pluginDir.resolve("plugin-backend.jar"));
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-backend")));

    BackendRoutingIrcClientService service =
        BackendRoutingIrcClientService.installed(
            serverCatalog,
            BackendMetadataPort.builtInsOnly(),
            pluginDir,
            BackendRoutingIrcClientServicePluginLoadingTest.class.getClassLoader(),
            List.of(ircBackend));
    try {
      assertEquals("plugin-backend", service.backendIdForServer("plugin"));

      service.connect("plugin").blockingAwait();

      assertTrue(PluginProvidedIrcBackendClientService.connectedServers().contains("plugin"));
      verify(ircBackend, never()).connect("plugin");
    } finally {
      service.closePluginClassLoaders();
    }
  }

  @Test
  void loadsBackendTransportFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("plugin-backend.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-backend")));

    BackendRoutingIrcClientService service =
        BackendRoutingIrcClientService.installed(
            serverCatalog,
            BackendMetadataPort.builtInsOnly(),
            runtimeConfigPathPort,
            BackendRoutingIrcClientServicePluginLoadingTest.class.getClassLoader(),
            List.of(ircBackend));
    try {
      assertEquals("plugin-backend", service.backendIdForServer("plugin"));

      service.connect("plugin").blockingAwait();

      assertTrue(PluginProvidedIrcBackendClientService.connectedServers().contains("plugin"));
      verify(ircBackend, never()).connect("plugin");
    } finally {
      service.closePluginClassLoaders();
    }
  }

  private static void writePluginJar(Path jarPath) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(
          new JarEntry("META-INF/services/" + IrcBackendClientService.class.getName()));
      out.write(
          (PluginProvidedIrcBackendClientService.class.getName() + System.lineSeparator())
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static IrcProperties.Server server(String id, String backendId) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "tester",
        "tester",
        "IRCafe Test",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backendId);
  }

  public static final class PluginProvidedIrcBackendClientService
      extends NoOpBackendClientServiceSupport {

    private static final CopyOnWriteArrayList<String> CONNECTED_SERVERS =
        new CopyOnWriteArrayList<>();

    static List<String> connectedServers() {
      return List.copyOf(CONNECTED_SERVERS);
    }

    static void reset() {
      CONNECTED_SERVERS.clear();
    }

    @Override
    public String backendId() {
      return "plugin-backend";
    }

    @Override
    public Completable connect(String serverId) {
      CONNECTED_SERVERS.add(serverId);
      return Completable.complete();
    }
  }

  abstract static class NoOpBackendClientServiceSupport implements IrcBackendClientService {

    @Override
    public Flowable<ServerIrcEvent> events() {
      return Flowable.empty();
    }

    @Override
    public Optional<String> currentNick(String serverId) {
      return Optional.empty();
    }

    @Override
    public Completable connect(String serverId) {
      return Completable.complete();
    }

    @Override
    public Completable disconnect(String serverId) {
      return Completable.complete();
    }

    @Override
    public Completable changeNick(String serverId, String newNick) {
      return Completable.complete();
    }

    @Override
    public Completable setAway(String serverId, String awayMessage) {
      return Completable.complete();
    }

    @Override
    public Completable requestNames(String serverId, String channel) {
      return Completable.complete();
    }

    @Override
    public Completable joinChannel(String serverId, String channel) {
      return Completable.complete();
    }

    @Override
    public Completable whois(String serverId, String nick) {
      return Completable.complete();
    }

    @Override
    public Completable partChannel(String serverId, String channel, String reason) {
      return Completable.complete();
    }

    @Override
    public Completable sendToChannel(String serverId, String channel, String message) {
      return Completable.complete();
    }

    @Override
    public Completable sendPrivateMessage(String serverId, String nick, String message) {
      return Completable.complete();
    }

    @Override
    public Completable sendNoticeToChannel(String serverId, String channel, String message) {
      return Completable.complete();
    }

    @Override
    public Completable sendNoticePrivate(String serverId, String nick, String message) {
      return Completable.complete();
    }

    @Override
    public Completable sendRaw(String serverId, String rawLine) {
      return Completable.complete();
    }

    @Override
    public Completable requestChatHistoryBefore(
        String serverId, String target, Instant beforeExclusive, int limit) {
      return Completable.complete();
    }
  }
}
