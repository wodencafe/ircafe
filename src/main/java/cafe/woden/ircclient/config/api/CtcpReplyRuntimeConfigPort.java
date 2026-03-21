package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for CTCP auto-reply policy. */
@ApplicationLayer
public interface CtcpReplyRuntimeConfigPort {

  boolean readCtcpAutoRepliesEnabled(boolean defaultValue);

  boolean readCtcpAutoReplyVersionEnabled(boolean defaultValue);

  boolean readCtcpAutoReplyPingEnabled(boolean defaultValue);

  boolean readCtcpAutoReplyTimeEnabled(boolean defaultValue);
}
