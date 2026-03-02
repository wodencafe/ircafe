package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModePrettyPrinterTest {

  @Test
  void formatsCommonPrivilegeModeChangesDeterministically() {
    List<String> lines = ModePrettyPrinter.pretty("ChanServ", "#ircafe", "+o alice");

    assertEquals(List.of("ChanServ gives channel operator privileges to alice."), lines);
  }

  @Test
  void parsesRawModeLineAndRecoversActorPrefix() {
    List<String> lines =
        ModePrettyPrinter.pretty("", "#ircafe", ":Oper!user@host MODE #ircafe +b trouble!*@*");

    assertEquals(1, lines.size());
    assertEquals("Oper adds a ban on trouble!*@*.", lines.getFirst());
  }

  @Test
  void suppressesEchoedChannelModesWhenPrivilegeModesShareTheSameCommand() {
    List<String> lines = ModePrettyPrinter.pretty("alice", "#ircafe", "+ovnt bob carol");

    assertEquals(2, lines.size());
    assertTrue(lines.contains("alice gives channel operator privileges to bob."));
    assertTrue(lines.contains("alice gives voice privileges to carol."));
  }

  @Test
  void formatsQModeAsQuietWhenTargetLooksLikeMask() {
    List<String> lines =
        ModePrettyPrinter.pretty("chat", "#ircafe", "-q *!*@brady@63-230-166-5.ptld.qwest.net");

    assertEquals(
        List.of("chat removes a quiet rule for *!*@brady@63-230-166-5.ptld.qwest.net."), lines);
  }

  @Test
  void keepsQModeAsOwnerWhenTargetLooksLikeNick() {
    List<String> lines = ModePrettyPrinter.pretty("ChanServ", "#ircafe", "+q alice");

    assertEquals(List.of("ChanServ gives owner privileges to alice."), lines);
  }
}
