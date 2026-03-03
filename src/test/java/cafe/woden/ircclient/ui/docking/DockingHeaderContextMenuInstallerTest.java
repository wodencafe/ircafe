package cafe.woden.ircclient.ui.docking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.andrewauclair.moderndocking.Dockable;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class DockingHeaderContextMenuInstallerTest {

  @Test
  void buildTabPopupMenuEnablesCloseWhenAllowed() {
    Dockable dockable = mock(Dockable.class);
    when(dockable.isClosable()).thenReturn(true);
    when(dockable.requestClose()).thenReturn(true);
    Runnable closeAction = mock(Runnable.class);

    JPopupMenu menu = DockingHeaderContextMenuInstaller.buildTabPopupMenu(dockable, closeAction);
    JMenuItem close = (JMenuItem) menu.getComponent(0);

    assertEquals("Close", close.getText());
    assertTrue(close.isEnabled());
    close.doClick();
    verify(closeAction).run();
  }

  @Test
  void buildTabPopupMenuDisablesCloseWhenNotAllowed() {
    Dockable dockable = mock(Dockable.class);
    when(dockable.isClosable()).thenReturn(false);
    Runnable closeAction = mock(Runnable.class);

    JPopupMenu menu = DockingHeaderContextMenuInstaller.buildTabPopupMenu(dockable, closeAction);
    JMenuItem close = (JMenuItem) menu.getComponent(0);

    assertFalse(close.isEnabled());
    close.doClick();
    verifyNoInteractions(closeAction);
  }

  @Test
  void buildTabPopupMenuDoesNotRunCloseActionWhenRequestCloseRejected() {
    Dockable dockable = mock(Dockable.class);
    when(dockable.isClosable()).thenReturn(true);
    when(dockable.requestClose()).thenReturn(false);
    Runnable closeAction = mock(Runnable.class);

    JPopupMenu menu = DockingHeaderContextMenuInstaller.buildTabPopupMenu(dockable, closeAction);
    JMenuItem close = (JMenuItem) menu.getComponent(0);

    assertTrue(close.isEnabled());
    close.doClick();
    verifyNoInteractions(closeAction);
    verify(dockable).requestClose();
  }
}
