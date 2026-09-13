package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.InstalledPluginProblem;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ircv3InboundTagSignalRuntimeCatalogTest {

  @Test
  void loadsFocusedBuiltInRuntimeProvidersFromApplicationClasspath() {
    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.applicationClasspath();
    long observedAtMs = 1_750_000_000_000L;
    Ircv3InboundTagRequest request =
        new Ircv3InboundTagRequest(
            "PRIVMSG",
            "alice",
            "ircafe",
            List.of("#ircafe", "hello"),
            Map.ofEntries(
                Map.entry("channel-context", "#ircafe"),
                Map.entry("reply", "m-1"),
                Map.entry("draft/react", ":+1:"),
                Map.entry("draft/unreact", ":wave:"),
                Map.entry("draft/redact", "m-2"),
                Map.entry("typing", "active"),
                Map.entry("read-marker", "timestamp=123"),
                Map.entry("draft/edit", "m-3"),
                Map.entry("account", "alice-account"),
                Map.entry("batch", "history-42"),
                Map.entry("label", "request-7"),
                Map.entry("msgid", "message-8"),
                Map.entry("time", Instant.ofEpochMilli(observedAtMs - 250L).toString())),
            "",
            observedAtMs);

    assertEquals(
        List.of(
            "channel-context",
            "reply",
            "reactions",
            "message-redaction",
            "typing",
            "read-marker",
            "message-edit",
            "account-tag",
            "echo-message",
            "batch",
            "znc-playback",
            "labeled-response",
            "server-time",
            "message-id"),
        catalog.providerIds());
    assertEquals(
        List.of(
            Ircv3InboundTagSignalType.CONVERSATION_TARGET,
            Ircv3InboundTagSignalType.REPLY,
            Ircv3InboundTagSignalType.REACT,
            Ircv3InboundTagSignalType.UNREACT,
            Ircv3InboundTagSignalType.MESSAGE_REDACTION,
            Ircv3InboundTagSignalType.TYPING,
            Ircv3InboundTagSignalType.READ_MARKER,
            Ircv3InboundTagSignalType.MESSAGE_EDIT,
            Ircv3InboundTagSignalType.ACCOUNT_TAG,
            Ircv3InboundTagSignalType.HISTORY_BATCH_REFERENCE,
            Ircv3InboundTagSignalType.LABELED_RESPONSE,
            Ircv3InboundTagSignalType.SERVER_TIME,
            Ircv3InboundTagSignalType.SERVER_TIME_LAG,
            Ircv3InboundTagSignalType.MESSAGE_ID),
        catalog.parseAll(request).stream().map(Ircv3InboundTagSignal::type).toList());
  }

  @Test
  void zncPlaybackProviderSuppressesOnlySelfAuthoredBootstrapTraffic() {
    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.applicationClasspath();

    assertEquals(
        Ircv3InboundTagSignalType.HISTORY_BOOTSTRAP_SUPPRESSED,
        catalog
            .parse(
                Ircv3InboundTagOperation.HISTORY_BOOTSTRAP_SUPPRESSION,
                Ircv3InboundTagRequest.historyBootstrap("*status", "ListNetworks", true))
            .getFirst()
            .type());
    assertTrue(
        catalog
            .parse(
                Ircv3InboundTagOperation.HISTORY_BOOTSTRAP_SUPPRESSION,
                Ircv3InboundTagRequest.historyBootstrap("*status", "ListNetworks", false))
            .isEmpty());
  }

  @Test
  void echoMessageProviderPlansPrivateTargetHintsForSelfAuthoredTraffic() {
    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.applicationClasspath();

    List<Ircv3InboundTagSignal> signals =
        catalog.parse(
            Ircv3InboundTagOperation.ECHO_MESSAGE_TARGET_HINT,
            new Ircv3InboundTagRequest(
                "PRIVMSG",
                "me",
                "alice",
                List.of("alice", ":hello there"),
                Map.of("msgid", "msg-1"),
                "@msgid=msg-1 :me!u@h PRIVMSG alice :hello there",
                1_750_000_000_000L,
                List.of("me", "me_")));

    assertEquals(
        List.of(
            Ircv3InboundTagSignalType.ECHO_MESSAGE_TARGET_HINT,
            Ircv3InboundTagSignalType.ECHO_MESSAGE_KIND,
            Ircv3InboundTagSignalType.ECHO_MESSAGE_PAYLOAD),
        signals.stream().map(Ircv3InboundTagSignal::type).toList());
    assertEquals("alice", signals.getFirst().primaryValue());
    assertEquals("msg-1", signals.getFirst().secondaryValue());
    assertEquals("PRIVMSG", signals.get(1).primaryValue());
    assertEquals("hello there", signals.get(2).primaryValue());
  }

  @Test
  void labeledResponseProviderCanInterpretRawLineFallback() {
    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.applicationClasspath();

    Ircv3InboundTagSignal signal =
        catalog
            .parse(
                Ircv3InboundTagOperation.LABELED_RESPONSE,
                new Ircv3InboundTagRequest(
                    "FAIL",
                    "",
                    "",
                    List.of(),
                    Map.of(),
                    "@label=request-9 :server FAIL CHATHISTORY INVALID_PARAMS :bad"))
            .getFirst();

    assertEquals(Ircv3InboundTagSignalType.LABELED_RESPONSE, signal.type());
    assertEquals("request-9", signal.primaryValue());
    assertEquals("FAILURE", signal.secondaryValue());
  }

  @Test
  void higherPriorityProviderReplacesBuiltInOperation() {
    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.fromProviders(
            List.of(provider("built-in", 0, "built-in"), provider("plugin", 100, "plugin")));

    assertEquals(
        "plugin",
        catalog.parse(Ircv3InboundTagOperation.REPLY, request()).getFirst().primaryValue());
    assertEquals(List.of("plugin"), catalog.providerIds());
  }

  @Test
  void equalPriorityOperationConflictsAreRejected() {
    assertThrows(
        IllegalStateException.class,
        () ->
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(
                List.of(provider("one", 10, "one"), provider("two", 10, "two"))));
  }

  @Test
  void installedProviderConflictIsReportedAndBuiltInsRemainAvailable() {
    RecordingInstalledPlugins plugins =
        new RecordingInstalledPlugins(provider("conflict", 0, "conflict"));

    Ircv3InboundTagSignalRuntimeCatalog catalog =
        Ircv3InboundTagSignalRuntimeCatalog.fromInstalledServices(plugins);

    assertTrue(catalog.supports(Ircv3InboundTagOperation.REPLY));
    assertEquals(1, plugins.problems.size());
    assertTrue(plugins.problems.getFirst().summary().contains("inbound tag-signal"));
  }

  private static Ircv3InboundTagRequest request() {
    return new Ircv3InboundTagRequest(
        "PRIVMSG", "alice", "#ircafe", List.of("#ircafe", "hello"), Map.of());
  }

  private static Ircv3InboundTagSignalProvider provider(
      String providerId, int priority, String response) {
    return new Ircv3InboundTagSignalProvider() {
      @Override
      public String providerId() {
        return providerId;
      }

      @Override
      public int inboundTagPriority() {
        return priority;
      }

      @Override
      public Set<Ircv3InboundTagOperation> inboundTagOperations() {
        return Set.of(Ircv3InboundTagOperation.REPLY);
      }

      @Override
      public List<Ircv3InboundTagSignal> parse(
          Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
        return List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.REPLY, response));
      }
    };
  }

  private static final class RecordingInstalledPlugins implements InstalledPluginsPort {
    private final Ircv3InboundTagSignalProvider provider;
    private final List<InstalledPluginProblem> problems = new ArrayList<>();

    private RecordingInstalledPlugins(Ircv3InboundTagSignalProvider provider) {
      this.provider = provider;
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      ArrayList<T> services = new ArrayList<>(builtInServices);
      if (serviceType.isInstance(provider)) {
        services.add(serviceType.cast(provider));
      }
      return List.copyOf(services);
    }

    @Override
    public void recordPluginProblem(InstalledPluginProblem problem) {
      problems.add(problem);
    }
  }
}
