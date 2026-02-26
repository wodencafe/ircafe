package cafe.woden.ircclient.ui.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.interceptors.InterceptorHit;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.InterceptorRuleMode;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterceptorPanelFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void rulesLifecycleHitListAndCsvExportFlowWork() throws Exception {
    String serverId = "libera";
    String interceptorId = "int-1";

    InterceptorStore store = mock(InterceptorStore.class);
    when(store.changes()).thenReturn(Flowable.never());

    AtomicReference<InterceptorDefinition> current =
        new AtomicReference<>(definition(interceptorId, "First Rule"));
    when(store.interceptor(serverId, interceptorId)).thenAnswer(inv -> current.get());
    when(store.listHits(serverId, interceptorId, 2_000))
        .thenReturn(
            List.of(
                hit(serverId, interceptorId, Instant.parse("2026-02-01T10:15:00Z"), "older line"),
                hit(serverId, interceptorId, Instant.parse("2026-02-01T10:16:00Z"), "newer line")));
    when(store.saveInterceptor(eq(serverId), any()))
        .thenAnswer(
            inv -> {
              InterceptorDefinition updated = inv.getArgument(1);
              current.set(updated);
              return true;
            });

    InterceptorPanel panel = onEdtCall(() -> new InterceptorPanel(store));
    JTable rulesTable = readField(panel, "rulesTable", JTable.class);
    JTable hitsTable = readField(panel, "hitsTable", JTable.class);
    JButton clearHits = readField(panel, "clearHits", JButton.class);
    Object rulesModel = readField(panel, "rulesModel", Object.class);

    try {
      onEdt(() -> panel.setInterceptorTarget(serverId, interceptorId));
      waitFor(
          () -> onEdtBoolean(() -> rulesTable.getRowCount() == 1 && hitsTable.getRowCount() == 2),
          Duration.ofSeconds(3));

      onEdt(
          () ->
              assertEquals(
                  "newer line",
                  String.valueOf(hitsTable.getValueAt(0, 7)),
                  "hit list should be sorted newest-first"));

      InterceptorRule created =
          new InterceptorRule(
              true,
              "Rule 2",
              "",
              InterceptorRuleMode.LIKE,
              "ping",
              InterceptorRuleMode.LIKE,
              "",
              InterceptorRuleMode.GLOB,
              "");
      onEdt(
          () -> {
            rulesModelAddRule(rulesModel, created);
            saveCurrentDefinition(panel);
          });
      waitFor(() -> current.get().rules().size() == 2, Duration.ofSeconds(2));

      InterceptorRule edited =
          new InterceptorRule(
              true,
              "Rule 2 edited",
              "",
              InterceptorRuleMode.REGEX,
              "p.ng",
              InterceptorRuleMode.LIKE,
              "",
              InterceptorRuleMode.GLOB,
              "");
      onEdt(
          () -> {
            rulesModelSetRule(rulesModel, 1, edited);
            saveCurrentDefinition(panel);
          });
      waitFor(
          () -> "Rule 2 edited".equals(current.get().rules().get(1).label()),
          Duration.ofSeconds(2));

      onEdt(
          () -> {
            rulesModelRemoveRow(rulesModel, 1);
            saveCurrentDefinition(panel);
          });
      waitFor(() -> current.get().rules().size() == 1, Duration.ofSeconds(2));

      onEdt(clearHits::doClick);
      verify(store).clearHits(serverId, interceptorId);

      Path out = tempDir.resolve("interceptor-hits.csv");
      onEdt(() -> writeHitsCsv(panel, out));
      List<String> lines = Files.readAllLines(out);
      assertTrue(lines.size() >= 3, "csv should contain header plus rows");
      assertTrue(lines.getFirst().contains("Time"), "csv header should include Time");
      assertTrue(lines.stream().anyMatch(line -> line.contains("newer line")));
    } finally {
      onEdt(panel::close);
      flushEdt();
    }
  }

  private static InterceptorDefinition definition(String id, String ruleLabel) {
    return new InterceptorDefinition(
        id,
        "Interceptor",
        true,
        "libera",
        InterceptorRuleMode.GLOB,
        "",
        InterceptorRuleMode.GLOB,
        "",
        false,
        true,
        false,
        "NOTIF_1",
        false,
        "",
        false,
        "",
        "",
        "",
        List.of(
            new InterceptorRule(
                true,
                ruleLabel,
                "",
                InterceptorRuleMode.LIKE,
                "",
                InterceptorRuleMode.LIKE,
                "",
                InterceptorRuleMode.GLOB,
                "")));
  }

  private static InterceptorHit hit(
      String serverId, String interceptorId, Instant at, String message) {
    return new InterceptorHit(
        serverId,
        interceptorId,
        "Interceptor",
        at,
        "#ircafe",
        "alice",
        "alice!u@h",
        "message",
        "",
        message);
  }

  private static void rulesModelAddRule(Object model, InterceptorRule rule) {
    try {
      Method m = model.getClass().getDeclaredMethod("addRule", InterceptorRule.class);
      m.setAccessible(true);
      m.invoke(model, rule);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void rulesModelSetRule(Object model, int row, InterceptorRule rule) {
    try {
      Method m = model.getClass().getDeclaredMethod("setRule", int.class, InterceptorRule.class);
      m.setAccessible(true);
      m.invoke(model, row, rule);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void rulesModelRemoveRow(Object model, int row) {
    try {
      Method m = model.getClass().getDeclaredMethod("removeRow", int.class);
      m.setAccessible(true);
      m.invoke(model, row);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void saveCurrentDefinition(InterceptorPanel panel) {
    try {
      Method m = InterceptorPanel.class.getDeclaredMethod("saveCurrentDefinition");
      m.setAccessible(true);
      m.invoke(panel);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeHitsCsv(InterceptorPanel panel, Path path) {
    try {
      Method m = InterceptorPanel.class.getDeclaredMethod("writeHitsCsv", Path.class);
      m.setAccessible(true);
      m.invoke(panel, path);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new RuntimeException(cause);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> T readField(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable runnable) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    final AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static boolean onEdtBoolean(ThrowingBooleanSupplier supplier) {
    try {
      return onEdtCall(supplier::getAsBoolean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }
}
