package cafe.woden.ircclient.config.api;

import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by bouncer discovery, mapping, and auto-connect flows. */
@ApplicationLayer
public interface BouncerDiscoveryConfigPort {

  List<String> readKnownChannels(String serverId);

  boolean readServerTreeChannelAutoReattach(String serverId, String channel, boolean defaultValue);

  Map<String, Map<String, Boolean>> readGenericBouncerAutoConnectRules();

  void rememberGenericBouncerAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled);

  void rememberSojuAutoConnectNetwork(String bouncerServerId, String networkName, boolean enabled);

  void rememberZncAutoConnectNetwork(String bouncerServerId, String networkName, boolean enabled);

  String readGenericBouncerLoginTemplate(String defaultValue);

  boolean readGenericBouncerPreferLoginHint(boolean defaultValue);
}
