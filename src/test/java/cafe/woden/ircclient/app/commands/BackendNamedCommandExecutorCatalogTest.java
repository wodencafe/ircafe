package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BackendNamedCommandExecutorCatalogTest {

  @Test
  void duplicateExecutionCommandRegistrationsFailFast() {
    BackendNamedCommandExecutor first =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendexec");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              io.reactivex.rxjava3.disposables.CompositeDisposable disposables,
              ParsedInput.BackendNamed command) {
            return false;
          }
        };
    BackendNamedCommandExecutor second =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendexec");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              io.reactivex.rxjava3.disposables.CompositeDisposable disposables,
              ParsedInput.BackendNamed command) {
            return false;
          }
        };

    assertThrows(
        IllegalStateException.class,
        () -> BackendNamedCommandExecutorCatalog.fromExecutors(java.util.List.of(first, second)));
  }
}
