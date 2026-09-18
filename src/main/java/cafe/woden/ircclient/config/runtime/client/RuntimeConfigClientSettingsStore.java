package cafe.woden.ircclient.config.runtime.client;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.runtime.client.RuntimeConfigClientSettingsCodec.HeartbeatSettings;
import cafe.woden.ircclient.config.runtime.client.RuntimeConfigClientSettingsCodec.ProxySettings;
import cafe.woden.ircclient.config.runtime.client.RuntimeConfigClientSettingsCodec.TranslationSettings;
import cafe.woden.ircclient.config.yaml.RuntimeConfigDocumentStore;
import cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSection;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns persisted IRC client transport settings under {@code irc.client}. */
public class RuntimeConfigClientSettingsStore {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigClientSettingsStore.class);

  private final RuntimeConfigYamlSection clientSection;

  public RuntimeConfigClientSettingsStore(Path file, RuntimeConfigDocumentStore documentStore) {
    this.clientSection = RuntimeConfigYamlSection.irc(file, documentStore, log, "client");
  }

  public synchronized void rememberTlsTrustAllCertificates(boolean trustAllCertificates) {
    clientSection.putValue(
        "TLS trust-all setting", trustAllCertificates, "tls", "trustAllCertificates");
  }

  public synchronized void rememberHeartbeat(IrcProperties.Heartbeat heartbeat) {
    HeartbeatSettings hb = RuntimeConfigClientSettingsCodec.normalizeHeartbeat(heartbeat);

    clientSection.mutateMap(
        "heartbeat settings",
        hbMap -> {
          hbMap.put("enabled", hb.enabled());
          hbMap.put("checkPeriodMs", hb.checkPeriodMs());
          hbMap.put("timeoutMs", hb.timeoutMs());
        },
        "heartbeat");
  }

  public synchronized void rememberFloodProtection(IrcProperties.FloodProtection floodProtection) {
    IrcProperties.FloodProtection safe =
        floodProtection == null ? IrcProperties.FloodProtection.defaults() : floodProtection;
    clientSection.mutateMap(
        "IRC flood protection",
        floodMap -> {
          floodMap.put("enabled", safe.enabled());
          floodMap.put("commandIntervalMs", safe.commandIntervalMs());
          floodMap.put("autoJoinDelayMs", safe.autoJoinDelayMs());
        },
        "floodProtection");
  }

  public synchronized void rememberProxy(IrcProperties.Proxy proxy) {
    ProxySettings p = RuntimeConfigClientSettingsCodec.normalizeProxy(proxy);

    clientSection.mutateMap(
        "SOCKS proxy settings",
        proxyMap -> {
          proxyMap.put("enabled", p.enabled());
          proxyMap.put("host", p.host());
          proxyMap.put("port", p.port());
          proxyMap.put("username", p.username());
          proxyMap.put("password", p.password());
          proxyMap.put("remoteDns", p.remoteDns());
          proxyMap.put("connectTimeoutMs", p.connectTimeoutMs());
          proxyMap.put("readTimeoutMs", p.readTimeoutMs());
        },
        "proxy");
  }

  public synchronized void rememberTranslation(IrcProperties.Client.Translation translation) {
    TranslationSettings safe = RuntimeConfigClientSettingsCodec.normalizeTranslation(translation);

    clientSection.mutateMap(
        "translation settings",
        translationMap -> {
          translationMap.put("enabled", safe.enabled());
          translationMap.put("mode", safe.mode());
          RuntimeConfigClientSettingsCodec.putOptionalString(
              translationMap, "backend", safe.backendId(), "");
          RuntimeConfigClientSettingsCodec.putOptionalString(
              translationMap, "endpoint", safe.endpoint(), "");
          RuntimeConfigClientSettingsCodec.putOptionalString(
              translationMap, "apiKey", safe.apiKey(), "");
          translationMap.put("sourceLanguage", safe.sourceLanguage());
          RuntimeConfigClientSettingsCodec.putOptionalString(
              translationMap, "targetLanguage", safe.targetLanguage(), "");
          translationMap.put("translateUnknownMessages", safe.translateUnknownMessages());
          translationMap.put("detectAllLanguages", safe.detectAllLanguages());
          if (safe.detectionLanguages().isEmpty()) {
            translationMap.remove("detectionLanguages");
          } else {
            translationMap.put("detectionLanguages", safe.detectionLanguages());
          }
          translationMap.put("displayMode", safe.displayMode());
          translationMap.put("requestTimeoutMs", safe.requestTimeoutMs());
          translationMap.put("maxRequestChars", safe.maxRequestChars());
          translationMap.put("maxConcurrentRequests", safe.maxConcurrentRequests());
        },
        "translation");
  }
}
