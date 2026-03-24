package cafe.woden.ircclient.irc.pircbotx.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PircbotxChannelMode324DeduperTest {

  @Test
  void duplicateClaimIsRejectedWithinTtl() {
    PircbotxChannelMode324Deduper deduper = new PircbotxChannelMode324Deduper();

    assertTrue(deduper.tryClaim("#ircafe", "+nt", 1_000L));
    assertFalse(deduper.tryClaim("#ircafe", "+nt", 2_000L));
    assertTrue(deduper.tryClaim("#ircafe", "+nt", 4_001L));
  }

  @Test
  void channelCaseAndWhitespaceNormalizeToSameDedupKey() {
    PircbotxChannelMode324Deduper deduper = new PircbotxChannelMode324Deduper();

    assertTrue(deduper.tryClaim("#IRCafe", "+nt   key", 1_000L));
    assertFalse(deduper.tryClaim("#ircafe", "+nt key", 1_500L));
  }

  @Test
  void blankChannelOrDetailsBypassesDedup() {
    PircbotxChannelMode324Deduper deduper = new PircbotxChannelMode324Deduper();

    assertTrue(deduper.tryClaim("", "+nt", 1_000L));
    assertTrue(deduper.tryClaim("#ircafe", "   ", 1_500L));
  }

  @Test
  void clearDropsPreviouslyClaimedEntries() {
    PircbotxChannelMode324Deduper deduper = new PircbotxChannelMode324Deduper();

    assertTrue(deduper.tryClaim("#ircafe", "+nt", 1_000L));
    deduper.clear();
    assertTrue(deduper.tryClaim("#ircafe", "+nt", 1_500L));
  }
}
