package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatLogDatabaseConfigTest {

  @TempDir Path tempDir;

  @Test
  void detectsRecoverableLockFailureFromNestedCauseChain() {
    Throwable failure =
        new RuntimeException(
            "wrapper",
            new RuntimeException(
                "Database lock acquisition failure",
                new RuntimeException("org.hsqldb.persist.LockFile$LockHeldExternallyException")));

    assertTrue(ChatLogDatabaseConfig.isRecoverableLockFailure(failure));
  }

  @Test
  void doesNotTreatNonLockFailuresAsRecoverable() {
    Throwable failure = new RuntimeException("Database lock acquisition failure");

    assertFalse(ChatLogDatabaseConfig.isRecoverableLockFailure(failure));
  }

  @Test
  void staleLockRecoveryDeletesUnlockedLockFile() throws Exception {
    Path lockPath = tempDir.resolve("ircafe-chatlog.lck");
    Files.writeString(lockPath, "stale");

    assertTrue(ChatLogDatabaseConfig.tryRecoverStaleLockFile(lockPath));
    assertFalse(Files.exists(lockPath));
  }

  @Test
  void staleLockRecoverySkipsActivelyLockedFile() throws Exception {
    Path lockPath = tempDir.resolve("ircafe-chatlog.lck");
    Files.writeString(lockPath, "active");

    try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
        FileLock ignored = channel.lock()) {
      assertFalse(ChatLogDatabaseConfig.tryRecoverStaleLockFile(lockPath));
      assertTrue(Files.exists(lockPath));
    }
  }
}
