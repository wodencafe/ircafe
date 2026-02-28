package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class UserListDockableFunctionalTest {

  @Test
  void selectionAndDoubleClickRoutePrivateMessage() throws Exception {
    UserListDockable dockable = newDockable();
    CopyOnWriteArrayList<PrivateMessageRequest> requests = new CopyOnWriteArrayList<>();
    Disposable sub = dockable.privateMessageRequests().subscribe(requests::add);

    try {
      TargetRef channel = new TargetRef("libera", "#ircafe");
      JList<?> list = readField(dockable, "list", JList.class);

      onEdt(
          () -> {
            dockable.setChannel(channel);
            dockable.setNicks(List.of(new NickInfo("alice", "@", "alice!u@h")));
            list.setSize(240, 120);
            list.doLayout();
            list.setSelectedIndex(0);
          });
      flushEdt();

      assertEquals("alice", onEdtCall(dockable::selectedNick));

      onEdt(
          () -> {
            Rectangle cell = list.getCellBounds(0, 0);
            int x = cell.x + Math.max(2, cell.width / 3);
            int y = cell.y + Math.max(2, cell.height / 2);
            MouseEvent dblClick =
                new MouseEvent(
                    list,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    x,
                    y,
                    2,
                    false,
                    MouseEvent.BUTTON1);
            list.dispatchEvent(dblClick);
          });
      waitFor(() -> !requests.isEmpty(), Duration.ofSeconds(2));

      assertEquals(new PrivateMessageRequest("libera", "alice"), requests.getLast());
    } finally {
      sub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void contextActionPublishesUserActionRequest() throws Exception {
    UserListDockable dockable = newDockable();
    CopyOnWriteArrayList<UserActionRequest> actions = new CopyOnWriteArrayList<>();
    Disposable sub = dockable.userActionRequests().subscribe(actions::add);

    try {
      TargetRef channel = new TargetRef("libera", "#ircafe");
      onEdt(
          () -> {
            dockable.setChannel(channel);
            dockable.setNicks(List.of(new NickInfo("alice", "", "alice!u@h")));
          });
      flushEdt();

      Object menuObj = readField(dockable, "nickContextMenu", Object.class);
      assertNotNull(menuObj, "nick context menu should be available");

      JPopupMenu menu =
          onEdtCall(
              () -> {
                java.lang.reflect.Method forNick =
                    menuObj
                        .getClass()
                        .getDeclaredMethod(
                            "forNick",
                            TargetRef.class,
                            String.class,
                            NickContextMenuFactory.IgnoreMark.class);
                return (JPopupMenu)
                    forNick.invoke(
                        menuObj,
                        channel,
                        "alice",
                        new NickContextMenuFactory.IgnoreMark(false, false));
              });

      JMenuItem whois = findMenuItem(menu, "Whois");
      assertNotNull(whois, "whois action should exist");
      onEdt(whois::doClick);

      waitFor(() -> !actions.isEmpty(), Duration.ofSeconds(2));
      UserActionRequest request = actions.getLast();
      assertEquals(UserActionRequest.Action.WHOIS, request.action());
      assertEquals("alice", request.nick());
      assertEquals(channel, request.contextTarget());
    } finally {
      sub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  private static UserListDockable newDockable() {
    NickColorService colors = mock(NickColorService.class);
    when(colors.enabled()).thenReturn(false);
    return new UserListDockable(
        colors,
        mock(NickColorSettingsBus.class),
        null,
        null,
        null,
        new TargetActivationBus(),
        new OutboundLineBus(),
        new NickContextMenuFactory());
  }

  private static JMenuItem findMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component c : menu.getComponents()) {
      if (c instanceof JMenuItem item && text.equals(item.getText())) return item;
    }
    return null;
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
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
    AtomicReference<T> out = new AtomicReference<>();
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

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
