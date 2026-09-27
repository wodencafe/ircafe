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
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationIrcBoundaryContractTest {

  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"irc.IrcClientService", "irc.backend.BackendClient"})
  void applicationCannotDependOnBroadClientOrBackendSubtype(String dependency) throws IOException {
    JavaClasses fixture = compile(dependency);
    assertThatThrownBy(
            () ->
                ArchitectureGuardrailsTest.app_should_not_depend_on_broad_irc_client.check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("cafe.woden.ircclient.app.commands.Command")
        .hasMessageContaining(dependency);
  }

  @Test
  void applicationCanDependOnNarrowCommandPort() throws IOException {
    JavaClasses fixture = compile("irc.port.IrcMessagingPort");
    assertThatCode(
            () ->
                ArchitectureGuardrailsTest.app_should_not_depend_on_broad_irc_client.check(fixture))
        .doesNotThrowAnyException();
  }

  private JavaClasses compile(String dependency) throws IOException {
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
                "cafe/woden/ircclient/irc/port/IrcMessagingPort.java",
                    """
            package cafe.woden.ircclient.irc.port;
            public interface IrcMessagingPort {}
            """,
                "cafe/woden/ircclient/app/commands/Command.java",
                    """
            package cafe.woden.ircclient.app.commands;
            public class Command { public java.util.List<cafe.woden.ircclient.%s> clients; }
            """
                        .formatted(dependency)));
    return new ClassFileImporter().importPath(output);
  }
}
