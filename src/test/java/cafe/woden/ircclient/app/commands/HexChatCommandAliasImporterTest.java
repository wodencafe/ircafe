package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HexChatCommandAliasImporterTest {

  @Test
  void parsesCommandsConfAndMergesDuplicateCommandNames() {
    HexChatCommandAliasImporter.ImportResult out =
        HexChatCommandAliasImporter.parseLines(
            List.of(
                "# comment",
                "NAME SLAP",
                "CMD /me slaps %1 with %2-",
                "NAME slap",
                "CMD /msg %1 from=%e last=&1",
                "NAME shout",
                "CMD /say hi %%t %t %m %v"));

    assertEquals(2, out.aliases().size());
    assertEquals("slap", out.aliases().get(0).name());
    assertEquals("/me slaps %1 with %2-; /msg %1 from=%e last=&1", out.aliases().get(0).template());
    assertEquals("shout", out.aliases().get(1).name());
    assertEquals(
        "/say hi %%t %hexchat_time %hexchat_machine %hexchat_version",
        out.aliases().get(1).template());
    assertEquals(1, out.mergedDuplicateCommands());
    assertEquals(3, out.translatedPlaceholders());
    assertEquals(0, out.skippedInvalidEntries());
  }

  @Test
  void skipsInvalidCommandNames() {
    HexChatCommandAliasImporter.ImportResult out =
        HexChatCommandAliasImporter.parseLines(
            List.of(
                "NAME 123bad",
                "CMD /say no",
                "NAME _alsoBad",
                "CMD /say no2",
                "NAME good_name",
                "CMD /say yes"));

    assertEquals(1, out.aliases().size());
    assertEquals("good_name", out.aliases().get(0).name());
    assertEquals("/say yes", out.aliases().get(0).template());
    assertEquals(2, out.skippedInvalidEntries());
  }
}
