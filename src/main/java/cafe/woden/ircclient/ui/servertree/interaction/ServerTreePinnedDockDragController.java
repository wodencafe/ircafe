package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.api.DockingAPI;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.internal.DockableWrapper;
import io.github.andrewauclair.moderndocking.internal.DockingInternal;
import io.github.andrewauclair.moderndocking.internal.floating.FloatListener;
import io.github.andrewauclair.moderndocking.internal.floating.Floating;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates prepared channel press/drag gestures used to float pinned chat dockables. */
public final class ServerTreePinnedDockDragController {
  private static final Logger log =
      LoggerFactory.getLogger(ServerTreePinnedDockDragController.class);

  private static final int PREPARED_CHANNEL_DRAG_THRESHOLD_FALLBACK_PX = 5;
  private static final Function<TargetRef, Dockable> NO_PINNED_DOCKABLE_PROVIDER = target -> null;

  private final JTree tree;
  private final BiFunction<Integer, Integer, TargetRef> channelTargetForHit;

  private volatile Function<TargetRef, Dockable> pinnedDockableProvider =
      NO_PINNED_DOCKABLE_PROVIDER;
  private volatile DragGestureRecognizer preparedTreeDragRecognizer = null;
  private volatile DragGestureListener preparedTreeDragGestureListener = null;
  private volatile TargetRef preparedTreeDragTarget = null;

  public ServerTreePinnedDockDragController(
      JTree tree, BiFunction<Integer, Integer, TargetRef> channelTargetForHit) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.channelTargetForHit = Objects.requireNonNull(channelTargetForHit, "channelTargetForHit");
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    pinnedDockableProvider = provider == null ? NO_PINNED_DOCKABLE_PROVIDER : provider;
  }

  public void prepareChannelDockDrag(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return;

    TargetRef target = channelTargetForEvent(event);
    if (target == null) return;

    clearPreparedChannelDockDrag();

    DragSource dragSource = DragSource.getDefaultDragSource();

    DragGestureListener dragGestureListener =
        dragGestureEvent -> {
          if (!shouldStartPreparedChannelDockDrag(dragGestureEvent)) {
            return;
          }
          Dockable dockable = ensurePinnedDockableForDrag(target);
          if (dockable == null) {
            log.warn(
                "[ircafe] prepared channel drag failed: no dockable available for target={}",
                target);
            clearPreparedChannelDockDrag();
            return;
          }
          try {
            beginPinnedDockDrag(dockable, dragGestureEvent);
          } finally {
            clearPreparedChannelDockDrag();
          }
        };
    try {
      DragGestureRecognizer recognizer =
          dragSource.createDefaultDragGestureRecognizer(
              tree, DnDConstants.ACTION_MOVE, dragGestureListener);
      if (recognizer == null) {
        log.warn(
            "[ircafe] prepareChannelDockDrag failed: recognizer not created target={}", target);
        return;
      }
      preparedTreeDragRecognizer = recognizer;
      preparedTreeDragGestureListener = dragGestureListener;
      preparedTreeDragTarget = target;
      primeAlternateDragGesture(recognizer, event);
    } catch (Exception ex) {
      log.warn(
          "[ircafe] prepareChannelDockDrag failed to create drag recognizer target={}", target, ex);
      clearPreparedChannelDockDrag();
    }
  }

  public void clearPreparedChannelDockDrag() {
    DragGestureRecognizer recognizer = preparedTreeDragRecognizer;
    DragGestureListener dragGestureListener = preparedTreeDragGestureListener;
    TargetRef target = preparedTreeDragTarget;
    preparedTreeDragRecognizer = null;
    preparedTreeDragGestureListener = null;
    preparedTreeDragTarget = null;

    if (recognizer == null || dragGestureListener == null) return;
    try {
      recognizer.removeDragGestureListener(dragGestureListener);
    } catch (Exception ex) {
      log.warn("[ircafe] failed clearing prepared channel drag target={}", target, ex);
    }
  }

  private TargetRef channelTargetForEvent(MouseEvent event) {
    if (event == null) return null;
    TargetRef target;
    try {
      target = channelTargetForHit.apply(event.getX(), event.getY());
    } catch (Exception ex) {
      log.warn(
          "[ircafe] failed resolving channel target for prepared drag x={} y={}",
          event.getX(),
          event.getY(),
          ex);
      return null;
    }
    if (!isChannelTarget(target)) return null;
    return target;
  }

  private boolean shouldStartPreparedChannelDockDrag(DragGestureEvent dragGestureEvent) {
    if (dragGestureEvent == null) return false;
    InputEvent triggerEvent = dragGestureEvent.getTriggerEvent();
    if (triggerEvent instanceof MouseEvent mouseEvent
        && mouseEvent.getID() == MouseEvent.MOUSE_DRAGGED) {
      return true;
    }

    if (!(triggerEvent instanceof MouseEvent mouseEvent)) return false;

    boolean leftButtonDown = (mouseEvent.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0;
    double pointerDistancePx = pointerDistanceFromDragOrigin(dragGestureEvent);
    int thresholdPx = preparedChannelDragThresholdPx();
    return leftButtonDown && pointerDistancePx >= thresholdPx;
  }

  private int preparedChannelDragThresholdPx() {
    try {
      return Math.max(1, DragSource.getDragThreshold());
    } catch (Exception ignored) {
      return PREPARED_CHANNEL_DRAG_THRESHOLD_FALLBACK_PX;
    }
  }

  private double pointerDistanceFromDragOrigin(DragGestureEvent dragGestureEvent) {
    if (dragGestureEvent == null) return -1.0d;
    Point origin = dragGestureEvent.getDragOrigin();
    Component component = dragGestureEvent.getComponent();
    if (origin == null || component == null) return -1.0d;
    try {
      Point pointer = pointerLocationInComponent(component);
      if (pointer == null) return -1.0d;
      return origin.distance(pointer);
    } catch (Exception ignored) {
      return -1.0d;
    }
  }

  private Point pointerLocationInComponent(Component component) {
    if (component == null) return null;
    var pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo == null || pointerInfo.getLocation() == null) return null;
    Point pointer = new Point(pointerInfo.getLocation());
    SwingUtilities.convertPointFromScreen(pointer, component);
    return pointer;
  }

  private void primeAlternateDragGesture(DragGestureRecognizer recognizer, MouseEvent pressEvent) {
    if (recognizer == null || pressEvent == null) return;
    try {
      if (recognizer instanceof MouseListener mouseListener) {
        mouseListener.mousePressed(pressEvent);
      } else {
        log.warn(
            "[ircafe] prepared drag recognizer is not MouseListener: {}",
            recognizer.getClass().getName());
      }
    } catch (Exception ex) {
      log.warn("[ircafe] failed to prime prepared drag recognizer", ex);
    }
  }

  private Dockable ensurePinnedDockableForDrag(TargetRef target) {
    if (!isChannelTarget(target)) return null;
    Function<TargetRef, Dockable> provider = pinnedDockableProvider;
    if (provider == null) return null;
    try {
      return provider.apply(target);
    } catch (Exception ex) {
      log.warn("[ircafe] failed to ensure pinned dockable for drag target={}", target, ex);
      return null;
    }
  }

  private void beginPinnedDockDrag(Dockable dockable, DragGestureEvent dragGestureEvent) {
    beginPinnedDockDrag(dockable, dragGestureEvent, 0);
  }

  private void beginPinnedDockDrag(
      Dockable dockable, DragGestureEvent dragGestureEvent, int attemptNumber) {
    if (dockable == null || dragGestureEvent == null) return;
    try {
      try {
        Docking.display(dockable);
      } catch (Exception ignored) {
      }
      DockingAPI dockingApi = Docking.getSingleInstance();
      DockingInternal internals = DockingInternal.get(dockingApi);
      if (internals == null) {
        log.warn("[ircafe] beginPinnedDockDrag aborted: DockingInternal unavailable");
        return;
      }
      DockableWrapper wrapper = internals.getWrapper(dockable);
      if (wrapper == null) {
        log.warn(
            "[ircafe] beginPinnedDockDrag aborted: no DockableWrapper for {}",
            dockable.getPersistentID());
        return;
      }
      FloatListener floatListener = wrapper.getFloatListener();
      if (floatListener == null) {
        log.warn(
            "[ircafe] beginPinnedDockDrag aborted: no FloatListener for {}",
            dockable.getPersistentID());
        return;
      }
      DragGestureEvent compatibleEvent =
          compatibleDragGestureEvent(floatListener, dragGestureEvent);
      floatListener.startDrag(compatibleEvent);
      boolean floatingAfter = Floating.isFloating();
      if (!floatingAfter) {
        if (attemptNumber == 0) {
          boolean floatedViaFloatSource =
              retryPinnedDockDragWithFloatSource(floatListener, dragGestureEvent, dockable);
          if (floatedViaFloatSource || Floating.isFloating()) return;
          log.warn(
              "[ircafe] beginPinnedDockDrag did not enter floating mode for {}; scheduling one retry",
              dockable.getPersistentID());
          SwingUtilities.invokeLater(
              () -> beginPinnedDockDrag(dockable, dragGestureEvent, attemptNumber + 1));
        } else {
          log.warn(
              "[ircafe] beginPinnedDockDrag did not enter floating mode for {} after retry",
              dockable.getPersistentID());
        }
      }
    } catch (Exception ex) {
      log.warn(
          "[ircafe] failed to begin pinned dock drag dockable={}", dockable.getPersistentID(), ex);
    }
  }

  private boolean retryPinnedDockDragWithFloatSource(
      FloatListener floatListener, DragGestureEvent seedEvent, Dockable dockable) {
    if (floatListener == null || seedEvent == null || dockable == null) return false;
    DragSource floatDragSource = floatListenerDragSource(floatListener);
    Component sourceComponent = seedEvent.getComponent();
    if (sourceComponent == null) {
      sourceComponent = tree;
    }
    if (floatDragSource == null || sourceComponent == null) {
      return false;
    }

    DragGestureListener retryListener =
        retryEvent -> {
          if (retryEvent != null) {
            floatListener.startDrag(retryEvent);
          }
        };

    DragGestureRecognizer retryRecognizer = null;
    try {
      retryRecognizer =
          floatDragSource.createDefaultDragGestureRecognizer(
              sourceComponent, DnDConstants.ACTION_MOVE, retryListener);
      if (retryRecognizer == null) {
        return false;
      }

      MouseEvent pressEvent = seedPressEventForRecognizer(seedEvent, sourceComponent);
      if (pressEvent == null) return false;
      if (retryRecognizer instanceof MouseListener mouseListener) {
        mouseListener.mousePressed(pressEvent);
      }

      MouseEvent dragEvent = seedDragEventForRecognizer(seedEvent, sourceComponent, pressEvent);
      if (dragEvent != null
          && retryRecognizer instanceof java.awt.event.MouseMotionListener motion) {
        motion.mouseDragged(dragEvent);
      }

      boolean floatingAfter = Floating.isFloating();
      return floatingAfter;
    } catch (Exception ex) {
      log.warn("[ircafe] float-source retry failed for {}", dockable.getPersistentID(), ex);
      return false;
    } finally {
      if (retryRecognizer != null) {
        try {
          retryRecognizer.removeDragGestureListener(retryListener);
        } catch (Exception ignored) {
        }
      }
    }
  }

  private MouseEvent seedPressEventForRecognizer(
      DragGestureEvent seedEvent, Component sourceComponent) {
    if (seedEvent == null || sourceComponent == null) return null;
    InputEvent triggerEvent = seedEvent.getTriggerEvent();
    int modifiers = InputEvent.BUTTON1_DOWN_MASK;
    long when = System.currentTimeMillis();
    Point point = null;
    if (triggerEvent instanceof MouseEvent triggerMouse) {
      modifiers |= triggerMouse.getModifiersEx();
      when = triggerMouse.getWhen();
      point = eventPointInComponent(triggerMouse, sourceComponent);
    }
    if (point == null) {
      point = pointerLocationInComponent(sourceComponent);
    }
    if (point == null) return null;
    return new MouseEvent(
        sourceComponent,
        MouseEvent.MOUSE_PRESSED,
        when,
        modifiers,
        point.x,
        point.y,
        1,
        false,
        MouseEvent.BUTTON1);
  }

  private MouseEvent seedDragEventForRecognizer(
      DragGestureEvent seedEvent, Component sourceComponent, MouseEvent pressEvent) {
    if (seedEvent == null || sourceComponent == null) return null;
    Point dragPoint = pointerLocationInComponent(sourceComponent);
    if (dragPoint == null && pressEvent != null) {
      dragPoint = new Point(pressEvent.getPoint());
    }
    if (dragPoint == null) return null;

    Point basePoint = pressEvent != null ? pressEvent.getPoint() : new Point(dragPoint);
    int minDistance = preparedChannelDragThresholdPx() + 2;
    if (basePoint.distance(dragPoint) < minDistance) {
      dragPoint = new Point(basePoint.x + minDistance, basePoint.y + minDistance);
    }
    int maxX = Math.max(0, sourceComponent.getWidth() - 1);
    int maxY = Math.max(0, sourceComponent.getHeight() - 1);
    dragPoint.x = Math.max(0, Math.min(maxX, dragPoint.x));
    dragPoint.y = Math.max(0, Math.min(maxY, dragPoint.y));

    int modifiers = InputEvent.BUTTON1_DOWN_MASK;
    InputEvent triggerEvent = seedEvent.getTriggerEvent();
    if (triggerEvent instanceof MouseEvent triggerMouse) {
      modifiers |= triggerMouse.getModifiersEx();
    }

    return new MouseEvent(
        sourceComponent,
        MouseEvent.MOUSE_DRAGGED,
        System.currentTimeMillis(),
        modifiers,
        dragPoint.x,
        dragPoint.y,
        1,
        false,
        MouseEvent.BUTTON1);
  }

  private Point eventPointInComponent(MouseEvent sourceEvent, Component targetComponent) {
    if (sourceEvent == null || targetComponent == null) return null;
    Point point = new Point(sourceEvent.getPoint());
    Component sourceComponent = sourceEvent.getComponent();
    if (sourceComponent != null) {
      SwingUtilities.convertPointToScreen(point, sourceComponent);
      SwingUtilities.convertPointFromScreen(point, targetComponent);
      return point;
    }
    return point;
  }

  private DragGestureEvent compatibleDragGestureEvent(
      FloatListener floatListener, DragGestureEvent originalEvent) {
    if (floatListener == null || originalEvent == null) return originalEvent;
    DragSource expectedSource = floatListenerDragSource(floatListener);
    if (expectedSource == null) {
      log.warn("[ircafe] drag-event adaptation skipped: float listener drag source unavailable");
      return originalEvent;
    }
    if (expectedSource == originalEvent.getDragSource()) {
      return originalEvent;
    }
    Component component = floatListenerDragComponent(floatListener);
    if (component == null) {
      component = originalEvent.getComponent();
    }
    if (component == null) {
      log.warn("[ircafe] drag-event adaptation skipped: missing source component");
      return originalEvent;
    }

    int action = originalEvent.getDragAction();
    if (action == DnDConstants.ACTION_NONE) {
      action = DnDConstants.ACTION_MOVE;
    }

    java.awt.Point originScreen = originalEvent.getDragOrigin();
    if (originScreen == null) {
      originScreen = new java.awt.Point(0, 0);
    }
    Component originalComponent = originalEvent.getComponent();
    if (originalComponent != null) {
      SwingUtilities.convertPointToScreen(originScreen, originalComponent);
    }

    java.awt.Point origin = new java.awt.Point(originScreen);
    SwingUtilities.convertPointFromScreen(origin, component);

    ArrayList<InputEvent> events = new ArrayList<>(1);
    InputEvent trigger = originalEvent.getTriggerEvent();
    boolean triggerIsMouseDragged =
        trigger instanceof MouseEvent mouseEvent && mouseEvent.getID() == MouseEvent.MOUSE_DRAGGED;
    if (!triggerIsMouseDragged) {
      Point livePointer = pointerLocationInComponent(component);
      if (livePointer != null) {
        origin = livePointer;
      }
    }
    int triggerModifiers =
        trigger instanceof MouseEvent mouseEvent
            ? mouseEvent.getModifiersEx()
            : InputEvent.BUTTON1_DOWN_MASK;
    triggerModifiers |= InputEvent.BUTTON1_DOWN_MASK;
    long triggerWhen =
        trigger instanceof MouseEvent mouseEvent
            ? mouseEvent.getWhen()
            : System.currentTimeMillis();
    if (!triggerIsMouseDragged) {
      triggerWhen = System.currentTimeMillis();
    }
    events.add(
        new MouseEvent(
            component,
            MouseEvent.MOUSE_DRAGGED,
            triggerWhen,
            triggerModifiers,
            origin.x,
            origin.y,
            1,
            false,
            MouseEvent.BUTTON1));

    try {
      DragGestureRecognizer recognizer =
          new SyntheticDragGestureRecognizer(expectedSource, component, action);
      return new DragGestureEvent(recognizer, action, origin, events);
    } catch (Exception ex) {
      log.warn("[ircafe] failed to adapt drag gesture event; using original event", ex);
      return originalEvent;
    }
  }

  private DragSource floatListenerDragSource(FloatListener floatListener) {
    try {
      var field = FloatListener.class.getDeclaredField("dragSource");
      field.setAccessible(true);
      Object value = field.get(floatListener);
      return value instanceof DragSource dragSource ? dragSource : null;
    } catch (Exception ex) {
      log.warn("[ircafe] could not access float listener drag source", ex);
      return null;
    }
  }

  private Component floatListenerDragComponent(FloatListener floatListener) {
    try {
      var field = FloatListener.class.getDeclaredField("dragComponent");
      field.setAccessible(true);
      Object value = field.get(floatListener);
      return value instanceof Component component ? component : null;
    } catch (Exception ex) {
      log.warn("[ircafe] could not access float listener drag component", ex);
      return null;
    }
  }

  private static final class SyntheticDragGestureRecognizer extends DragGestureRecognizer {
    SyntheticDragGestureRecognizer(DragSource dragSource, Component component, int sourceActions) {
      super(dragSource, component, sourceActions);
    }

    @Override
    protected void registerListeners() {
      // No-op: synthetic recognizer used only to carry a compatible DragGestureEvent.
    }

    @Override
    protected void unregisterListeners() {
      // No-op: synthetic recognizer used only to carry a compatible DragGestureEvent.
    }
  }

  private static boolean isChannelTarget(TargetRef ref) {
    return ServerTreeConventions.isChannelTarget(ref);
  }
}
