package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IrcConsumerBoundaryContractTest {

  @TempDir Path directory;

  @ParameterizedTest
  @CsvSource({
    "app.commands,irc.IrcClientService", "app.commands,irc.backend.BackendClient",
    "logging.history,irc.IrcClientService", "logging.history,irc.backend.BackendClient",
    "monitor.presence,irc.IrcClientService", "monitor.presence,irc.backend.BackendClient",
    "perform.commands,irc.IrcClientService", "perform.commands,irc.backend.BackendClient"
  })
  void consumerCannotDependOnBroadClientOrBackendSubtype(String consumerPackage, String dependency)
      throws IOException {
    JavaClasses fixture = compile(consumerPackage, dependency);
    assertThatThrownBy(() -> ruleFor(consumerPackage).check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("cafe.woden.ircclient." + consumerPackage + ".Command")
        .hasMessageContaining(dependency);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"app.commands", "logging.history", "monitor.presence", "perform.commands"})
  void consumerCanDependOnNarrowCommandPort(String consumerPackage) throws IOException {
    JavaClasses fixture = compile(consumerPackage, "irc.port.IrcChatHistoryPort");
    assertThatCode(() -> ruleFor(consumerPackage).check(fixture)).doesNotThrowAnyException();
  }

  private static ArchRule ruleFor(String consumerPackage) {
    if (consumerPackage.startsWith("perform.")) {
      return ArchitectureGuardrailsTest.perform_should_not_depend_on_broad_irc_client;
    }
    if (consumerPackage.startsWith("monitor.")) {
      return ArchitectureGuardrailsTest.monitor_should_not_depend_on_broad_irc_client;
    }
    return consumerPackage.startsWith("logging.")
        ? ArchitectureGuardrailsTest.logging_should_not_depend_on_broad_irc_client
        : ArchitectureGuardrailsTest.app_should_not_depend_on_broad_irc_client;
  }

  private JavaClasses compile(String consumerPackage, String dependency) throws IOException {
    Path output =
        ArchitectureFixtureCompiler.compile(
            directory,
            Map.of(
                "cafe/woden/ircclient/irc/IrcClientService.java",
                """
            package cafe.woden.ircclient.irc;
            public interface IrcClientService {}
            """,
                "cafe/woden/ircclient/irc/backend/BackendClient.java",
                """
            package cafe.woden.ircclient.irc.backend;
            public interface BackendClient extends cafe.woden.ircclient.irc.IrcClientService {}
            """,
                "cafe/woden/ircclient/irc/port/IrcChatHistoryPort.java",
                """
            package cafe.woden.ircclient.irc.port;
            public interface IrcChatHistoryPort {}
            """,
                "cafe/woden/ircclient/" + consumerPackage.replace('.', '/') + "/Command.java",
                """
            package cafe.woden.ircclient.%s;
            public class Command { public java.util.List<cafe.woden.ircclient.%s> clients; }
            """
                    .formatted(consumerPackage, dependency)));
    return new ClassFileImporter().importPath(output);
  }
}
