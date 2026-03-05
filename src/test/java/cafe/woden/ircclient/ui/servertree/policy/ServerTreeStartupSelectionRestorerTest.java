package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServerTreeStartupSelectionRestorerTest {

  @Test
  void tryRestoreForServerSelectsRememberedLeafAndClearsState() {
    TargetRef remembered = new TargetRef("libera", "#ircafe");
    StubContext context = new StubContext();
    context.leaves.add(remembered);

    ServerTreeStartupSelectionRestorer restorer =
        new ServerTreeStartupSelectionRestorer(remembered, context);

    assertTrue(restorer.tryRestoreForServer("libera"));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection());
    assertFalse(restorer.tryRestoreForServer("libera"));
  }

  @Test
  void tryRestoreForServerSkipsWhenServerDoesNotMatch() {
    TargetRef remembered = new TargetRef("libera", "#ircafe");
    StubContext context = new StubContext();
    context.leaves.add(remembered);

    ServerTreeStartupSelectionRestorer restorer =
        new ServerTreeStartupSelectionRestorer(remembered, context);

    assertFalse(restorer.tryRestoreForServer("oftc"));
    assertTrue(context.selections.isEmpty());
    assertEquals(remembered, restorer.rememberedSelection());
  }

  @Test
  void tryRestoreAfterEnsureSelectsWhenRememberedLeafBecomesAvailable() {
    TargetRef remembered = new TargetRef("libera", "#new");
    StubContext context = new StubContext();

    ServerTreeStartupSelectionRestorer restorer =
        new ServerTreeStartupSelectionRestorer(remembered, context);

    assertFalse(restorer.tryRestoreForServer("libera"));
    context.leaves.add(remembered);

    assertTrue(restorer.tryRestoreAfterEnsure(remembered));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection());
  }

  @Test
  void tryRestoreForServerSelectsMonitorGroupWhenGroupIsSelectable() {
    TargetRef remembered = TargetRef.monitorGroup("libera");
    StubContext context = new StubContext();
    context.selectableMonitorGroups.add("libera");

    ServerTreeStartupSelectionRestorer restorer =
        new ServerTreeStartupSelectionRestorer(remembered, context);

    assertTrue(restorer.tryRestoreForServer("libera"));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection());
  }

  @Test
  void contextDelegatesSelectionOperations() {
    TargetRef leaf = new TargetRef("libera", "#ircafe");
    List<TargetRef> selections = new ArrayList<>();

    ServerTreeStartupSelectionRestorer.Context context =
        ServerTreeStartupSelectionRestorer.context(
            id -> Objects.toString(id, "").trim(),
            ref -> Objects.equals(ref, leaf),
            sid -> "libera".equals(Objects.toString(sid, "").trim()),
            sid -> false,
            selections::add);

    assertEquals("libera", context.normalizeServerId(" libera "));
    assertTrue(context.hasLeaf(leaf));
    assertTrue(context.isMonitorGroupSelectable("libera"));
    assertFalse(context.isInterceptorsGroupSelectable("libera"));

    context.selectTarget(leaf);
    assertEquals(List.of(leaf), selections);
  }

  private static final class StubContext implements ServerTreeStartupSelectionRestorer.Context {
    private final Set<TargetRef> leaves = new HashSet<>();
    private final Set<String> selectableMonitorGroups = new HashSet<>();
    private final Set<String> selectableInterceptorsGroups = new HashSet<>();
    private final List<TargetRef> selections = new ArrayList<>();

    @Override
    public String normalizeServerId(String serverId) {
      return Objects.toString(serverId, "").trim();
    }

    @Override
    public boolean hasLeaf(TargetRef ref) {
      return ref != null && leaves.contains(ref);
    }

    @Override
    public boolean isMonitorGroupSelectable(String serverId) {
      return selectableMonitorGroups.contains(normalizeServerId(serverId));
    }

    @Override
    public boolean isInterceptorsGroupSelectable(String serverId) {
      return selectableInterceptorsGroups.contains(normalizeServerId(serverId));
    }

    @Override
    public void selectTarget(TargetRef ref) {
      if (ref != null) {
        selections.add(ref);
      }
    }
  }
}
