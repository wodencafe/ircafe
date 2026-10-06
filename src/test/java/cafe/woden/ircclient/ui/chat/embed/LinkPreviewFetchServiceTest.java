package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.plugins.InstalledPluginServices;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreview;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewHttp;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewHttpHeaders;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewHttpResponse;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewResolver;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkPreviewFetchServiceTest {

  private static final List<String> NO_ARG_BUILT_IN_RESOLVER_CLASS_NAMES = List.of();

  @TempDir Path tempDir;

  @Test
  void concurrentViewsSharePendingResolutionAndCachedResult() throws Exception {
    String url = "https://example.org/article";
    LinkPreview preview = new LinkPreview(url, "Shared article", null, "News", null, 1);
    AtomicInteger resolutions = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    LinkPreviewResolver resolver =
        (uri, originalUrl, http) -> {
          assertFalse(SwingUtilities.isEventDispatchThread());
          resolutions.incrementAndGet();
          started.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS));
          return preview;
        };
    LinkPreviewFetchService service = new LinkPreviewFetchService(null, List.of(resolver));
    try {
      var main = service.fetch("server", url).test();
      assertTrue(started.await(5, TimeUnit.SECONDS));
      var extra = service.fetch("server", url).test();
      release.countDown();

      main.awaitDone(5, TimeUnit.SECONDS).assertResult(preview);
      extra.awaitDone(5, TimeUnit.SECONDS).assertResult(preview);
      assertSame(preview, service.fetch("server", url).blockingGet());
      assertEquals(1, resolutions.get(), "multiple views must share a single article resolution");
    } finally {
      release.countDown();
    }
  }

  @Test
  void rejectsPrivateHostsBeforeInvokingResolvers() {
    AtomicBoolean invoked = new AtomicBoolean(false);
    LinkPreviewResolver resolver =
        (uri, originalUrl, http) -> {
          invoked.set(true);
          return new LinkPreview(originalUrl, "unexpected", null, "Test", null, 0);
        };
    LinkPreviewFetchService service = new LinkPreviewFetchService(null, List.of(resolver));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.fetch("server-a", "https://127.0.0.1/private").blockingGet());

    assertEquals(false, invoked.get());
  }

  @Test
  void loadsResolversFromInstalledPluginsPort() {
    LinkPreviewFetchService service =
        new LinkPreviewFetchService(
            null,
            List.of((uri, originalUrl, http) -> null),
            new FakeInstalledPluginsPort(List.of(new PluginLinkPreviewResolver())));

    LinkPreview preview = service.fetch("server-a", "https://plugin.example/item").blockingGet();

    assertEquals("Plugin preview", preview.title());
    assertEquals("Plugin", preview.siteName());
  }

  @Test
  void loadsResolversFromPluginDirectoryJar() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("plugin-link-preview.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);

    LinkPreviewFetchService service =
        new LinkPreviewFetchService(null, List.of(), installedPlugins);

    LinkPreview preview = service.fetch("server-a", "https://plugin.example/item").blockingGet();

    assertEquals("Plugin preview", preview.title());
    assertEquals("Plugin", preview.siteName());
    assertTrue(installedPlugins.pluginProblems().isEmpty());
  }

  @Test
  void loadsNoArgBuiltInResolversThroughClasspathServiceLoader() {
    List<String> resolverClassNames =
        LinkPreviewPluginProviders.linkPreviewResolvers(List.of(), null).stream()
            .map(resolver -> resolver.getClass().getName())
            .toList();

    assertEquals(
        NO_ARG_BUILT_IN_RESOLVER_CLASS_NAMES, orderedKnownNoArgResolvers(resolverClassNames));
  }

  @Test
  void loadsNoArgBuiltInResolversThroughInstalledPluginsPort() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);

    List<String> resolverClassNames =
        LinkPreviewPluginProviders.linkPreviewResolvers(List.of(), installedPlugins).stream()
            .map(resolver -> resolver.getClass().getName())
            .toList();

    assertEquals(
        NO_ARG_BUILT_IN_RESOLVER_CLASS_NAMES, orderedKnownNoArgResolvers(resolverClassNames));
    assertTrue(installedPlugins.pluginProblems().isEmpty());
  }

  @Test
  void recordsInstalledPluginResolverRuntimeFailureOnce() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("failing-plugin/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writeThrowingPluginJar(pluginDir.resolve("throwing-link-preview.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    LinkPreviewFetchService service =
        new LinkPreviewFetchService(null, List.of(), installedPlugins);

    assertThrows(
        RuntimeException.class,
        () -> service.fetch("server-a", "https://broken-preview.example/item").blockingGet());
    assertThrows(
        RuntimeException.class,
        () -> service.fetch("server-a", "https://broken-preview.example/other").blockingGet());

    assertEquals(1, installedPlugins.pluginProblems().size());
    assertTrue(
        installedPlugins.pluginProblems().getFirst().summary().contains("throwing-link-preview"));
    assertTrue(
        installedPlugins
            .pluginProblems()
            .getFirst()
            .details()
            .contains("ThrowingLinkPreviewResolver"));
    assertTrue(installedPlugins.pluginProblems().getFirst().details().contains("Error type:"));
    assertTrue(
        installedPlugins
            .pluginProblems()
            .getFirst()
            .details()
            .contains("broken-preview.example/item"));
  }

  @Test
  void dedupesLinkPreviewResolversByProviderClass() {
    LinkPreviewResolver builtInResolver = new DuplicateLinkPreviewResolver();

    List<LinkPreviewResolver> resolvers =
        LinkPreviewPluginProviders.linkPreviewResolvers(
            List.of(builtInResolver),
            new FakeInstalledPluginsPort(
                List.of(new DuplicateLinkPreviewResolver(), new PluginLinkPreviewResolver())));

    assertSame(
        builtInResolver,
        resolvers.stream()
            .filter(DuplicateLinkPreviewResolver.class::isInstance)
            .findFirst()
            .orElseThrow());
    assertEquals(
        1, resolvers.stream().filter(DuplicateLinkPreviewResolver.class::isInstance).count());
    assertEquals(1, resolvers.stream().filter(PluginLinkPreviewResolver.class::isInstance).count());
  }

  private static List<String> orderedKnownNoArgResolvers(List<String> resolverClassNames) {
    return resolverClassNames.stream()
        .filter(NO_ARG_BUILT_IN_RESOLVER_CLASS_NAMES::contains)
        .toList();
  }

  private static URI redditJsonUri(URI postUri) {
    String path = postUri.getPath();
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return URI.create(postUri.getScheme() + "://" + postUri.getHost() + path + ".json?raw_json=1");
  }

  private void writePluginJar(Path jarPath) throws Exception {
    String providerClassName = "cafe.woden.ircclient.testplugins.PluginLinkPreviewResolver";
    String providerSource =
        """
        package cafe.woden.ircclient.testplugins;

        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreview;
        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewHttp;
        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewResolver;
        import java.net.URI;

        public final class PluginLinkPreviewResolver implements LinkPreviewResolver {
          public LinkPreview tryResolve(URI uri, String originalUrl, LinkPreviewHttp http) {
            if (!"plugin.example".equals(uri.getHost())) return null;
            return new LinkPreview(
                originalUrl, "Plugin preview", "from plugin jar", "Plugin", null, 0);
          }
        }
        """;
    CompiledPluginJarSupport.writePluginJar(
        jarPath,
        providerClassName,
        providerSource,
        LinkPreviewResolver.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("plugin-link-preview", "1.0.0"));
  }

  private void writeThrowingPluginJar(Path jarPath) throws Exception {
    String providerClassName = "cafe.woden.ircclient.testplugins.ThrowingLinkPreviewResolver";
    String providerSource =
        """
        package cafe.woden.ircclient.testplugins;

        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreview;
        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewHttp;
        import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreviewResolver;
        import java.net.URI;

        public final class ThrowingLinkPreviewResolver implements LinkPreviewResolver {
          public LinkPreview tryResolve(URI uri, String originalUrl, LinkPreviewHttp http) {
            if (!"broken-preview.example".equals(uri.getHost())) return null;
            throw new IllegalStateException("resolver exploded");
          }
        }
        """;
    CompiledPluginJarSupport.writePluginJar(
        jarPath,
        providerClassName,
        providerSource,
        LinkPreviewResolver.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("throwing-link-preview", "1.0.0"));
  }

  private static final class FakeInstalledPluginsPort implements InstalledPluginsPort {
    private final List<?> pluginServices;

    private FakeInstalledPluginsPort(List<?> pluginServices) {
      this.pluginServices = List.copyOf(pluginServices);
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      java.util.ArrayList<T> services =
          new java.util.ArrayList<>(
              java.util.Objects.requireNonNullElse(builtInServices, List.of()));
      for (Object pluginService : pluginServices) {
        if (serviceType.isInstance(pluginService)) {
          services.add(serviceType.cast(pluginService));
        }
      }
      return List.copyOf(services);
    }
  }

  private static final class DuplicateLinkPreviewResolver implements LinkPreviewResolver {
    @Override
    public LinkPreview tryResolve(URI uri, String originalUrl, LinkPreviewHttp http) {
      return null;
    }
  }

  private static final class PluginLinkPreviewResolver implements LinkPreviewResolver {
    @Override
    public LinkPreview tryResolve(URI uri, String originalUrl, LinkPreviewHttp http) {
      if (!"plugin.example".equals(uri.getHost())) {
        return null;
      }
      return new LinkPreview(originalUrl, "Plugin preview", "from fake port", "Plugin", null, 0);
    }
  }

  private record FakeLinkPreviewHttp(URI expectedUri, String expectedAccept, String body)
      implements LinkPreviewHttp {
    @Override
    public LinkPreviewHttpResponse<InputStream> getStream(URI uri, String accept)
        throws IOException {
      throw new IOException("unexpected stream fetch");
    }

    @Override
    public LinkPreviewHttpResponse<InputStream> getStream(
        URI uri, String accept, Map<String, String> extraHeaders) throws IOException {
      throw new IOException("unexpected stream fetch");
    }

    @Override
    public LinkPreviewHttpResponse<String> getString(URI uri) throws IOException {
      throw new IOException("unexpected string fetch without accept");
    }

    @Override
    public LinkPreviewHttpResponse<String> getString(URI uri, Map<String, String> extraHeaders)
        throws IOException {
      throw new IOException("unexpected string fetch without accept");
    }

    @Override
    public LinkPreviewHttpResponse<String> getString(
        URI uri, String accept, Map<String, String> extraHeaders) {
      assertEquals(expectedUri, uri);
      assertEquals(expectedAccept, accept);
      return new LinkPreviewHttpResponse<>(200, new LinkPreviewHttpHeaders(Map.of()), body);
    }
  }
}
