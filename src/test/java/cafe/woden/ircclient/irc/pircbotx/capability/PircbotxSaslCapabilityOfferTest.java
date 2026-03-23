package cafe.woden.ircclient.irc.pircbotx.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PircbotxSaslCapabilityOfferTest {

  @Test
  void recognizesLsContinuationMarker() {
    PircbotxSaslCapabilityOffer offer = PircbotxSaslCapabilityOffer.parse(ImmutableList.of("*"));

    assertTrue(offer.continuationOnly());
    assertFalse(offer.saslOffered());
    assertEquals(Set.of(), offer.offeredMechanismsUpper());
  }

  @Test
  void extractsMechanismsFromSaslCapability() {
    PircbotxSaslCapabilityOffer offer =
        PircbotxSaslCapabilityOffer.parse(
            ImmutableList.of("message-tags", "sasl=plain, scram-sha-256 ,EXTERNAL"));

    assertFalse(offer.continuationOnly());
    assertTrue(offer.saslOffered());
    assertEquals(Set.of("PLAIN", "SCRAM-SHA-256", "EXTERNAL"), offer.offeredMechanismsUpper());
  }

  @Test
  void stripsLeadingColonBeforeParsingTokens() {
    PircbotxSaslCapabilityOffer offer =
        PircbotxSaslCapabilityOffer.parse(ImmutableList.of(":sasl=SCRAM-SHA-1"));

    assertTrue(offer.saslOffered());
    assertEquals(Set.of("SCRAM-SHA-1"), offer.offeredMechanismsUpper());
  }

  @Test
  void ignoresNonSaslCaps() {
    PircbotxSaslCapabilityOffer offer =
        PircbotxSaslCapabilityOffer.parse(ImmutableList.of("batch", "message-tags"));

    assertFalse(offer.continuationOnly());
    assertFalse(offer.saslOffered());
    assertEquals(Set.of(), offer.offeredMechanismsUpper());
  }
}
