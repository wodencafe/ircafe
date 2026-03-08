package cafe.woden.ircclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetRefChannelSemanticsTest {

  @Test
  void matrixRoomIdIsChannel() {
    TargetRef ref = new TargetRef("matrix", "!abc123:matrix.org");

    assertTrue(ref.isChannel());
  }

  @Test
  void bangTargetWithoutRoomIdShapeIsNotChannel() {
    TargetRef ref = new TargetRef("matrix", "!not-a-room-id");

    assertFalse(ref.isChannel());
  }

  @Test
  void qualifiedChannelListTargetIsStillChannelListAndUiOnly() {
    TargetRef ref = TargetRef.channelList("quassel", "libera");

    assertTrue(ref.isChannelList());
    assertTrue(ref.isUiOnly());
    assertEquals(TargetRef.CHANNEL_LIST_TARGET, ref.baseTarget());
    assertEquals("libera", ref.networkQualifierToken());
  }

  @Test
  void qualifiedChannelLabelUsesBaseTarget() {
    TargetRef ref = new TargetRef("quassel", "#ircafe{net:libera}");

    assertTrue(ref.isChannel());
    assertEquals("#ircafe", ref.baseTarget());
    assertEquals("libera", ref.networkQualifierToken());
  }

  @Test
  void qualifiedIgnoresTargetIsUiOnlyAndKeepsBaseBuiltIn() {
    TargetRef ref = TargetRef.ignores("quassel", "libera");

    assertTrue(ref.isIgnores());
    assertTrue(ref.isUiOnly());
    assertEquals(TargetRef.IGNORES_TARGET, ref.baseTarget());
    assertEquals("libera", ref.networkQualifierToken());
  }
}
