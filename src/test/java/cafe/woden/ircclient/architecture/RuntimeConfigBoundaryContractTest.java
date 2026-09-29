package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuntimeConfigBoundaryContractTest {

  @TempDir Path directory;

  @ParameterizedTest
  @CsvSource({
    "app, RuntimeConfigStore",
    "ui, RuntimeConfigStore[]",
    "irc, RuntimeConfigStore.Nested",
    "logging, cafe.woden.ircclient.config.runtime.RuntimeConfigStore"
  })
  void consumersCannotReferenceTheInternalStore(String consumerPackage, String fieldType)
      throws IOException {
    JavaClasses fixture = compile(consumerPackage, fieldType);

    assertThatThrownBy(
            () ->
                ArchitectureGuardrailsTest.only_config_should_depend_on_runtime_config_store.check(
                    fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("cafe.woden.ircclient." + consumerPackage + ".Consumer")
        .hasMessageContaining("cafe.woden.ircclient.config.runtime.RuntimeConfigStore");
  }

  @Test
  void configAdaptersCanReferenceTheInternalStore() throws IOException {
    JavaClasses fixture = compile("config", "RuntimeConfigStore");

    assertThatCode(
            () ->
                ArchitectureGuardrailsTest.only_config_should_depend_on_runtime_config_store.check(
                    fixture))
        .doesNotThrowAnyException();
  }

  @Test
  void consumersCanReferencePublicConfigPorts() throws IOException {
    JavaClasses fixture = compile("app", "cafe.woden.ircclient.config.api.SettingsPort");

    assertThatCode(
            () ->
                ArchitectureGuardrailsTest.only_config_should_depend_on_runtime_config_store.check(
                    fixture))
        .doesNotThrowAnyException();
  }

  private JavaClasses compile(String consumerPackage, String fieldType) throws IOException {
    Path output =
        ArchitectureFixtureCompiler.compile(
            directory,
            Map.of(
                "cafe/woden/ircclient/config/runtime/RuntimeConfigStore.java",
                """
                package cafe.woden.ircclient.config.runtime;
                public class RuntimeConfigStore { public static class Nested {} }
                """,
                "cafe/woden/ircclient/config/api/SettingsPort.java",
                """
                package cafe.woden.ircclient.config.api;
                public interface SettingsPort {}
                """,
                "cafe/woden/ircclient/consumer/PortConsumer.java",
                """
                package cafe.woden.ircclient.consumer;
                public class PortConsumer {
                  public cafe.woden.ircclient.config.api.SettingsPort settings;
                }
                """,
                "cafe/woden/ircclient/" + consumerPackage + "/Consumer.java",
                """
                package cafe.woden.ircclient.%s;
                import cafe.woden.ircclient.config.runtime.*;
                public class Consumer { public %s settings; }
                """
                    .formatted(consumerPackage, fieldType)));
    return new ClassFileImporter().importPath(output);
  }
}
