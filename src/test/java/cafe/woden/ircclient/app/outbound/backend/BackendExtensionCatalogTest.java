package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.model.TargetRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendExtensionCatalogTest {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @TempDir Path tempDir;

  @Test
  void resolvesBuiltInBackendStrategiesFromExtensions() {
    BackendExtensionCatalog catalog =
        BackendExtensionCatalog.fromExtensions(
            java.util.List.of(
                new IrcBackendExtension(),
                new MatrixBackendExtension(),
                new QuasselBackendExtension()));

    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor(IrcProperties.Server.Backend.IRC));
    assertInstanceOf(
        MatrixUploadCommandTranslationHandler.class,
        catalog.uploadTranslationHandlerFor(IrcProperties.Server.Backend.MATRIX));
    assertInstanceOf(
        QuasselMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor(IrcProperties.Server.Backend.QUASSEL_CORE));
    assertTrue(
        catalog.featureAdapterFor(IrcProperties.Server.Backend.MATRIX).supportsSemanticUpload());
    assertTrue(
        catalog
            .featureAdapterFor(IrcProperties.Server.Backend.QUASSEL_CORE)
            .supportsQuasselCoreCommands());
    assertNull(catalog.uploadTranslationHandlerFor(IrcProperties.Server.Backend.QUASSEL_CORE));
  }

  @Test
  void rejectsDuplicateBackendExtensions() {
    assertThrows(
        IllegalStateException.class,
        () ->
            BackendExtensionCatalog.fromExtensions(
                java.util.List.of(new IrcBackendExtension(), new DuplicateIrcBackendExtension())));
  }

  @Test
  void rejectsContributionBackendMismatch() {
    assertThrows(
        IllegalStateException.class,
        () ->
            BackendExtensionCatalog.fromExtensions(
                java.util.List.of(new MismatchedBackendExtension())));
  }

  @Test
  void resolvesCustomBackendIdExtensions() {
    BackendExtensionCatalog catalog =
        BackendExtensionCatalog.fromExtensions(java.util.List.of(new PluginBackendExtension()));

    assertTrue(catalog.featureAdapterFor("plugin-backend").supportsSemanticUpload());
    assertTrue(catalog.availableBackendIds().contains("plugin-backend"));
    assertTrue(
        catalog.availableBackendEditorProfiles().stream()
            .anyMatch(profile -> "Plugin Backend".equals(profile.displayName())));
  }

  @Test
  void acceptsLegacyEnumOnlyExtensions() {
    BackendExtensionCatalog catalog =
        BackendExtensionCatalog.fromExtensions(
            java.util.List.of(new LegacyMatrixBackendExtension()));

    assertTrue(catalog.featureAdapterFor("matrix").supportsSemanticUpload());
    assertInstanceOf(
        LegacyMatrixMessageMutationOutboundCommands.class,
        catalog.messageMutationCommandsFor("matrix"));
  }

  @Test
  void loadsBackendExtensionsFromPluginDirectoryJar() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    writePluginJar(pluginDir.resolve("plugin-backend.jar"));

    BackendExtensionCatalog catalog =
        BackendExtensionCatalog.installed(
            pluginDir, BackendExtensionCatalogTest.class.getClassLoader());
    try {
      assertTrue(catalog.featureAdapterFor("plugin-backend").supportsSemanticUpload());
      assertTrue(catalog.availableBackendIds().contains("plugin-backend"));
      assertTrue("Plugin Backend".equals(catalog.backendDisplayName("plugin-backend")));
    } finally {
      catalog.shutdown();
    }
  }

  @Test
  void loadsBackendExtensionsFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("plugin-backend.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    BackendExtensionCatalog catalog =
        BackendExtensionCatalog.installed(
            runtimeConfigPathPort, BackendExtensionCatalogTest.class.getClassLoader());
    try {
      assertTrue(catalog.featureAdapterFor("plugin-backend").supportsSemanticUpload());
      assertTrue(catalog.availableBackendIds().contains("plugin-backend"));
      assertTrue("Plugin Backend".equals(catalog.backendDisplayName("plugin-backend")));
    } finally {
      catalog.shutdown();
    }
  }

  private static void writePluginJar(Path jarPath) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(new JarEntry("META-INF/services/" + BackendExtension.class.getName()));
      out.write(
          (PluginBackendExtension.class.getName() + System.lineSeparator())
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static final class DuplicateIrcBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
    }
  }

  private static final class MismatchedBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX);
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new QuasselOutboundBackendFeatureAdapter();
    }
  }

  public static final class PluginBackendExtension implements BackendExtension {
    @Override
    public String backendId() {
      return "plugin-backend";
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new OutboundBackendFeatureAdapter() {
        @Override
        public String backendId() {
          return "plugin-backend";
        }

        @Override
        public boolean supportsSemanticUpload() {
          return true;
        }
      };
    }

    @Override
    public BackendEditorProfileSpec editorProfile() {
      return new BackendEditorProfileSpec(
          "plugin-backend",
          "Plugin Backend",
          7000,
          7443,
          true,
          false,
          true,
          true,
          false,
          "plugin-user",
          "Host",
          "Server password",
          "Nick",
          "Login",
          "Real name",
          "Use TLS",
          "Plugin backend connection.",
          "Plugin backend auth is configured directly.",
          "(optional)",
          "plugin.example.net",
          "plugin-user",
          "PluginUser",
          "Plugin User");
    }
  }

  private static final class LegacyMatrixBackendExtension implements BackendExtension {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public OutboundBackendFeatureAdapter featureAdapter() {
      return new LegacyMatrixFeatureAdapter();
    }

    @Override
    public MessageMutationOutboundCommands messageMutationOutboundCommands() {
      return new LegacyMatrixMessageMutationOutboundCommands();
    }
  }

  private static final class LegacyMatrixFeatureAdapter implements OutboundBackendFeatureAdapter {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public boolean supportsSemanticUpload() {
      return true;
    }
  }

  private static final class LegacyMatrixMessageMutationOutboundCommands
      implements MessageMutationOutboundCommands {
    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.MATRIX;
    }

    @Override
    public String buildReplyRawLine(TargetRef target, String replyToMessageId, String message) {
      return "";
    }

    @Override
    public String buildReactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildUnreactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildEditRawLine(TargetRef target, String targetMessageId, String editedText) {
      return "";
    }

    @Override
    public String buildRedactRawLine(TargetRef target, String targetMessageId, String reason) {
      return "";
    }
  }
}
