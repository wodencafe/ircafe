package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Owns interaction wiring assembly and exposes lifecycle operations used by the dockable. */
public final class ServerTreeInteractionSetupCoordinator {

  @FunctionalInterface
  public interface MediatorInputsFactory
      extends BiFunction<
          ServerTreePinnedDockDragController,
          ServerTreeMiddleDragReorderHandler.Context,
          ServerTreeInteractionWiringFactory.MediatorInputs> {}

  private final ServerTreePinnedDockDragController pinnedDockDragController;
  private final ServerTreeInteractionMediator interactionMediator;

  public static ServerTreeInteractionSetupCoordinator create(
      ServerTreeInteractionWiringFactory interactionWiringFactory,
      ServerTreeInteractionWiringFactory.MiddleDragInputs middleDragInputs,
      ServerTreeInteractionWiringFactory.PinnedDockDragInputs pinnedDockDragInputs,
      MediatorInputsFactory mediatorInputsFactory) {
    Objects.requireNonNull(interactionWiringFactory, "interactionWiringFactory");
    Objects.requireNonNull(middleDragInputs, "middleDragInputs");
    Objects.requireNonNull(pinnedDockDragInputs, "pinnedDockDragInputs");
    Objects.requireNonNull(mediatorInputsFactory, "mediatorInputsFactory");

    ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        interactionWiringFactory.createMiddleDragReorderContext(middleDragInputs);
    ServerTreePinnedDockDragController pinnedDockDragController =
        interactionWiringFactory.createPinnedDockDragController(pinnedDockDragInputs);
    ServerTreeInteractionWiringFactory.MediatorInputs mediatorInputs =
        mediatorInputsFactory.apply(pinnedDockDragController, middleDragContext);
    ServerTreeInteractionMediator interactionMediator =
        interactionWiringFactory.createInteractionMediator(mediatorInputs);
    return new ServerTreeInteractionSetupCoordinator(pinnedDockDragController, interactionMediator);
  }

  public ServerTreeInteractionSetupCoordinator(
      ServerTreePinnedDockDragController pinnedDockDragController,
      ServerTreeInteractionMediator interactionMediator) {
    this.pinnedDockDragController =
        Objects.requireNonNull(pinnedDockDragController, "pinnedDockDragController");
    this.interactionMediator = Objects.requireNonNull(interactionMediator, "interactionMediator");
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    pinnedDockDragController.setPinnedDockableProvider(provider);
  }

  public void clearPreparedChannelDockDrag() {
    pinnedDockDragController.clearPreparedChannelDockDrag();
  }

  public void install() {
    interactionMediator.install();
  }
}
