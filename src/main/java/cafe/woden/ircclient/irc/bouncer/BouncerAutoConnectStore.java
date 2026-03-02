package cafe.woden.ircclient.irc.bouncer;

/** Shared contract for bouncer network auto-connect preferences. */
public interface BouncerAutoConnectStore {

  boolean isEnabled(String bouncerServerId, String networkName);

  void setEnabled(String bouncerServerId, String networkName, boolean enable);
}
