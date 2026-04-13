package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalBrowserLauncherTest {

  @Test
  void linuxStartsWithXdgOpen() {
    TestableLauncher launcher = new TestableLauncher("linux");
    launcher.succeedCommandPrefix = "xdg-open";

    assertTrue(launcher.open("https://example.com/releases"));
    assertEquals("xdg-open https://example.com/releases", launcher.attemptedCommands.get(0));
    assertFalse(launcher.desktopBrowseCalled);
    assertFalse(launcher.attemptedCommands.stream().anyMatch(cmd -> cmd.startsWith("firefox ")));
  }

  @Test
  void linuxOnlyTriesKnownBrowserAfterXdgOpenPathFails() {
    TestableLauncher launcher = new TestableLauncher("linux");
    launcher.succeedCommandPrefix = "firefox";

    assertTrue(launcher.open("https://example.com/releases"));
    assertTrue(
        indexOfPrefix(launcher.attemptedCommands, "xdg-open ")
            < indexOfPrefix(launcher.attemptedCommands, "firefox "));
  }

  @Test
  void linuxFallsBackToDesktopWhenCommandPathFails() {
    TestableLauncher launcher = new TestableLauncher("linux");
    launcher.desktopBrowseResult = true;

    assertTrue(launcher.open("https://example.com/releases"));
    assertTrue(launcher.desktopBrowseCalled);
  }

  @Test
  void openSanitizesBareWwwLinksBeforeLaunching() {
    TestableLauncher launcher = new TestableLauncher("linux");
    launcher.succeedCommandPrefix = "xdg-open";

    assertTrue(launcher.open("www.example.com/docs)."));
    assertEquals("xdg-open https://www.example.com/docs", launcher.attemptedCommands.get(0));
  }

  private static int indexOfPrefix(List<String> commands, String prefix) {
    for (int i = 0; i < commands.size(); i++) {
      if (commands.get(i).startsWith(prefix)) return i;
    }
    return Integer.MAX_VALUE;
  }

  private static final class TestableLauncher extends ExternalBrowserLauncher {
    private final String osLower;
    private final List<String> attemptedCommands = new ArrayList<>();
    private boolean desktopBrowseCalled;
    private boolean desktopBrowseResult;
    private String succeedCommandPrefix;

    private TestableLauncher(String osLower) {
      this.osLower = osLower;
    }

    @Override
    protected String currentOsLowerCase() {
      return osLower;
    }

    @Override
    protected boolean tryDesktopBrowse(String url) {
      desktopBrowseCalled = true;
      return desktopBrowseResult;
    }

    @Override
    protected boolean tryStart(String... cmd) {
      String joined = String.join(" ", Arrays.asList(cmd));
      attemptedCommands.add(joined);
      return succeedCommandPrefix != null && cmd.length > 0 && cmd[0].equals(succeedCommandPrefix);
    }
  }
}
