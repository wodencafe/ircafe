package cafe.woden.ircclient.model;

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
}
