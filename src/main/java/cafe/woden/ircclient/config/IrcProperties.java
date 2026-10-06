package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.properties.ConfigPropertyKeys;
import cafe.woden.ircclient.util.AppVersion;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * IRC client configuration.
 *
 * <p>Supports multiple servers via {@code irc.servers}.
 */
@ConfigurationProperties(prefix = ConfigPropertyKeys.IRC_PREFIX)
@InfrastructureLayer
public record IrcProperties(Client client, List<Server> servers) {

  /**
   * Global IRC client identity/settings.
   *
   * <p>Example YAML:
   *
   * <pre>
   * irc:
   *   client:
   *     version: "IRCafe 1.2.3"
   * </pre>
   */
  public record Client(
      String version,
      Reconnect reconnect,
      Heartbeat heartbeat,
      Proxy proxy,
      Tls tls,
      Translation translation,
      FloodProtection floodProtection) {

    /** TLS settings for outbound connections (IRC-over-TLS and HTTPS fetching). */
    public record Tls(boolean trustAllCertificates) {
      public Tls {
        // default false
      }
    }

    /**
     * Client-side translation settings.
     *
     * <p>This is deliberately backend-agnostic: {@code backendId} is a normalized identifier that a
     * later translation-service registry can resolve.
     */
    public record Translation(
        boolean enabled,
        Mode mode,
        @Name("backend") String backendId,
        String endpoint,
        String apiKey,
        String sourceLanguage,
        String targetLanguage,
        @DefaultValue("true") boolean translateUnknownMessages,
        @DefaultValue("true") boolean detectAllLanguages,
        List<String> detectionLanguages,
        DisplayMode displayMode,
        long requestTimeoutMs,
        int maxRequestChars,
        int maxConcurrentRequests) {

      public enum DisplayMode {
        BELOW_ORIGINAL
      }

      public enum Mode {
        AUTO,
        MANUAL
      }

      public Translation(
          boolean enabled,
          Mode mode,
          String backendId,
          String endpoint,
          String apiKey,
          String sourceLanguage,
          String targetLanguage,
          DisplayMode displayMode,
          long requestTimeoutMs,
          int maxRequestChars,
          int maxConcurrentRequests) {
        this(
            enabled,
            mode,
            backendId,
            endpoint,
            apiKey,
            sourceLanguage,
            targetLanguage,
            true,
            true,
            List.of(),
            displayMode,
            requestTimeoutMs,
            maxRequestChars,
            maxConcurrentRequests);
      }

      public Translation(
          boolean enabled,
          Mode mode,
          String backendId,
          String endpoint,
          String apiKey,
          String sourceLanguage,
          String targetLanguage,
          boolean translateUnknownMessages,
          DisplayMode displayMode,
          long requestTimeoutMs,
          int maxRequestChars,
          int maxConcurrentRequests) {
        this(
            enabled,
            mode,
            backendId,
            endpoint,
            apiKey,
            sourceLanguage,
            targetLanguage,
            translateUnknownMessages,
            true,
            List.of(),
            displayMode,
            requestTimeoutMs,
            maxRequestChars,
            maxConcurrentRequests);
      }

      @ConstructorBinding
      public Translation {
        if (mode == null) {
          mode = Mode.AUTO;
        }
        backendId = normalizeToken(backendId);
        endpoint = normalizeEndpoint(endpoint, backendId);
        apiKey = Objects.toString(apiKey, "").trim();
        sourceLanguage = normalizeLanguage(sourceLanguage, "auto");
        targetLanguage = normalizeLanguage(targetLanguage, "");
        detectionLanguages = normalizeLanguages(detectionLanguages);
        if (displayMode == null) {
          displayMode = DisplayMode.BELOW_ORIGINAL;
        }
        if (requestTimeoutMs <= 0) {
          requestTimeoutMs = 10_000;
        }
        if (maxRequestChars <= 0) {
          maxRequestChars = 4_000;
        }
        if (maxConcurrentRequests <= 0) {
          maxConcurrentRequests = 2;
        }
        if (maxConcurrentRequests > 16) {
          maxConcurrentRequests = 16;
        }

        if (enabled) {
          if (backendId.isBlank()) {
            throw new IllegalArgumentException(
                "irc.client.translation.enabled=true but backend is blank");
          }
          if (targetLanguage.isBlank()) {
            throw new IllegalArgumentException(
                "irc.client.translation.enabled=true but target-language is blank");
          }
          if ("deepl".equals(backendId) && apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "irc.client.translation.enabled=true with backend=deepl but api-key is blank");
          }
        }
      }

      private static String normalizeToken(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
      }

      private static String normalizeEndpoint(String raw, String backendId) {
        String endpoint = Objects.toString(raw, "").trim();
        if (!endpoint.isBlank()) {
          return endpoint;
        }
        return switch (backendId) {
          case "deepl" -> "https://api-free.deepl.com/v2/translate";
          case "libretranslate" -> "https://libretranslate.com/translate";
          case "google-web" -> "https://translate.googleapis.com/translate_a/single";
          default -> "";
        };
      }

      private static String normalizeLanguage(String raw, String defaultValue) {
        String value = raw == null ? defaultValue : raw.trim();
        return value.toLowerCase(Locale.ROOT);
      }

      private static List<String> normalizeLanguages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
          return List.of();
        }
        return raw.stream()
            .map(value -> normalizeLanguage(value, ""))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
      }
    }

    @ConstructorBinding
    public Client {
      if (floodProtection == null) floodProtection = FloodProtection.defaults();
      version = AppVersion.decorateIfDefaultName(version);
      if (reconnect == null) {
        reconnect = new Reconnect(true, 1_000, 120_000, 2.0, 0.20, 0);
      }
      if (heartbeat == null) {
        heartbeat = new Heartbeat(true, 15_000, 360_000);
      }
      if (proxy == null) {
        proxy = new Proxy(false, "", 0, "", "", true, 20_000, 30_000);
      }
      if (tls == null) {
        tls = new Tls(false);
      }
      if (translation == null) {
        translation =
            new Translation(
                false, Translation.Mode.AUTO, "", "", "", "auto", "", null, 10_000, 4_000, 2);
      }
    }

    public Client(String version, Reconnect reconnect, Heartbeat heartbeat, Proxy proxy, Tls tls) {
      this(version, reconnect, heartbeat, proxy, tls, null);
    }

    public Client(
        String version,
        Reconnect reconnect,
        Heartbeat heartbeat,
        Proxy proxy,
        Tls tls,
        Translation translation) {
      this(version, reconnect, heartbeat, proxy, tls, translation, null);
    }
  }

  /** Per-connection pacing for ordinary IRC commands and startup channel joins. */
  public record FloodProtection(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("2000") long commandIntervalMs,
      @DefaultValue("5000") long autoJoinDelayMs) {
    @ConstructorBinding
    public FloodProtection {
      commandIntervalMs =
          commandIntervalMs <= 0 ? 2000 : Math.clamp(commandIntervalMs, 100, 10_000);
      autoJoinDelayMs = Math.clamp(autoJoinDelayMs, 0, 120_000);
    }

    public FloodProtection(long commandIntervalMs, long autoJoinDelayMs) {
      this(true, commandIntervalMs, autoJoinDelayMs);
    }

    public static FloodProtection defaults() {
      return new FloodProtection(true, 2000, 5000);
    }
  }

  /**
   * SOCKS5 proxy settings used for IRC connections and outbound HTTP fetching (link previews, image
   * embeds, etc.).
   */
  public record Proxy(
      boolean enabled,
      String host,
      int port,
      String username,
      String password,
      boolean remoteDns,
      long connectTimeoutMs,
      long readTimeoutMs) {
    public Proxy {
      if (host == null) host = "";
      if (username == null) username = "";
      if (password == null) password = "";
      if (connectTimeoutMs <= 0) connectTimeoutMs = 20_000;
      if (readTimeoutMs <= 0) readTimeoutMs = 30_000;

      if (enabled) {
        if (host.isBlank()) {
          throw new IllegalArgumentException("irc.client.proxy.enabled=true but host is blank");
        }
        if (port <= 0 || port > 65535) {
          throw new IllegalArgumentException(
              "irc.client.proxy.enabled=true but port is invalid: " + port);
        }
      }
    }

    public boolean hasAuth() {
      return username != null && !username.isBlank();
    }
  }

  public record Reconnect(
      boolean enabled,
      long initialDelayMs,
      long maxDelayMs,
      double multiplier,
      double jitterPct,
      int maxAttempts) {
    public Reconnect {
      if (initialDelayMs <= 0) initialDelayMs = 1_000;
      if (maxDelayMs <= 0) maxDelayMs = 120_000;
      if (maxDelayMs < initialDelayMs) maxDelayMs = initialDelayMs;
      if (multiplier < 1.1) multiplier = 2.0;
      if (jitterPct < 0) jitterPct = 0;
      if (jitterPct > 0.75) jitterPct = 0.75;
      // maxAttempts == 0 means "infinite".
      if (maxAttempts < 0) maxAttempts = 0;
    }
  }

  public record Heartbeat(boolean enabled, long checkPeriodMs, long timeoutMs) {
    public Heartbeat {
      if (checkPeriodMs <= 0) checkPeriodMs = 15_000;
      if (timeoutMs <= 0) timeoutMs = 360_000;
      if (timeoutMs < checkPeriodMs) timeoutMs = Math.max(checkPeriodMs * 2, checkPeriodMs);
    }
  }

  public record Server(
      String id,
      String host,
      int port,
      boolean tls,
      String serverPassword,
      String nick,
      String login,
      String realName,
      Sasl sasl,
      Nickserv nickserv,
      List<String> autoJoin,
      /**
       * Optional list of commands to run after connecting, similar to HexChat's "Perform" list.
       *
       * <p>Each entry is a single line. Later steps may add variable substitution and /sleep.
       */
      List<String> perform,
      /**
       * Optional per-server proxy override.
       *
       * <p>If {@code null}, the server inherits {@code irc.client.proxy}. If non-null and {@code
       * enabled} is {@code false}, the server explicitly disables proxying.
       */
      Proxy proxy,
      @Name("backend") String backendId,
      /** Accept untrusted TLS certificates for this server only. Disabled by default. */
      boolean trustAllCertificates) {
    /** Transport backend used for this server entry. */
    public enum Backend {
      IRC("irc"),
      QUASSEL_CORE("quassel-core"),
      MATRIX("matrix");

      private final String token;

      Backend(String token) {
        this.token = token;
      }

      public String token() {
        return token;
      }
    }

    public record Sasl(
        boolean enabled,
        String username,
        String password,
        String mechanism,
        /**
         * If true, a SASL failure (e.g. wrong password) is treated as a hard connect failure. The
         * client will disconnect and surface the error. If false, the client may remain connected
         * without SASL (useful for networks where SASL is optional).
         *
         * <p>If omitted, defaults to {@code true}.
         */
        Boolean disconnectOnFailure) {
      public Sasl {
        if (mechanism == null || mechanism.isBlank()) {
          mechanism = "PLAIN";
        }
        if (disconnectOnFailure == null) {
          // Strict default.
          disconnectOnFailure = true;
        }
      }
    }

    public record Nickserv(
        boolean enabled,
        String password,
        String service,
        /**
         * If true, delay auto-join channels until NickServ identification is confirmed by a notice.
         *
         * <p>If omitted, defaults to {@code true}.
         */
        Boolean delayJoinUntilIdentified) {
      public Nickserv {
        if (password == null) password = "";
        if (service == null || service.isBlank()) {
          service = "NickServ";
        }
        if (delayJoinUntilIdentified == null) {
          delayJoinUntilIdentified = true;
        }
      }
    }

    @ConstructorBinding
    public Server {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("irc.servers[].id is required");
      }
      if (serverPassword == null) {
        serverPassword = "";
      }
      if (sasl == null) {
        sasl = new Sasl(false, "", "", "PLAIN", null);
      }
      if (nickserv == null) {
        nickserv = new Nickserv(false, "", "NickServ", null);
      }
      if (autoJoin == null) {
        autoJoin = List.of();
      }
      if (perform == null) {
        perform = List.of();
      }
      backendId = BackendDescriptorCatalog.builtIns().normalizeIdOrDefault(backendId);
    }

    // Legacy constructor kept for call sites that don't opt out of certificate validation.
    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        Nickserv nickserv,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy,
        String backendId) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          backendId,
          false);
    }

    // Legacy constructor kept for call sites that don't set backend explicitly.
    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        Nickserv nickserv,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          BackendDescriptorCatalog.builtIns().idFor(Backend.IRC));
    }

    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          null,
          autoJoin,
          perform,
          proxy,
          BackendDescriptorCatalog.builtIns().idFor(Backend.IRC));
    }

    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        Nickserv nickserv,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy,
        Backend backend) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          BackendDescriptorCatalog.builtIns().idFor(backend));
    }

    public Server withAutoJoin(List<String> nextAutoJoin) {
      List<String> value = nextAutoJoin == null ? List.of() : List.copyOf(nextAutoJoin);
      return new Server(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          value,
          perform,
          proxy,
          backendId,
          trustAllCertificates);
    }

    public Server withTransport(int nextPort, boolean nextTls) {
      return new Server(
          id,
          host,
          nextPort,
          nextTls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          backendId,
          trustAllCertificates);
    }

    public Backend backend() {
      return BackendDescriptorCatalog.builtIns().backendForId(backendId).orElse(null);
    }
  }

  public IrcProperties {
    if (client == null) {
      client = new Client("IRCafe", null, null, null, null);
    }
    if (servers == null) {
      servers = List.of();
    }
  }

  public Map<String, Server> byId() {
    return servers.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                s -> s.id().trim(),
                Function.identity(),
                (a, b) -> {
                  throw new IllegalStateException("Duplicate server id: " + a.id());
                }));
  }
}
