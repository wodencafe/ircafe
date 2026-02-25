package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrSnapshotSummarizerTest {

  @TempDir Path tempDir;

  @Test
  void invalidSnapshotFileReturnsParseFailureSummary() throws Exception {
    Path fakeSnapshot = tempDir.resolve("fake.jfr");
    Files.writeString(fakeSnapshot, "not-a-jfr", StandardCharsets.UTF_8);

    JfrSnapshotSummarizer summarizer = new JfrSnapshotSummarizer();
    String summary = summarizer.summarize(fakeSnapshot);

    assertTrue(summary.contains("Snapshot captured at: " + fakeSnapshot.toAbsolutePath()));
    assertTrue(summary.contains("Could not parse snapshot:"));
  }
}
