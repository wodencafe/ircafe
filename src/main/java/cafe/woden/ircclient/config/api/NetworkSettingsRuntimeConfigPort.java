package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by the advanced network preferences panel. */
@SecondaryPort
@ApplicationLayer
public interface NetworkSettingsRuntimeConfigPort {

  boolean readGenericBouncerPreferLoginHint(boolean defaultValue);

  String readGenericBouncerLoginTemplate(String defaultValue);

  void rememberClientProxy(IrcProperties.Proxy proxy);

  void rememberClientHeartbeat(IrcProperties.Heartbeat heartbeat);

  void rememberClientFloodProtection(IrcProperties.FloodProtection floodProtection);

  void rememberGenericBouncerPreferLoginHint(boolean enabled);

  void rememberGenericBouncerLoginTemplate(String template);

  void rememberClientTlsTrustAllCertificates(boolean trustAllCertificates);
}
