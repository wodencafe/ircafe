package cafe.woden.ircclient.ui.chat.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ReadMarkerFeatureSupport;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.logging.NoOpChatRedactionAuditService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.ExternalBrowserLauncher;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import cafe.woden.ircclient.ui.backend.BackendUiProfile;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsTestFixtures;
import io.github.andrewauclair.moderndocking.app.Docking;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedChatNickContextMenuFunctionalTest {

  @Test
  void separateBufferUsesItsOwnTargetForNickActionsAndReleasesProviderOnClose() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture fixture = createFixture();
          try {
            JPopupMenu popup = fixture.dock.nickContextMenuFor("alice");
            assertNotNull(popup);
            findItem(popup, "Whois").doClick(0);
            findItem(popup, "Open Query").doClick(0);
            findItem(popup, "Kick").doClick(0);
            verify(fixture.callbacks)
                .emitUserAction(fixture.target, "alice", UserActionRequest.Action.WHOIS);
            verify(fixture.callbacks)
                .emitUserAction(fixture.target, "alice", UserActionRequest.Action.KICK);
            verify(fixture.callbacks).openQuery(fixture.target, "alice");
            verifyNoInteractions(fixture.activationBus);
          } finally {
            fixture.dock.close();
          }
          assertNull(fixture.dock.nickContextMenuFor("alice"));
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED})
  void rightClickNickShowsWhoisMenuInSeparateWindow(int eventId) throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a desktop or xvfb-run");
    Fixture[] fixtureRef = new Fixture[1];
    JFrame[] frameRef = new JFrame[1];
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            Fixture fixture = createFixture();
            fixtureRef[0] = fixture;
            JFrame frame = new JFrame("Separate chat buffer");
            frameRef[0] = frame;
            frame.add(fixture.dock);
            frame.setSize(600, 400);
            frame.setVisible(true);
          });
      SwingUtilities.invokeAndWait(
          () -> {
            Fixture fixture = fixtureRef[0];
            try {
              Rectangle2D nickBounds = fixture.dock.chat.modelToView2D(2);
              assertNotNull(nickBounds);
              fixture.dock.chat.dispatchEvent(
                  new MouseEvent(
                      fixture.dock.chat,
                      eventId,
                      System.currentTimeMillis(),
                      0,
                      (int) nickBounds.getX(),
                      (int) nickBounds.getCenterY(),
                      1,
                      true,
                      MouseEvent.BUTTON3));
              JPopupMenu popup = selectedPopup();
              assertNotNull(popup);
              assertSame(fixture.dock.chat, popup.getInvoker());
              JMenuItem whois = findItem(popup, "Whois");
              assertNotNull(whois);
              assertTrue(whois.isEnabled());
              whois.doClick(0);
              verify(fixture.callbacks)
                  .emitUserAction(fixture.target, "alice", UserActionRequest.Action.WHOIS);
            } catch (Exception failure) {
              throw new AssertionError(failure);
            }
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            if (fixtureRef[0] != null) fixtureRef[0].dock.close();
            if (frameRef[0] != null) frameRef[0].dispose();
          });
    }
  }

  private static Fixture createFixture() {
    TargetRef target = new TargetRef("quassel", TargetRef.withNetworkQualifier("#pinned", "7"));
    ChatDockable mainChat = mock(ChatDockable.class);
    NickContextMenuFactory.Callbacks callbacks = mock(NickContextMenuFactory.Callbacks.class);
    NickContextMenuFactory.NickContextMenu menu = new NickContextMenuFactory().create(callbacks);
    when(mainChat.nickContextMenuFor(target, "alice"))
        .thenAnswer(
            ignored ->
                menu.forNick(target, "alice", new NickContextMenuFactory.IgnoreMark(false, false)));
    UiSettingsBus settings = mock(UiSettingsBus.class);
    when(settings.get()).thenReturn(UiSettingsTestFixtures.builder().build());
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    DefaultStyledDocument document = new DefaultStyledDocument();
    SimpleAttributeSet nickAttrs = new SimpleAttributeSet();
    nickAttrs.addAttribute(NickColorService.ATTR_NICK, "alice");
    try {
      document.insertString(0, "alice", nickAttrs);
      document.insertString(document.getLength(), ": hello\n", null);
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
    when(transcripts.document(target)).thenReturn(document);
    BackendUiProfileProvider profiles = mock(BackendUiProfileProvider.class);
    when(profiles.profileForServer(target.serverId()))
        .thenReturn(BackendUiProfile.ircOnly(target.serverId()));
    TargetActivationBus activationBus = mock(TargetActivationBus.class);
    ChatDockManager manager =
        new ChatDockManager(
            mock(ServerTreeDockable.class),
            mainChat,
            transcripts,
            activationBus,
            settings,
            null,
            new OutboundLineBus(),
            mock(IrcTypingPort.class),
            mock(Ircv3ReadMarkerFeatureSupport.class),
            mock(IrcCurrentNickPort.class),
            profiles,
            mock(MessageActionCapabilityPolicy.class),
            null,
            null,
            null,
            new NoOpChatRedactionAuditService(),
            null,
            new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty()),
            mock(InstalledPluginsPort.class),
            null,
            mock(CommandHistoryStore.class),
            mock(ExternalBrowserLauncher.class));
    try (var docking = mockStatic(Docking.class)) {
      String id = "chat-pinned:" + b64(target.serverId()) + ":" + b64(target.key());
      PinnedChatDockable dock = (PinnedChatDockable) manager.dynamicDockableForPersistentId(id);
      assertNotNull(dock);
      return new Fixture(dock, target, callbacks, activationBus);
    }
  }

  private static JPopupMenu selectedPopup() {
    for (var element : MenuSelectionManager.defaultManager().getSelectedPath()) {
      if (element instanceof JPopupMenu popup) return popup;
    }
    return null;
  }

  private static JMenuItem findItem(JPopupMenu popup, String text) {
    for (Component component : popup.getComponents()) {
      if (component instanceof JMenuItem item && text.equals(item.getText())) return item;
    }
    return null;
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private record Fixture(
      PinnedChatDockable dock,
      TargetRef target,
      NickContextMenuFactory.Callbacks callbacks,
      TargetActivationBus activationBus) {}
}
