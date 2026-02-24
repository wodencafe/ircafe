package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserCommandAliasEngineTest {

  private final UserCommandAliasesBus aliasesBus = mock(UserCommandAliasesBus.class);
  private final IrcClientService irc = mock(IrcClientService.class);
  private final UserCommandAliasEngine engine = new UserCommandAliasEngine(aliasesBus, irc);

  @Test
  void expandsPositionalAndRangePlaceholders() {
    when(aliasesBus.get())
        .thenReturn(List.of(new UserCommandAlias(true, "slap", "/me slaps %1 with %2-")));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/slap bob a large trout", new TargetRef("libera", "#ircafe"));

    assertEquals(List.of("/me slaps bob with a large trout"), out.lines());
    assertTrue(out.warnings().isEmpty());
  }

  @Test
  void expandsMultiCommandAliasesUsingSemicolonAndNewline() {
    when(aliasesBus.get())
        .thenReturn(
            List.of(
                new UserCommandAlias(true, "hi", "/msg %1 hello; /notice %1 wave\\nand smile")));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/hi alice", new TargetRef("libera", "#ircafe"));

    assertEquals(List.of("/msg alice hello", "/notice alice wave", "and smile"), out.lines());
  }

  @Test
  void expandsContextPlaceholders() {
    when(aliasesBus.get())
        .thenReturn(List.of(new UserCommandAlias(true, "where", "/msg %1 ctx=%c|%t|%s|%n")));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/where bob", new TargetRef("libera", "#ircafe"));

    assertEquals(List.of("/msg bob ctx=#ircafe|#ircafe|libera|me"), out.lines());
  }

  @Test
  void expandsHexChatNetworkAndFromEndPlaceholders() {
    when(aliasesBus.get())
        .thenReturn(List.of(new UserCommandAlias(true, "where", "/msg %1 ctx=%e|&1|&2")));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/where bob alpha omega", new TargetRef("libera", "#ircafe"));

    assertEquals(List.of("/msg bob ctx=libera|omega|alpha"), out.lines());
  }

  @Test
  void expandsHexChatCompatibilitySyntheticPlaceholders() {
    when(aliasesBus.get())
        .thenReturn(
            List.of(
                new UserCommandAlias(
                    true,
                    "meta",
                    "/msg %1 t=%hexchat_time v=%hexchat_version m=%hexchat_machine")));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/meta bob", new TargetRef("libera", "#ircafe"));

    assertEquals(1, out.lines().size());
    String line = out.lines().get(0);
    assertTrue(line.startsWith("/msg bob t="));
    assertFalse(line.contains("%hexchat_time"));
    assertFalse(line.contains("%hexchat_version"));
    assertFalse(line.contains("%hexchat_machine"));
  }

  @Test
  void expandsDollarNickWithoutPartialDollarNSubstitution() {
    when(aliasesBus.get())
        .thenReturn(List.of(new UserCommandAlias(true, "whoami", "/msg %1 me=$nick and short=$n")));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/whoami bob", new TargetRef("libera", "#ircafe"));

    assertEquals(List.of("/msg bob me=me and short=me"), out.lines());
  }

  @Test
  void detectsRecursiveAliases() {
    when(aliasesBus.get()).thenReturn(List.of(new UserCommandAlias(true, "loop", "/loop")));

    UserCommandAliasEngine.ExpansionResult out =
        engine.expand("/loop", new TargetRef("libera", "#ircafe"));

    assertTrue(out.lines().isEmpty());
    assertTrue(out.warnings().stream().anyMatch(w -> w.contains("recursion")));
  }

  @Test
  void splitExpandedCommandsSupportsEscapedSemicolon() {
    List<String> out =
        UserCommandAliasEngine.splitExpandedCommands("/msg a one\\;two; /msg b three");
    assertEquals(List.of("/msg a one;two", "/msg b three"), out);
  }
}
