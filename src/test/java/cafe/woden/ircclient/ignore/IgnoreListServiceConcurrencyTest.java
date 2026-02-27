package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IgnoreListServiceConcurrencyTest {

  @TempDir Path tempDir;

  @Test
  void concurrentAddRemoveAndListOperationsRemainConsistent() throws Exception {
    IgnoreListService service = newService(tempDir.resolve("ignore-concurrency.yml"));
    IgnoreListQueryPort queryPort = service;
    IgnoreListCommandPort commandPort = service;

    int workers = 8;
    int loopsPerWorker = 200;
    ExecutorService exec = Executors.newFixedThreadPool(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    for (int worker = 0; worker < workers; worker++) {
      final int workerId = worker;
      futures.add(
          exec.submit(
              () -> {
                start.await();
                for (int i = 0; i < loopsPerWorker; i++) {
                  String mask = "nick" + ((workerId + i) % 25);
                  commandPort.addMask("libera", mask);
                  if (((i + workerId) % 3) == 0) {
                    commandPort.removeMask("libera", mask);
                  }
                  // Exercise read path concurrently while writes are happening.
                  queryPort.listMasks("libera");
                }
                return null;
              }));
    }

    start.countDown();
    for (Future<?> future : futures) {
      future.get(10, TimeUnit.SECONDS);
    }
    exec.shutdownNow();

    List<String> masks = queryPort.listMasks("libera");
    Set<String> uniqueLower = new java.util.HashSet<>();
    for (String mask : masks) {
      String normalized = String.valueOf(mask).trim();
      assertFalse(normalized.isEmpty());
      uniqueLower.add(normalized.toLowerCase(Locale.ROOT));
    }
    assertEquals(uniqueLower.size(), masks.size());
  }

  private static IgnoreListService newService(Path configPath) {
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(
            configPath.toString(),
            new IrcProperties(
                null,
                List.of(
                    new IrcProperties.Server(
                        "libera",
                        "irc.example.net",
                        6697,
                        true,
                        "",
                        "ircafe",
                        "ircafe",
                        "IRCafe User",
                        null,
                        List.of(),
                        List.of(),
                        null))));
    return new IgnoreListService(new IgnoreProperties(true, false, Map.of()), runtimeConfig);
  }
}
