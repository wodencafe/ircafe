package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServerTreeStartupSelectionRestorerTest {

  @Test
  void tryRestoreForServerSelectsRememberedLeafAndClearsState() {
    TargetRef remembered = new TargetRef("libera", "#ircafe");
    StubContext context = new StubContext();
    context.rememberedSelection = remembered;
    context.leaves.add(remembered);

    ServerTreeStartupSelectionRestorer restorer = new ServerTreeStartupSelectionRestorer();

    assertTrue(restorer.tryRestoreForServer(context, "libera"));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection(context));
    assertFalse(restorer.tryRestoreForServer(context, "libera"));
  }

  @Test
  void tryRestoreForServerSkipsWhenServerDoesNotMatch() {
    TargetRef remembered = new TargetRef("libera", "#ircafe");
    StubContext context = new StubContext();
    context.rememberedSelection = remembered;
    context.leaves.add(remembered);

    ServerTreeStartupSelectionRestorer restorer = new ServerTreeStartupSelectionRestorer();

    assertFalse(restorer.tryRestoreForServer(context, "oftc"));
    assertTrue(context.selections.isEmpty());
    assertEquals(remembered, restorer.rememberedSelection(context));
  }

  @Test
  void tryRestoreAfterEnsureSelectsWhenRememberedLeafBecomesAvailable() {
    TargetRef remembered = new TargetRef("libera", "#new");
    StubContext context = new StubContext();
    context.rememberedSelection = remembered;

    ServerTreeStartupSelectionRestorer restorer = new ServerTreeStartupSelectionRestorer();

    assertFalse(restorer.tryRestoreForServer(context, "libera"));
    context.leaves.add(remembered);

    assertTrue(restorer.tryRestoreAfterEnsure(context, remembered));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection(context));
  }

  @Test
  void tryRestoreForServerSelectsMonitorGroupWhenGroupIsSelectable() {
    TargetRef remembered = TargetRef.monitorGroup("libera");
    StubContext context = new StubContext();
    context.rememberedSelection = remembered;
    context.selectableMonitorGroups.add("libera");

    ServerTreeStartupSelectionRestorer restorer = new ServerTreeStartupSelectionRestorer();

    assertTrue(restorer.tryRestoreForServer(context, "libera"));
    assertEquals(List.of(remembered), context.selections);
    assertNull(restorer.rememberedSelection(context));
  }

  @Test
  void contextDelegatesSelectionOperations() {
    TargetRef leaf = new TargetRef("libera", "#ircafe");
    AtomicReference<TargetRef> rememberedSelection = new AtomicReference<>(leaf);
    List<TargetRef> selections = new ArrayList<>();

    ServerTreeStartupSelectionRestorer.Context context =
        ServerTreeStartupSelectionRestorer.context(
            rememberedSelection::get,
            rememberedSelection::set,
            id -> Objects.toString(id, "").trim(),
            ref -> Objects.equals(ref, leaf),
            sid -> "libera".equals(Objects.toString(sid, "").trim()),
            sid -> false,
            selections::add);

    assertSame(leaf, context.rememberedSelection());
    assertEquals("libera", context.normalizeServerId(" libera "));
    assertTrue(context.hasLeaf(leaf));
    assertTrue(context.isMonitorGroupSelectable("libera"));
    assertFalse(context.isInterceptorsGroupSelectable("libera"));

    context.setRememberedSelection(null);
    assertNull(context.rememberedSelection());
    context.selectTarget(leaf);
    assertEquals(List.of(leaf), selections);
  }

  private static final class StubContext implements ServerTreeStartupSelectionRestorer.Context {
    private TargetRef rememberedSelection;
    private final Set<TargetRef> leaves = new HashSet<>();
    private final Set<String> selectableMonitorGroups = new HashSet<>();
    private final Set<String> selectableInterceptorsGroups = new HashSet<>();
    private final List<TargetRef> selections = new ArrayList<>();

    @Override
    public TargetRef rememberedSelection() {
      return rememberedSelection;
    }

    @Override
    public void setRememberedSelection(TargetRef rememberedSelection) {
      this.rememberedSelection = rememberedSelection;
    }

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
